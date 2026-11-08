package com.fitness.demo.repository;

import com.fitness.demo.model.Recommendation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RecommendationRepository extends MongoRepository<Recommendation, String> {

    Page<Recommendation> findByUserId(String userId, Pageable pageable);

    Optional<Recommendation> findFirstByActivityIdOrderByCreatedAtDesc(String activityId);
}
