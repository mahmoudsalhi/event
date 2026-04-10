package tn.esprit.event.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.event.dto.RecommendedEventDTO;
import tn.esprit.event.entity.Event;
import tn.esprit.event.entity.EventRegistration;
import tn.esprit.event.entity.EventStatus;
import tn.esprit.event.repository.EventRegistrationRepository;
import tn.esprit.event.repository.EventRepository;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventRecommendationService {

    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Returns AI-powered recommended events for a given user.
     * Falls back to date-sorted events if Gemini is unavailable.
     */
    public List<RecommendedEventDTO> getRecommendations(Long userId, int limit) {
        // 1. Load user's registration history
        List<EventRegistration> history = registrationRepository.findByUserId(userId);

        // 2. Load all upcoming events
        List<Event> upcomingEvents = eventRepository.findByStatus(EventStatus.UPCOMING);

        // 3. Exclude events user is already registered for
        Set<Long> registeredIds = history.stream()
                .filter(r -> r.getEvent() != null)
                .map(r -> r.getEvent().getId())
                .collect(Collectors.toSet());

        List<Event> candidates = upcomingEvents.stream()
                .filter(e -> !registeredIds.contains(e.getId()))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            log.info("No candidate events to recommend for user {}", userId);
            return List.of();
        }

        // 4. Build user profile from history
        String userProfile = buildUserProfile(history);

        // 5. Build simplified event list for the prompt
        String eventsJson = buildEventsJson(candidates);

        // 6. Build and send Gemini prompt
        String prompt = buildPrompt(userProfile, eventsJson, limit);
        log.info("Sending recommendation prompt to Gemini for user {}", userId);

        String geminiResponse = geminiService.ask(prompt);

        // 7. Parse Gemini response
        if (geminiResponse != null) {
            List<RecommendedEventDTO> aiResults = parseGeminiResponse(geminiResponse, candidates, limit);
            if (!aiResults.isEmpty()) {
                log.info("Gemini returned {} recommendations for user {}", aiResults.size(), userId);
                return aiResults;
            }
        }

        // 8. Fallback: return top N by date if Gemini failed
        log.warn("Gemini unavailable or parse failed — falling back to date-sorted for user {}", userId);
        return candidates.stream()
                .limit(limit)
                .map(e -> new RecommendedEventDTO(e, 0, "Upcoming event you haven't registered for yet."))
                .collect(Collectors.toList());
    }

    // ─── Prompt Building ──────────────────────────────────────────────────────

    private String buildUserProfile(List<EventRegistration> history) {
        if (history.isEmpty()) {
            return "New user with no event history.";
        }

        List<String> pastEventNames = history.stream()
                .filter(r -> r.getEvent() != null)
                .map(r -> r.getEvent().getTitle())
                .collect(Collectors.toList());

        Set<String> categories = history.stream()
                .filter(r -> r.getEvent() != null && r.getEvent().getCategory() != null)
                .map(r -> r.getEvent().getCategory())
                .collect(Collectors.toSet());

        Set<String> skillFocuses = history.stream()
                .filter(r -> r.getEvent() != null && r.getEvent().getSkillFocus() != null)
                .map(r -> r.getEvent().getSkillFocus())
                .collect(Collectors.toSet());

        Set<String> levels = history.stream()
                .filter(r -> r.getEvent() != null && r.getEvent().getTargetLevel() != null)
                .map(r -> r.getEvent().getTargetLevel())
                .collect(Collectors.toSet());

        return String.format(
                "Past events attended: %s\n" +
                "Preferred categories: %s\n" +
                "Skill focuses engaged with: %s\n" +
                "Levels engaged with: %s",
                String.join(", ", pastEventNames),
                String.join(", ", categories),
                String.join(", ", skillFocuses),
                String.join(", ", levels)
        );
    }

    private String buildEventsJson(List<Event> events) {
        // Keep only what Gemini needs — avoid sending huge payload
        List<Map<String, Object>> simplified = events.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("title", e.getTitle());
            if (e.getDescription() != null) {
                // Truncate to 100 chars to save tokens
                m.put("description", e.getDescription().length() > 100
                        ? e.getDescription().substring(0, 100) + "..."
                        : e.getDescription());
            }
            if (e.getCategory() != null) m.put("category", e.getCategory());
            if (e.getTargetLevel() != null) m.put("targetLevel", e.getTargetLevel());
            if (e.getSkillFocus() != null) m.put("skillFocus", e.getSkillFocus());
            if (e.getStartDate() != null) m.put("startDate", e.getStartDate().toString());
            if (e.getMaxAttendees() != null && e.getCurrentAttendees() != null) {
                m.put("availableSpots", e.getMaxAttendees() - e.getCurrentAttendees());
            }
            return m;
        }).collect(Collectors.toList());

        try {
            return objectMapper.writeValueAsString(simplified);
        } catch (Exception ex) {
            return events.stream()
                    .map(e -> "{\"id\":" + e.getId() + ",\"title\":\"" + e.getTitle() + "\"}")
                    .collect(Collectors.joining(",", "[", "]"));
        }
    }

    private String buildPrompt(String userProfile, String eventsJson, int limit) {
        return "You are an event recommendation AI for MiNoLingo, a language learning platform.\n\n" +
               "USER PROFILE:\n" + userProfile + "\n\n" +
               "AVAILABLE UPCOMING EVENTS (JSON):\n" + eventsJson + "\n\n" +
               "Task: Rank the top " + limit + " events that best match this user's interests and learning history.\n" +
               "For each recommended event, provide a confidence score (0-100) and a short 1-sentence reason " +
               "that mentions what specifically makes it a good match.\n\n" +
               "Rules:\n" +
               "- Only recommend events from the list above (use their exact IDs)\n" +
               "- Score 90-100: excellent match, 70-89: good match, 50-69: decent match\n" +
               "- Make the reason personal and specific, not generic\n" +
               "- If user has no history, base recommendations on event popularity and variety\n\n" +
               "Respond ONLY with a valid JSON array, no markdown, no extra text:\n" +
               "[{\"eventId\": 123, \"score\": 95, \"reason\": \"...\"}]";
    }

    // ─── Response Parsing ─────────────────────────────────────────────────────

    private List<RecommendedEventDTO> parseGeminiResponse(String rawResponse,
                                                           List<Event> candidates,
                                                           int limit) {
        try {
            // Clean up potential markdown code block wrapping
            String json = rawResponse.trim();
            if (json.startsWith("```")) {
                int start = json.indexOf('[');
                int end = json.lastIndexOf(']');
                if (start >= 0 && end > start) {
                    json = json.substring(start, end + 1);
                }
            }

            // Handle escaped newlines
            json = json.replace("\\n", " ").trim();

            List<Map<String, Object>> geminiItems = objectMapper.readValue(
                    json, new TypeReference<>() {}
            );

            // Build event lookup by ID
            Map<Long, Event> eventMap = candidates.stream()
                    .collect(Collectors.toMap(Event::getId, e -> e));

            List<RecommendedEventDTO> results = new ArrayList<>();
            for (Map<String, Object> item : geminiItems) {
                Object idRaw = item.get("eventId");
                Object scoreRaw = item.get("score");
                Object reasonRaw = item.get("reason");

                if (idRaw == null) continue;

                Long eventId = ((Number) idRaw).longValue();
                int score = scoreRaw != null ? ((Number) scoreRaw).intValue() : 50;
                String reason = reasonRaw != null ? reasonRaw.toString() : "Recommended for you.";

                Event event = eventMap.get(eventId);
                if (event != null) {
                    results.add(new RecommendedEventDTO(event, score, reason));
                }

                if (results.size() >= limit) break;
            }

            return results;

        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}. Raw: {}", e.getMessage(), rawResponse);
            return List.of();
        }
    }
}
