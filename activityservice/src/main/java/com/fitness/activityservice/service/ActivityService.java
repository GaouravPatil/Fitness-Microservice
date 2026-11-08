package com.fitness.activityservice.service;

import com.fitness.activityservice.dto.ActivityRequest;
import com.fitness.activityservice.dto.ActivityResponse;
import com.fitness.activityservice.exception.ActivityPublishException;
import com.fitness.activityservice.exception.InvalidUserException;
import com.fitness.activityservice.model.Activity;
import com.fitness.activityservice.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final UserValidationService userValidationService;
    private final KafkaTemplate<String, Activity> kafkaTemplate;

    @Value("${kafka.topic.name}")
    private String topicName;

    @Value("${kafka.publish-timeout:PT5S}")
    private Duration publishTimeout;

    public ActivityResponse trackActivity(ActivityRequest request) {
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
                .build();

        Activity savedActivity = activityRepository.save(activity);
        publishActivity(savedActivity);

        return mapToResponse(savedActivity);
    }

    public Page<ActivityResponse> getActivities(String userId, Pageable pageable) {
        return activityRepository.findByUserId(userId, pageable)
                .map(ActivityService::mapToResponse);
    }

    private void publishActivity(Activity activity) {
        try {
            kafkaTemplate.send(topicName, activity.getUserId(), activity)
                    .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
            log.info("Published activity {} for user {}", activity.getId(), activity.getUserId());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ActivityPublishException("Interrupted while publishing activity event", ex);
        } catch (Exception ex) {
            throw new ActivityPublishException("Could not publish activity event", ex);
        }
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
