package com.fitness.activityservice.service;


import com.fitness.activityservice.AcitivtyRepository;
import com.fitness.activityservice.dto.ActivityRequest;
import com.fitness.activityservice.dto.ActivityResponse;
import com.fitness.activityservice.model.Activity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor

public class ActivityService {
    private final AcitivtyRepository activityRepository;
    public final UserValidationService userValidationService;

    public ActivityResponse trackActivty(ActivityRequest request) {


        boolean isValidUser = userValidationService.validateUser(request.getUserid());

        if(!isValidUser)
            throw new RuntimeException("Invalid User: "+ request.getUserid());
        Activity activity = Activity.builder()
                .userid(request.getUserid())
                .type(request.getType())
                .duration(request.getDuration())
                .caloriesBurned(request.getCaloriesBurned())
                .starttime(request.getStartTime())
                .additionalMetrics(request.getAdditionalMetrics())
                .build();

            Activity savedActivity = activityRepository.save(activity);
            return mapToResponse(savedActivity);
        }

    private ActivityResponse mapToResponse(Activity activity) {
        ActivityResponse response = new ActivityResponse();
        response.setId(activity.getId());
         response.setUserid(activity.getUserid());
         response.setType(activity.getType());
         response.setDuration(activity.getDuration());
         response.setCaloriesBurned(activity.getCaloriesBurned());
         response.setStarttime(activity.getStarttime());
         response.setAdditionalMetrics(activity.getAdditionalMetrics());
         response.setCreatedAt(activity.getCreatedAt());
         response.setUpdatedAt(activity.getUpdatedAt());

         return response;
    }
}

