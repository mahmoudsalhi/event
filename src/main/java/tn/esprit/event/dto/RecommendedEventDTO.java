package tn.esprit.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.esprit.event.entity.Event;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedEventDTO {
    private Event event;
    private int score;       // 0–100 confidence score from Gemini
    private String reason;   // AI-generated natural language explanation
}
