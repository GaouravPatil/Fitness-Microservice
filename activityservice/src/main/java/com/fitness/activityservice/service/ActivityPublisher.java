package com.fitness.activityservice.service;

import com.fitness.activityservice.model.Activity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityPublisher {

    private final KafkaTemplate<String, Activity> kafkaTemplate;

    @Value("${kafka.topic.name}")
    private String topicName;

    @Value("${kafka.publish-timeout:PT5S}")
    private Duration publishTimeout;

    public boolean publish(Activity activity) {
        try {
            kafkaTemplate.send(topicName, activity.getUserId(), activity)
                    .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
            log.info("Published activity {} for user {}", activity.getId(), activity.getUserId());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while publishing activity {}", activity.getId());
            return false;
        } catch (Exception ex) {
            log.warn("Could not publish activity {}, leaving it pending for retry: {}",
                    activity.getId(), ex.getMessage());
            return false;
        }
    }
}
