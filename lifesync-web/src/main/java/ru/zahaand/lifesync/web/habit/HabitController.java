package ru.zahaand.lifesync.web.habit;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import ru.zahaand.lifesync.api.HabitApi;
import ru.zahaand.lifesync.api.model.*;
import ru.zahaand.lifesync.application.habit.*;
import ru.zahaand.lifesync.domain.habit.*;
import ru.zahaand.lifesync.domain.user.TokenProvider;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class HabitController implements HabitApi {

    private final CreateHabitUseCase createHabitUseCase;
    private final GetHabitsUseCase getHabitsUseCase;
    private final GetHabitUseCase getHabitUseCase;
    private final UpdateHabitUseCase updateHabitUseCase;
    private final DeleteHabitUseCase deleteHabitUseCase;
    private final CompleteHabitUseCase completeHabitUseCase;
    private final DeleteHabitLogUseCase deleteHabitLogUseCase;
    private final GetHabitLogsUseCase getHabitLogsUseCase;
    private final GetHabitStreakUseCase getHabitStreakUseCase;

    public HabitController(CreateHabitUseCase createHabitUseCase,
                            GetHabitsUseCase getHabitsUseCase,
                            GetHabitUseCase getHabitUseCase,
                            UpdateHabitUseCase updateHabitUseCase,
                            DeleteHabitUseCase deleteHabitUseCase,
                            CompleteHabitUseCase completeHabitUseCase,
                            DeleteHabitLogUseCase deleteHabitLogUseCase,
                            GetHabitLogsUseCase getHabitLogsUseCase,
                            GetHabitStreakUseCase getHabitStreakUseCase) {
        this.createHabitUseCase = createHabitUseCase;
        this.getHabitsUseCase = getHabitsUseCase;
        this.getHabitUseCase = getHabitUseCase;
        this.updateHabitUseCase = updateHabitUseCase;
        this.deleteHabitUseCase = deleteHabitUseCase;
        this.completeHabitUseCase = completeHabitUseCase;
        this.deleteHabitLogUseCase = deleteHabitLogUseCase;
        this.getHabitLogsUseCase = getHabitLogsUseCase;
        this.getHabitStreakUseCase = getHabitStreakUseCase;
    }

    @Override
    public ResponseEntity<HabitResponseDto> createHabit(CreateHabitRequestDto request) {
        UUID userId = getCurrentUserId();
        Frequency frequency = Frequency.valueOf(request.getFrequency().name());
        DayOfWeekSet targetDays = toDayOfWeekSet(request.getTargetDaysOfWeek());
        LocalTime reminderTime = request.getReminderTime() != null
                ? LocalTime.parse(request.getReminderTime()) : null;

        Habit habit = createHabitUseCase.execute(userId, request.getTitle(), request.getDescription(),
                frequency, targetDays, reminderTime);

        return ResponseEntity.status(HttpStatus.CREATED).body(toHabitResponse(habit));
    }

    @Override
    public ResponseEntity<HabitPageResponseDto> getHabits(String status, Integer page, Integer size) {
        UUID userId = getCurrentUserId();
        GetHabitsUseCase.EnrichedHabitPage result = getHabitsUseCase.execute(userId, status, page, size);

        List<HabitResponseDto> content = result.content().stream()
                .map(this::toEnrichedHabitResponse)
                .toList();

        HabitPageResponseDto response = new HabitPageResponseDto()
                .content(content)
                .totalElements(result.totalElements())
                .totalPages(result.totalPages())
                .page(result.page())
                .size(result.size());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<HabitResponseDto> getHabit(UUID id) {
        UUID userId = getCurrentUserId();
        EnrichedHabit enriched = getHabitUseCase.execute(new HabitId(id), userId);
        return ResponseEntity.ok(toEnrichedHabitResponse(enriched));
    }

    @Override
    public ResponseEntity<HabitResponseDto> updateHabit(UUID id, UpdateHabitRequestDto request) {
        UUID userId = getCurrentUserId();

        Frequency frequency = request.getFrequency() != null
                ? Frequency.valueOf(request.getFrequency().name()) : null;
        DayOfWeekSet targetDays = request.getTargetDaysOfWeek() != null
                ? toDayOfWeekSet(request.getTargetDaysOfWeek()) : null;
        LocalTime reminderTime = request.getReminderTime() != null
                ? LocalTime.parse(request.getReminderTime()) : null;

        UpdateHabitUseCase.UpdateCommand command = new UpdateHabitUseCase.UpdateCommand(
                request.getTitle(),
                request.getDescription(),
                request.getDescription() != null || isFieldExplicitlyNull(request, "description"),
                frequency,
                targetDays,
                request.getTargetDaysOfWeek() != null || isFieldExplicitlyNull(request, "targetDaysOfWeek"),
                reminderTime,
                request.getReminderTime() != null || isFieldExplicitlyNull(request, "reminderTime"),
                request.getIsActive()
        );

        Habit habit = updateHabitUseCase.execute(new HabitId(id), userId, command);
        return ResponseEntity.ok(toHabitResponse(habit));
    }

    @Override
    public ResponseEntity<Void> deleteHabit(UUID id) {
        UUID userId = getCurrentUserId();
        deleteHabitUseCase.execute(new HabitId(id), userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<HabitLogResponseDto> completeHabit(UUID id, CompleteHabitRequestDto request) {
        UUID userId = getCurrentUserId();
        HabitLog log = completeHabitUseCase.execute(new HabitId(id), userId,
                request.getDate(), request.getNote());
        return ResponseEntity.status(HttpStatus.CREATED).body(toHabitLogResponse(log));
    }

    @Override
    public ResponseEntity<Void> deleteHabitLog(UUID id, UUID logId) {
        UUID userId = getCurrentUserId();
        deleteHabitLogUseCase.execute(new HabitId(id), new HabitLogId(logId), userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<HabitLogPageResponseDto> getHabitLogs(UUID id, Integer page, Integer size) {
        UUID userId = getCurrentUserId();
        HabitLogRepository.HabitLogPage result = getHabitLogsUseCase.execute(
                new HabitId(id), userId, page, size);

        List<HabitLogResponseDto> content = result.content().stream()
                .map(this::toHabitLogResponse)
                .toList();

        HabitLogPageResponseDto response = new HabitLogPageResponseDto()
                .content(content)
                .totalElements(result.totalElements())
                .totalPages(result.totalPages())
                .page(result.page())
                .size(result.size());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<HabitStreakResponseDto> getHabitStreak(UUID id) {
        UUID userId = getCurrentUserId();
        HabitStreak streak = getHabitStreakUseCase.execute(new HabitId(id), userId);

        HabitStreakResponseDto response = new HabitStreakResponseDto()
                .currentStreak(streak.currentStreak())
                .longestStreak(streak.longestStreak())
                .lastLogDate(streak.lastLogDate());

        return ResponseEntity.ok(response);
    }

    private UUID getCurrentUserId() {
        TokenProvider.TokenClaims claims = (TokenProvider.TokenClaims)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return claims.userId().value();
    }

    private HabitResponseDto toEnrichedHabitResponse(EnrichedHabit enriched) {
        HabitResponseDto dto = toBaseHabitResponse(enriched.habit());
        dto.completedToday(enriched.completedToday());
        dto.todayLogId(enriched.todayLogId() != null ? enriched.todayLogId().value() : null);
        dto.currentStreak(enriched.currentStreak());
        return dto;
    }

    private HabitResponseDto toHabitResponse(Habit habit) {
        HabitResponseDto dto = toBaseHabitResponse(habit);
        dto.completedToday(false);
        dto.currentStreak(0);
        return dto;
    }

    private HabitResponseDto toBaseHabitResponse(Habit habit) {
        HabitResponseDto dto = new HabitResponseDto()
                .id(habit.getId().value())
                .title(habit.getTitle())
                .description(habit.getDescription())
                .frequency(HabitResponseDto.FrequencyEnum.fromValue(habit.getFrequency().name()))
                .isActive(habit.getActive())
                .createdAt(habit.getCreatedAt().atOffset(ZoneOffset.UTC));

        if (habit.getTargetDaysOfWeek() != null) {
            List<DayOfWeekDto> days = habit.getTargetDaysOfWeek().days().stream()
                    .map(d -> DayOfWeekDto.fromValue(d.name()))
                    .toList();
            dto.targetDaysOfWeek(days);
        }

        if (habit.getReminderTime() != null) {
            dto.reminderTime(habit.getReminderTime().toString());
        }

        return dto;
    }

    private HabitLogResponseDto toHabitLogResponse(HabitLog log) {
        return new HabitLogResponseDto()
                .id(log.getId().value())
                .habitId(log.getHabitId().value())
                .date(log.getLogDate())
                .note(log.getNote())
                .createdAt(log.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    private DayOfWeekSet toDayOfWeekSet(List<DayOfWeekDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return null;
        }
        return new DayOfWeekSet(
                dtos.stream()
                        .map(d -> DayOfWeek.valueOf(d.getValue()))
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)))
        );
    }

    private boolean isFieldExplicitlyNull(UpdateHabitRequestDto request, String fieldName) {
        // For JSON Merge Patch: if the field was present in the JSON body with null value,
        // the generated DTO will have it set to null (same as absent).
        // Since openapi-generator doesn't use JsonNullable by default, we can't distinguish
        // absent from explicit null. We treat null fields as "no change" per the DTO contract.
        // Fields that need to be cleared must be handled by the caller sending an explicit marker.
        return false;
    }
}
