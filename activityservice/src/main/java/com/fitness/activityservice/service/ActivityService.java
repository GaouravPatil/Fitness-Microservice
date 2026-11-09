package com.fitness.activityservice.service;

import com.fitness.activityservice.dto.ActivityRequest;
import com.fitness.activityservice.dto.ActivityResponse;
import com.fitness.activityservice.exception.InvalidIdempotencyKeyException;
import com.fitness.activityservice.exception.InvalidUserException;
import com.fitness.activityservice.model.Activity;
import com.fitness.activityservice.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final ActivityRepository activityRepository;
    private final UserValidationService userValidationService;
    private final ActivityPublisher activityPublisher;

    public ActivityResponse trackActivity(ActivityRequest request, String idempotencyKey) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        if (key != null) {
            Optional<Activity> existing = activityRepository
                    .findByUserIdAndIdempotencyKey(request.getUserId(), key);
            if (existing.isPresent()) {
                log.info("Returning existing activity {} for idempotency key {}", existing.get().getId(), key);
                return mapToResponse(existing.get());
            }
        }

        if (!userValidationService.validateUser(request.getUserId())) {
            throw new InvalidUserException(request.getUserId());
        }

        Activity activity = Activity.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .duration(request.getDuration())
                .caloriesBurned(request.getCaloriesBurned())
                .startTime(request.getStartTime())
                .additionalMetrics(request.getAdditionalMetrics())
                .idempotencyKey(key)
                .published(false)
                .build();

        Activity savedActivity;
        try {
            savedActivity = activityRepository.save(activity);
        } catch (DuplicateKeyException ex) {
            if (key == null) {
                throw ex;
            }
            log.info("Concurrent request detected for idempotency key {}, returning existing activity", key);
            return activityRepository.findByUserIdAndIdempotencyKey(request.getUserId(), key)
                    .map(ActivityService::mapToResponse)
                    .orElseThrow(() -> ex);
        }

        if (activityPublisher.publish(savedActivity)) {
            savedActivity.setPublished(true);
            savedActivity = activityRepository.save(savedActivity);
        } else {
            log.warn("Activity {} stored but not yet published; the republisher will retry", savedActivity.getId());
        }

        return mapToResponse(savedActivity);
    }

    public Page<ActivityResponse> getActivities(String userId, Pageable pageable) {
        return activityRepository.findByUserId(userId, pageable)
                .map(ActivityService::mapToResponse);
    }

    private static String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new InvalidIdempotencyKeyException();
        }
        return trimmed;
    }

    private static ActivityResponse mapToResponse(Activity activity) {
        return ActivityResponse.builder()
                .id(activity.getId())
                .userId(activity.getUserId())
                .type(activity.getType())
                .duration(activity.getDuration())
                .caloriesBurned(activity.getCaloriesBurned())
                .startTime(activity.getStartTime())
                .additionalMetrics(activity.getAdditionalMetrics())
                .createdAt(activity.getCreatedAt())
                .updatedAt(activity.getUpdatedAt())
                .build();
    }
}
