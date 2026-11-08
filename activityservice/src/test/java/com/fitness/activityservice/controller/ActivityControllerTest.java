package com.fitness.activityservice.controller;

import com.fitness.activityservice.dto.ActivityResponse;
import com.fitness.activityservice.model.ActivityType;
import com.fitness.activityservice.security.JwtConfig;
import com.fitness.activityservice.security.SecurityConfig;
import com.fitness.activityservice.service.ActivityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ActivityController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class ActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivityService activityService;

    @Test
    void trackActivityReturnsCreatedForOwner() throws Exception {
        when(activityService.trackActivity(any())).thenReturn(response());

        mockMvc.perform(post("/api/activities")
                        .with(jwt().jwt(token -> token.subject("user-1").claim("role", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user-1","type":"RUNNING","duration":30,
                                 "caloriesBurned":300,"startTime":"2026-01-01T10:00:00"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("activity-1"))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.startTime").exists());
    }

    @Test
    void trackActivityRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user-1","type":"RUNNING","duration":30,
                                 "caloriesBurned":300,"startTime":"2026-01-01T10:00:00"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trackActivityForbidsRecordingForAnotherUser() throws Exception {
        mockMvc.perform(post("/api/activities")
                        .with(jwt().jwt(token -> token.subject("user-2").claim("role", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user-1","type":"RUNNING","duration":30,
                                 "caloriesBurned":300,"startTime":"2026-01-01T10:00:00"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void trackActivityRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/activities")
                        .with(jwt().jwt(token -> token.subject("user-1").claim("role", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"","type":"RUNNING","duration":-5,
                                 "caloriesBurned":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.userId").exists())
                .andExpect(jsonPath("$.fieldErrors.duration").exists())
                .andExpect(jsonPath("$.fieldErrors.caloriesBurned").exists())
                .andExpect(jsonPath("$.fieldErrors.startTime").exists());
    }

    @Test
    void getUserActivitiesReturnsPageForOwner() throws Exception {
        when(activityService.getActivities(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response())));

        mockMvc.perform(get("/api/activities/user/user-1")
                        .with(jwt().jwt(token -> token.subject("user-1").claim("role", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("activity-1"));
    }

    @Test
    void getUserActivitiesForbidsOtherUsers() throws Exception {
        mockMvc.perform(get("/api/activities/user/user-1")
                        .with(jwt().jwt(token -> token.subject("user-2").claim("role", "USER"))))
                .andExpect(status().isForbidden());
    }

    private ActivityResponse response() {
        return ActivityResponse.builder()
                .id("activity-1")
                .userId("user-1")
                .type(ActivityType.RUNNING)
                .duration(30)
                .caloriesBurned(300)
                .startTime(LocalDateTime.of(2026, 1, 1, 10, 0))
                .build();
    }
}
