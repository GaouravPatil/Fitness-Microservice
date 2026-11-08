package com.fitness.activityservice.service;

import com.fitness.activityservice.dto.ActivityRequest;
import com.fitness.activityservice.dto.ActivityResponse;
import com.fitness.activityservice.exception.ActivityPublishException;
import com.fitness.activityservice.exception.InvalidUserException;
import com.fitness.activityservice.model.Activity;
import com.fitness.activityservice.model.ActivityType;
import com.fitness.activityservice.repository.ActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private UserValidationService userValidationService;

    @Mock
    private KafkaTemplate<String, Activity> kafkaTemplate;

    private ActivityService activityService;

    @BeforeEach
    void setUp() {
        activityService = new ActivityService(activityRepository, userValidationService, kafkaTemplate);
        ReflectionTestUtils.setField(activityService, "topicName", "activity-topic");
        ReflectionTestUtils.setField(activityService, "publishTimeout", Duration.ofSeconds(5));
    }

    @Test
    void trackActivitySavesAndPublishesForValidUser() {
        when(userValidationService.validateUser("user-1")).thenReturn(true);
        when(activityRepository.save(any(Activity.class))).thenAnswer(invocation -> {
            Activity activity = invocation.getArgument(0);
            activity.setId("activity-1");
            activity.setCreatedAt(LocalDateTime.now());
            activity.setUpdatedAt(LocalDateTime.now());
            return activity;
        });
        when(kafkaTemplate.send(eq("activity-topic"), eq("user-1"), any(Activity.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        ActivityResponse response = activityService.trackActivity(request());

        assertThat(response.getId()).isEqualTo("activity-1");
        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getStartTime()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
        verify(kafkaTemplate).send(eq("activity-topic"), eq("user-1"), any(Activity.class));
    }

    @Test
    void trackActivityRejectsUnknownUser() {
        when(userValidationService.validateUser("user-1")).thenReturn(false);

        assertThatThrownBy(() -> activityService.trackActivity(request()))
                .isInstanceOf(InvalidUserException.class);

        verify(activityRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void trackActivityFailsWhenKafkaPublishFails() {
        when(userValidationService.validateUser("user-1")).thenReturn(true);
        when(activityRepository.save(any(Activity.class))).thenAnswer(invocation -> {
            Activity activity = invocation.getArgument(0);
            activity.setId("activity-1");
            return activity;
        });
        when(kafkaTemplate.send(anyString(), anyString(), any(Activity.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThatThrownBy(() -> activityService.trackActivity(request()))
                .isInstanceOf(ActivityPublishException.class);
    }

    @Test
    void getActivitiesMapsRepositoryPage() {
        Activity activity = Activity.builder()
                .id("activity-1")
                .userId("user-1")
                .type(ActivityType.RUNNING)
                .duration(30)
                .caloriesBurned(300)
                .startTime(LocalDateTime.now())
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        when(activityRepository.findByUserId("user-1", pageable))
                .thenReturn(new PageImpl<>(List.of(activity), pageable, 1));

        Page<ActivityResponse> page = activityService.getActivities("user-1", pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().getId()).isEqualTo("activity-1");
    }

    private ActivityRequest request() {
        ActivityRequest request = new ActivityRequest();
        request.setUserId("user-1");
        request.setType(ActivityType.RUNNING);
        request.setDuration(30);
        request.setCaloriesBurned(300);
        request.setStartTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        return request;
    }
}
