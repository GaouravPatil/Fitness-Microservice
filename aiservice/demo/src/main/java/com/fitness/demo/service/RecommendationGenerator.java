package com.fitness.demo.service;

import com.fitness.demo.event.ActivityEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RecommendationGenerator {

    private final OpenAiRecommendationClient openAiRecommendationClient;

    public GeneratedRecommendation generate(ActivityEvent event) {
        return openAiRecommendationClient.generate(event)
                .orElseGet(() -> ruleBasedRecommendation(event));
    }

    private GeneratedRecommendation ruleBasedRecommendation(ActivityEvent event) {
        String type = event.getType() == null ? "OTHER" : event.getType().toUpperCase(Locale.ROOT);
        int duration = event.getDuration() == null ? 0 : event.getDuration();
        int calories = event.getCaloriesBurned() == null ? 0 : event.getCaloriesBurned();

        String summary = "You completed %d minutes of %s and burned approximately %d calories. %s"
                .formatted(duration, readableType(type), calories, closingMessage(duration, calories));

        List<String> suggestions = new ArrayList<>(suggestionsFor(type));
        List<String> safety = new ArrayList<>(List.of(
                "Warm up for 5-10 minutes before training and cool down afterwards.",
                "Drink water before, during and after the session.",
                "Stop immediately if you feel chest pain, dizziness or sharp joint pain."));

        if (duration > 120) {
            safety.add("This was a long session: prioritise sleep, protein and an easy recovery day.");
        }
        if (calories > 1000) {
            safety.add("Very high energy expenditure: replace fluids and electrolytes and avoid training "
                    + "the same muscle groups hard tomorrow.");
        }

        return new GeneratedRecommendation(summary, suggestions, safety, "RULES");
    }

    private List<String> suggestionsFor(String type) {
        return switch (type) {
            case "RUNNING" -> List.of(
                    "Add one interval session next week to improve speed and VO2 max.",
                    "Keep an easy long run at a conversational pace.",
                    "Include hip and calf mobility work twice a week.");
            case "WALKING" -> List.of(
                    "Increase daily steps by about 10% next week.",
                    "Walk briskly for at least 10 continuous minutes per session.",
                    "Use hills or stairs once a week for extra intensity.");
            case "CYCLING" -> List.of(
                    "Keep cadence around 80-100 rpm on flat terrain.",
                    "Add one longer endurance ride each week.",
                    "Check saddle height and knee alignment to prevent overuse injuries.");
            case "WEIGHTLIFTING", "WEIGHT_TRAINING" -> List.of(
                    "Apply progressive overload by adding reps or small load increases weekly.",
                    "Train each major muscle group twice per week.",
                    "Aim for 1.6-2.0 g of protein per kg of body weight daily.");
            case "SWIMMING" -> List.of(
                    "Reserve part of each session for technique drills.",
                    "Mix freestyle with another stroke to balance the workload.",
                    "Practise breathing patterns every length.");
            case "YOGA" -> List.of(
                    "Hold poses 20-30 seconds longer to build stability.",
                    "Add a short daily mobility routine for hips and shoulders.",
                    "Finish sessions with 5 minutes of slow breathing.");
            case "STEPS" -> List.of(
                    "Break sitting time every 45-60 minutes with a short walk.",
                    "Add a 15-minute post-dinner walk.",
                    "Track weekly averages instead of daily totals.");
            default -> List.of(
                    "Balance cardio and strength training across the week.",
                    "Leave at least one full rest day between hard sessions.",
                    "Track how you feel after each workout to adjust intensity.");
        };
    }

    private String readableType(String type) {
        return type.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private String closingMessage(int duration, int calories) {
        if (duration >= 60 || calories >= 600) {
            return "That was a solid effort - focus on recovery now.";
        }
        return "Nice work - keep building consistency.";
    }
}
