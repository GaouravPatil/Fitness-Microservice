package com.fitness.activityservice.service;

import com.fitness.activityservice.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityRepublisher {

    private static final int BATCH_SIZE = 100;

    private final ActivityRepository activityRepository;
    private final ActivityPublisher activityPublisher;

    @Scheduled(fixedDelayString = "${activity.republisher.fixed-delay:PT30S}")
    public void republishPendingActivities() {
        List<com.fitness.activityservice.model.Activity> pending =
                activityRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        if (pending.isEmpty()) {
            return;
        }
        int published = 0;
        for (com.fitness.activityservice.model.Activity activity : pending) {
            if (activityPublisher.publish(activity)) {
                activity.setPublished(true);
                activityRepository.save(activity);
                published++;
            }
        }
        if (published > 0) {
            log.info("Republished {} of {} pending activity events", published, pending.size());
        }
    }
}
