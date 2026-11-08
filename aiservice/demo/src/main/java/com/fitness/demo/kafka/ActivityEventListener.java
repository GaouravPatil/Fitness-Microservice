package com.fitness.demo.kafka;

import com.fitness.demo.event.ActivityEvent;
import com.fitness.demo.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityEventListener {

    private final RecommendationService recommendationService;

    @KafkaListener(topics = "${kafka.topic.name}", containerFactory = "activityKafkaListenerContainerFactory")
    public void onActivityEvent(ActivityEvent event) {
        log.info("Received activity {} for user {}", event.getId(), event.getUserId());
        recommendationService.generateRecommendation(event);
    }
}
