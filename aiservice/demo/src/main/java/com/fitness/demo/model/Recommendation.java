package com.fitness.demo.model;
import lombok.Builder;
import lombok.Data;
import org.springframework.cglib.core.Local;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Document(collection = "recommendatoins")
@Data
@Builder


public class Recommendation {
    @Id
    private String id;
    private String activityId;
    private String userId;
    private String recommendation;
    private List<String> suggestions;
    private List<String> safety;

    @CreatedDate
    private LocalDateTime createdAt;



}
