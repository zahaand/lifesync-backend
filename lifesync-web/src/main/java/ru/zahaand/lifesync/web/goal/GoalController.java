package ru.zahaand.lifesync.web.goal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import ru.zahaand.lifesync.api.GoalApi;
import ru.zahaand.lifesync.api.model.GoalCreateRequestDto;
import ru.zahaand.lifesync.api.model.GoalDetailResponseDto;
import ru.zahaand.lifesync.api.model.GoalHabitLinkRequestDto;
import ru.zahaand.lifesync.api.model.GoalHabitLinkResponseDto;
import ru.zahaand.lifesync.api.model.GoalPageResponseDto;
import ru.zahaand.lifesync.api.model.GoalProgressUpdateRequestDto;
import ru.zahaand.lifesync.api.model.GoalResponseDto;
import ru.zahaand.lifesync.api.model.GoalUpdateRequestDto;
import ru.zahaand.lifesync.api.model.MilestoneCreateRequestDto;
import ru.zahaand.lifesync.api.model.MilestoneResponseDto;
import ru.zahaand.lifesync.api.model.MilestoneUpdateRequestDto;
import ru.zahaand.lifesync.application.goal.AddMilestoneUseCase;
import ru.zahaand.lifesync.application.goal.CreateGoalUseCase;
import ru.zahaand.lifesync.application.goal.DeleteGoalUseCase;
import ru.zahaand.lifesync.application.goal.DeleteMilestoneUseCase;
import ru.zahaand.lifesync.application.goal.GetGoalUseCase;
import ru.zahaand.lifesync.application.goal.GetGoalsUseCase;
import ru.zahaand.lifesync.application.goal.LinkHabitToGoalUseCase;
import ru.zahaand.lifesync.application.goal.UnlinkHabitFromGoalUseCase;
import ru.zahaand.lifesync.application.goal.UpdateGoalProgressUseCase;
import ru.zahaand.lifesync.application.goal.UpdateGoalUseCase;
import ru.zahaand.lifesync.application.goal.UpdateMilestoneUseCase;
import ru.zahaand.lifesync.domain.goal.Goal;
import ru.zahaand.lifesync.domain.goal.GoalHabitLink;
import ru.zahaand.lifesync.domain.goal.GoalId;
import ru.zahaand.lifesync.domain.goal.GoalMilestone;
import ru.zahaand.lifesync.domain.goal.GoalMilestoneId;
import ru.zahaand.lifesync.domain.goal.GoalRepository;
import ru.zahaand.lifesync.domain.goal.GoalStatus;
import ru.zahaand.lifesync.domain.habit.HabitId;
import ru.zahaand.lifesync.domain.user.TokenProvider;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
public class GoalController implements GoalApi {

    private final CreateGoalUseCase createGoalUseCase;
    private final GetGoalUseCase getGoalUseCase;
    private final GetGoalsUseCase getGoalsUseCase;
    private final UpdateGoalUseCase updateGoalUseCase;
    private final DeleteGoalUseCase deleteGoalUseCase;
    private final UpdateGoalProgressUseCase updateGoalProgressUseCase;
    private final AddMilestoneUseCase addMilestoneUseCase;
    private final UpdateMilestoneUseCase updateMilestoneUseCase;
    private final DeleteMilestoneUseCase deleteMilestoneUseCase;
    private final LinkHabitToGoalUseCase linkHabitToGoalUseCase;
    private final UnlinkHabitFromGoalUseCase unlinkHabitFromGoalUseCase;

