package tn.esprit.event.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tn.esprit.event.dto.RecommendedEventDTO;
import tn.esprit.event.entity.Event;
import tn.esprit.event.entity.EventRegistration;
import tn.esprit.event.entity.EventStatus;
import tn.esprit.event.entity.RegistrationStatus;
import tn.esprit.event.services.EventRecommendationService;
import tn.esprit.event.services.EventRegistrationService;
import tn.esprit.event.services.EventReminderScheduler;
import tn.esprit.event.services.EventService;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    @Mock
    private EventService eventService;

    @Mock
    private EventRegistrationService registrationService;

    @Mock
    private EventRecommendationService recommendationService;

    @Mock
    private EventReminderScheduler reminderScheduler;

    @InjectMocks
    private EventController controller;

    private Event sampleEvent;

    @BeforeEach
    void setUp() {
        sampleEvent = Event.builder()
                .id(1L)
                .title("English Workshop")
                .description("A beginner workshop")
                .startDate(LocalDateTime.of(2026, 5, 10, 14, 0))
                .status(EventStatus.UPCOMING)
                .isPublic(true)
                .build();
    }

    // ══════════════════════════════════════════════════════
    // EVENT CRUD ENDPOINTS
    // ══════════════════════════════════════════════════════

    @Test
    void create_shouldReturn201WithCreatedEvent() {
        when(eventService.create(any(Event.class))).thenReturn(sampleEvent);

        ResponseEntity<Event> response = controller.create(sampleEvent);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("English Workshop", response.getBody().getTitle());
    }

    @Test
    void getAll_shouldReturn200WithEventList() {
        when(eventService.getAll()).thenReturn(Arrays.asList(sampleEvent));

        ResponseEntity<List<Event>> response = controller.getAll();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void getById_shouldReturn200WithEvent() {
        when(eventService.getById(1L)).thenReturn(sampleEvent);

        ResponseEntity<Event> response = controller.getById(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1L, response.getBody().getId());
        assertEquals("English Workshop", response.getBody().getTitle());
    }

    @Test
    void update_shouldReturn200WithUpdatedEvent() {
        when(eventService.update(eq(1L), any(Event.class))).thenReturn(sampleEvent);

        ResponseEntity<Event> response = controller.update(1L, sampleEvent);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void delete_shouldReturn204() {
        doNothing().when(eventService).delete(1L);

        ResponseEntity<Void> response = controller.delete(1L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(eventService).delete(1L);
    }

    @Test
    void draftEvent_shouldReturn200WithDraftedEvent() {
        sampleEvent.setStatus(EventStatus.DRAFT);
        when(eventService.draft(1L)).thenReturn(sampleEvent);

        ResponseEntity<Event> response = controller.draftEvent(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(EventStatus.DRAFT, response.getBody().getStatus());
    }

    @Test
    void undraftEvent_shouldReturn200() {
        when(eventService.undraft(1L)).thenReturn(sampleEvent);

        ResponseEntity<Event> response = controller.undraftEvent(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(EventStatus.UPCOMING, response.getBody().getStatus());
    }

    @Test
    void uncancelEvent_shouldReturn200() {
        when(eventService.uncancel(1L)).thenReturn(sampleEvent);

        ResponseEntity<Event> response = controller.uncancelEvent(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void duplicateEvent_shouldReturn200WithCopy() {
        Event copy = Event.builder()
                .id(2L).title("Copy of English Workshop")
                .status(EventStatus.DRAFT).build();
        when(eventService.duplicate(1L)).thenReturn(copy);

        ResponseEntity<Event> response = controller.duplicateEvent(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Copy of English Workshop", response.getBody().getTitle());
        assertEquals(EventStatus.DRAFT, response.getBody().getStatus());
    }

    @Test
    void bulkDraft_shouldReturn204() {
        doNothing().when(eventService).bulkDraft(anyList());

        ResponseEntity<Void> response = controller.bulkDraft(List.of(1L, 2L, 3L));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(eventService).bulkDraft(List.of(1L, 2L, 3L));
    }

    @Test
    void bulkCancel_shouldReturn204() {
        doNothing().when(eventService).bulkCancel(anyList());

        ResponseEntity<Void> response = controller.bulkCancel(List.of(1L, 2L));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(eventService).bulkCancel(List.of(1L, 2L));
    }

    // ══════════════════════════════════════════════════════
    // REGISTRATION ENDPOINTS
    // ══════════════════════════════════════════════════════

    @Test
    void createRegistration_shouldReturn201() {
        EventRegistration reg = EventRegistration.builder()
                .id(1L).userName("John").userEmail("john@test.com")
                .status(RegistrationStatus.PENDING).build();
        when(registrationService.create(eq(1L), any(EventRegistration.class))).thenReturn(reg);

        ResponseEntity<EventRegistration> response = controller.createRegistration(1L, reg);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("John", response.getBody().getUserName());
        assertEquals(RegistrationStatus.PENDING, response.getBody().getStatus());
    }

    @Test
    void getAllRegistrations_shouldReturn200() {
        when(registrationService.getAll()).thenReturn(List.of());

        ResponseEntity<List<EventRegistration>> response = controller.getAllRegistrations();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void getRegistrationById_shouldReturn200() {
        EventRegistration reg = EventRegistration.builder().id(1L).userName("John").build();
        when(registrationService.getById(1L)).thenReturn(reg);

        ResponseEntity<EventRegistration> response = controller.getRegistrationById(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("John", response.getBody().getUserName());
    }

    @Test
    void getRegistrationsByEvent_shouldReturn200() {
        when(registrationService.getByEventId(1L)).thenReturn(List.of());

        ResponseEntity<List<EventRegistration>> response = controller.getRegistrationsByEvent(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getRegistrationsByUser_shouldReturn200() {
        when(registrationService.getByUserId(42L)).thenReturn(List.of());

        ResponseEntity<List<EventRegistration>> response = controller.getRegistrationsByUser(42L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void deleteRegistration_shouldReturn204() {
        doNothing().when(registrationService).delete(1L);

        ResponseEntity<Void> response = controller.deleteRegistration(1L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    // ══════════════════════════════════════════════════════
    // APPROVAL / REJECTION
    // ══════════════════════════════════════════════════════

    @Test
    void approveRegistration_shouldReturn200OnSuccess() {
        EventRegistration reg = EventRegistration.builder()
                .id(1L).status(RegistrationStatus.REGISTERED).build();
        when(registrationService.approve(1L)).thenReturn(reg);

        ResponseEntity<?> response = controller.approveRegistration(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        EventRegistration body = (EventRegistration) response.getBody();
        assertEquals(RegistrationStatus.REGISTERED, body.getStatus());
    }

    @Test
    void approveRegistration_shouldReturn400OnError() {
        when(registrationService.approve(1L))
                .thenThrow(new RuntimeException("Only PENDING registrations can be approved"));

        ResponseEntity<?> response = controller.approveRegistration(1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("Only PENDING registrations can be approved", body.get("error"));
    }

    @Test
    void rejectRegistration_shouldReturn200OnSuccess() {
        EventRegistration reg = EventRegistration.builder()
                .id(1L).status(RegistrationStatus.REJECTED).build();
        when(registrationService.reject(1L)).thenReturn(reg);

        ResponseEntity<?> response = controller.rejectRegistration(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void rejectRegistration_shouldReturn400OnError() {
        when(registrationService.reject(1L))
                .thenThrow(new RuntimeException("Only PENDING registrations can be rejected"));

        ResponseEntity<?> response = controller.rejectRegistration(1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getPendingRegistrations_shouldReturn200() {
        when(registrationService.getPending()).thenReturn(List.of());

        ResponseEntity<List<EventRegistration>> response = controller.getPendingRegistrations();

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // ══════════════════════════════════════════════════════
    // CHECK-IN
    // ══════════════════════════════════════════════════════

    @Test
    void checkIn_validCode_shouldReturn200() {
        EventRegistration reg = EventRegistration.builder()
                .id(1L).status(RegistrationStatus.ATTENDED).build();
        when(registrationService.checkIn("abc-123")).thenReturn(reg);

        ResponseEntity<?> response = controller.checkIn("abc-123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void checkIn_invalidCode_shouldReturn400() {
        when(registrationService.checkIn("invalid"))
                .thenThrow(new RuntimeException("Invalid check-in code: invalid"));

        ResponseEntity<?> response = controller.checkIn("invalid");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("Invalid check-in code: invalid", body.get("error"));
    }

    // ══════════════════════════════════════════════════════
    // RATING
    // ══════════════════════════════════════════════════════

    @Test
    void rateEvent_shouldReturn200() {
        EventRegistration reg = EventRegistration.builder()
                .id(1L).rating(5).build();
        when(registrationService.rateEvent(1L, 5)).thenReturn(reg);

        ResponseEntity<?> response = controller.rateEvent(1L, Map.of("rating", 5));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void rateEvent_invalidRating_shouldReturn400() {
        when(registrationService.rateEvent(1L, 0))
                .thenThrow(new RuntimeException("Rating must be between 1 and 5"));

        ResponseEntity<?> response = controller.rateEvent(1L, Map.of("rating", 0));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getAvgRating_shouldReturn200WithAvgAndCount() {
        when(registrationService.getAvgRating(1L)).thenReturn(4.5);
        when(registrationService.getRatingCount(1L)).thenReturn(10L);

        ResponseEntity<Map<String, Object>> response = controller.getAvgRating(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(4.5, response.getBody().get("avg"));
        assertEquals(10L, response.getBody().get("count"));
    }

    @Test
    void getWaitlistPosition_shouldReturn200() {
        when(registrationService.getWaitlistPosition(1L)).thenReturn(3);

        ResponseEntity<Map<String, Integer>> response = controller.getWaitlistPosition(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().get("position"));
    }

    // ══════════════════════════════════════════════════════
    // ANNOUNCEMENT
    // ══════════════════════════════════════════════════════

    @Test
    void sendAnnouncement_shouldReturn200() {
        doNothing().when(registrationService).sendAnnouncement(eq(1L), anyString(), anyString());

        ResponseEntity<?> response = controller.sendAnnouncement(1L,
                Map.of("subject", "Hello", "message", "World"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // ══════════════════════════════════════════════════════
    // RECOMMENDATIONS
    // ══════════════════════════════════════════════════════

    @Test
    void getRecommendations_shouldReturn200WithResults() {
        RecommendedEventDTO dto = new RecommendedEventDTO(sampleEvent, 85, "Great match");
        when(recommendationService.getRecommendations(42L, 4)).thenReturn(List.of(dto));

        ResponseEntity<List<RecommendedEventDTO>> response = controller.getRecommendations(42L, 4);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(85, response.getBody().get(0).getScore());
        assertEquals("Great match", response.getBody().get(0).getReason());
    }

    @Test
    void getRecommendations_shouldReturnEmptyListWhenNoMatches() {
        when(recommendationService.getRecommendations(42L, 4)).thenReturn(List.of());

        ResponseEntity<List<RecommendedEventDTO>> response = controller.getRecommendations(42L, 4);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    // ══════════════════════════════════════════════════════
    // TEST ENDPOINTS
    // ══════════════════════════════════════════════════════

    @Test
    void testSmsReminders_shouldReturn200() {
        doNothing().when(reminderScheduler).sendUpcomingEventReminders();

        ResponseEntity<?> response = controller.testSmsReminders();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(reminderScheduler).sendUpcomingEventReminders();
    }

    @Test
    void testRatingEmails_shouldReturn200() {
        doNothing().when(reminderScheduler).sendPostEventRatingEmails();

        ResponseEntity<?> response = controller.testRatingEmails();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(reminderScheduler).sendPostEventRatingEmails();
    }
}
