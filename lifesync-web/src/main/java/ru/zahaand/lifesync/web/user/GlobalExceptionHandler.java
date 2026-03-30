package ru.zahaand.lifesync.web.user;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.zahaand.lifesync.api.model.ErrorResponseDto;
import ru.zahaand.lifesync.domain.habit.exception.DuplicateHabitLogException;
import ru.zahaand.lifesync.domain.habit.exception.HabitInactiveException;
import ru.zahaand.lifesync.domain.habit.exception.HabitNotFoundException;
import ru.zahaand.lifesync.domain.user.exception.DuplicateEmailException;
import ru.zahaand.lifesync.domain.user.exception.DuplicateUsernameException;
import ru.zahaand.lifesync.domain.user.exception.InvalidCredentialsException;
import ru.zahaand.lifesync.domain.user.exception.InvalidTokenException;
import ru.zahaand.lifesync.domain.user.exception.UserBannedException;
import ru.zahaand.lifesync.domain.user.exception.UserDeletedException;
import ru.zahaand.lifesync.domain.user.exception.UserNotFoundException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleUserNotFound(UserNotFoundException ex,
                                                               HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(HabitNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleHabitNotFound(HabitNotFoundException ex,
                                                                HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(HabitInactiveException.class)
    public ResponseEntity<ErrorResponseDto> handleHabitInactive(HabitInactiveException ex,
                                                                HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateHabitLogException.class)
    public ResponseEntity<ErrorResponseDto> handleDuplicateHabitLog(DuplicateHabitLogException ex,
                                                                    HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(UserDeletedException.class)
    public ResponseEntity<ErrorResponseDto> handleUserDeleted(UserDeletedException ex,
                                                              HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidCredentials(InvalidCredentialsException ex,
                                                                     HttpServletRequest request) {
        log.warn("Failed login attempt: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidToken(InvalidTokenException ex,
                                                               HttpServletRequest request) {
        log.warn("Invalid token used: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(UserBannedException.class)
    public ResponseEntity<ErrorResponseDto> handleUserBanned(UserBannedException ex,
                                                             HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler({DuplicateEmailException.class, DuplicateUsernameException.class})
    public ResponseEntity<ErrorResponseDto> handleDuplicate(RuntimeException ex,
                                                            HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgument(IllegalArgumentException ex,
                                                                  HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpected(Exception ex,
                                                             HttpServletRequest request) {
        log.error("Unexpected error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
    }

    private ResponseEntity<ErrorResponseDto> buildResponse(HttpStatus status, String message,
                                                           HttpServletRequest request) {
        ErrorResponseDto error = new ErrorResponseDto()
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI());
        return ResponseEntity.status(status).body(error);
    }
}