    public GoalController(CreateGoalUseCase createGoalUseCase,
                           GetGoalUseCase getGoalUseCase,
                           GetGoalsUseCase getGoalsUseCase,
                           UpdateGoalUseCase updateGoalUseCase,
                           DeleteGoalUseCase deleteGoalUseCase,
                           UpdateGoalProgressUseCase updateGoalProgressUseCase,
                           AddMilestoneUseCase addMilestoneUseCase,
                           UpdateMilestoneUseCase updateMilestoneUseCase,
                           DeleteMilestoneUseCase deleteMilestoneUseCase,
                           LinkHabitToGoalUseCase linkHabitToGoalUseCase,
                           UnlinkHabitFromGoalUseCase unlinkHabitFromGoalUseCase) {
        this.createGoalUseCase = createGoalUseCase;
        this.getGoalUseCase = getGoalUseCase;
        this.getGoalsUseCase = getGoalsUseCase;
        this.updateGoalUseCase = updateGoalUseCase;
        this.deleteGoalUseCase = deleteGoalUseCase;
        this.updateGoalProgressUseCase = updateGoalProgressUseCase;
        this.addMilestoneUseCase = addMilestoneUseCase;
        this.updateMilestoneUseCase = updateMilestoneUseCase;
        this.deleteMilestoneUseCase = deleteMilestoneUseCase;
        this.linkHabitToGoalUseCase = linkHabitToGoalUseCase;
        this.unlinkHabitFromGoalUseCase = unlinkHabitFromGoalUseCase;
    }

