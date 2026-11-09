package com.fitness.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "recommendations")
@CompoundIndexes({
        @CompoundIndex(name = "idx_recommendation_activity_created",
                def = "{'activityId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_recommendation_user_created",
                def = "{'userId': 1, 'createdAt': -1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recommendation {

    @Id
    private String id;

    private String activityId;

    private String userId;

    private String activityType;

    private String recommendation;

    private List<String> suggestions;

    private List<String> safety;

    private String provider;

    @CreatedDate
    private LocalDateTime createdAt;
}