    @Override
    public ResponseEntity<GoalResponseDto> createGoal(GoalCreateRequestDto request) {
        UUID userId = getCurrentUserId();
        Goal goal = createGoalUseCase.execute(userId, request.getTitle(),
                request.getDescription(), request.getTargetDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(toGoalResponse(goal));
    }

    @Override
    public ResponseEntity<GoalDetailResponseDto> getGoal(UUID goalId) {
        UUID userId = getCurrentUserId();
        GetGoalUseCase.GoalDetail detail = getGoalUseCase.execute(new GoalId(goalId), userId);
        return ResponseEntity.ok(toGoalDetailResponse(detail));
    }

    @Override
    public ResponseEntity<GoalPageResponseDto> getGoals(String status, Integer page, Integer size) {
        UUID userId = getCurrentUserId();
        GoalStatus goalStatus = status != null ? GoalStatus.valueOf(status) : null;
        GoalRepository.GoalPage result = getGoalsUseCase.execute(userId, goalStatus, page, size);

        List<GoalResponseDto> content = result.content().stream()
                .map(this::toGoalResponse)
                .toList();

        GoalPageResponseDto response = new GoalPageResponseDto()
                .content(content)
                .totalElements(result.totalElements())
                .totalPages(result.totalPages())
                .page(result.page())
                .size(result.size());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<GoalResponseDto> updateGoal(UUID goalId,
                                                       GoalUpdateRequestDto request) {
        UUID userId = getCurrentUserId();

        GoalStatus goalStatus = request.getStatus() != null
                ? GoalStatus.valueOf(request.getStatus().name()) : null;

        UpdateGoalUseCase.UpdateCommand command = new UpdateGoalUseCase.UpdateCommand(
                request.getTitle(),
                request.getDescription(),
                request.getDescription() != null,
                request.getTargetDate(),
                request.getTargetDate() != null,
                goalStatus
        );

        Goal goal = updateGoalUseCase.execute(new GoalId(goalId), userId, command);
        return ResponseEntity.ok(toGoalResponse(goal));
    }

    @Override
    public ResponseEntity<Void> deleteGoal(UUID goalId) {
        UUID userId = getCurrentUserId();
        deleteGoalUseCase.execute(new GoalId(goalId), userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<GoalResponseDto> updateGoalProgress(UUID goalId,
                                                                GoalProgressUpdateRequestDto request) {
        UUID userId = getCurrentUserId();
        Goal goal = updateGoalProgressUseCase.execute(new GoalId(goalId), userId,
                request.getProgress());
        return ResponseEntity.ok(toGoalResponse(goal));
    }

    @Override
    public ResponseEntity<MilestoneResponseDto> addMilestone(UUID goalId,
                                                              MilestoneCreateRequestDto request) {
        UUID userId = getCurrentUserId();
        int sortOrder = request.getSortOrder() != null ? request.getSortOrder() : 0;
        GoalMilestone milestone = addMilestoneUseCase.execute(
                new GoalId(goalId), userId, request.getTitle(), sortOrder);
        return ResponseEntity.status(HttpStatus.CREATED).body(toMilestoneResponse(milestone));
    }

    @Override
    public ResponseEntity<MilestoneResponseDto> updateMilestone(UUID goalId, UUID milestoneId,
                                                                  MilestoneUpdateRequestDto request) {
        UUID userId = getCurrentUserId();

        UpdateMilestoneUseCase.UpdateCommand command = new UpdateMilestoneUseCase.UpdateCommand(
                request.getTitle(),
                request.getSortOrder(),
                request.getCompleted()
        );

        GoalMilestone milestone = updateMilestoneUseCase.execute(
                new GoalId(goalId), userId, new GoalMilestoneId(milestoneId), command);
        return ResponseEntity.ok(toMilestoneResponse(milestone));
    }

    @Override
    public ResponseEntity<Void> deleteMilestone(UUID goalId, UUID milestoneId) {
        UUID userId = getCurrentUserId();
        deleteMilestoneUseCase.execute(new GoalId(goalId), userId, new GoalMilestoneId(milestoneId));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<GoalHabitLinkResponseDto> linkHabitToGoal(UUID goalId,
                                                                     GoalHabitLinkRequestDto request) {
        UUID userId = getCurrentUserId();
        GoalHabitLink link = linkHabitToGoalUseCase.execute(
                new GoalId(goalId), userId, new HabitId(request.getHabitId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toHabitLinkResponse(link));
    }

    @Override
    public ResponseEntity<Void> unlinkHabitFromGoal(UUID goalId, UUID habitId) {
        UUID userId = getCurrentUserId();
        unlinkHabitFromGoalUseCase.execute(new GoalId(goalId), userId, new HabitId(habitId));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<GoalHabitLinkResponseDto>> getGoalHabitLinks(UUID goalId) {
        UUID userId = getCurrentUserId();
        GetGoalUseCase.GoalDetail detail = getGoalUseCase.execute(new GoalId(goalId), userId);

        List<GoalHabitLinkResponseDto> links = detail.habitLinks().stream()
                .map(this::toHabitLinkResponse)
                .toList();

        return ResponseEntity.ok(links);
    }

    private UUID getCurrentUserId() {
        TokenProvider.TokenClaims claims = (TokenProvider.TokenClaims)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return claims.userId().value();
    }

    private GoalResponseDto toGoalResponse(Goal goal) {
        return new GoalResponseDto()
                .id(goal.getId().value())
                .title(goal.getTitle())
                .description(goal.getDescription())
                .targetDate(goal.getTargetDate())
                .status(GoalResponseDto.StatusEnum.fromValue(goal.getStatus().name()))
                .progress(goal.getProgress())
                .createdAt(goal.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    private GoalDetailResponseDto toGoalDetailResponse(GetGoalUseCase.GoalDetail detail) {
        Goal goal = detail.goal();

        List<MilestoneResponseDto> milestones = detail.milestones().stream()
                .map(this::toMilestoneResponse)
                .toList();

        List<UUID> linkedHabitIds = detail.habitLinks().stream()
                .map(link -> link.getHabitId().value())
                .toList();

        return new GoalDetailResponseDto()
                .id(goal.getId().value())
                .title(goal.getTitle())
                .description(goal.getDescription())
                .targetDate(goal.getTargetDate())
                .status(GoalDetailResponseDto.StatusEnum.fromValue(goal.getStatus().name()))
                .progress(goal.getProgress())
                .milestones(milestones)
                .linkedHabitIds(linkedHabitIds)
                .createdAt(goal.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    private MilestoneResponseDto toMilestoneResponse(GoalMilestone milestone) {
        MilestoneResponseDto dto = new MilestoneResponseDto()
                .id(milestone.getId().value())
                .goalId(milestone.getGoalId().value())
                .title(milestone.getTitle())
                .sortOrder(milestone.getSortOrder())
                .completed(milestone.getCompleted())
                .createdAt(milestone.getCreatedAt().atOffset(ZoneOffset.UTC));

        if (milestone.getCompletedAt() != null) {
            dto.completedAt(milestone.getCompletedAt().atOffset(ZoneOffset.UTC));
        }

        return dto;
    }

    private GoalHabitLinkResponseDto toHabitLinkResponse(GoalHabitLink link) {
        return new GoalHabitLinkResponseDto()
                .id(link.getId().value())
                .goalId(link.getGoalId().value())
                .habitId(link.getHabitId().value())
                .createdAt(link.getCreatedAt().atOffset(ZoneOffset.UTC));
    }
}
