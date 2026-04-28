package tn.esprit.event.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.event.entity.Event;
import tn.esprit.event.entity.EventRegistration;
import tn.esprit.event.entity.EventStatus;
import tn.esprit.event.entity.RegistrationStatus;
import tn.esprit.event.repository.EventRegistrationRepository;
import tn.esprit.event.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventRegistrationServiceTest {

    @Mock
    private EventRegistrationRepository registrationRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private EventRegistrationService registrationService;

    private Event sampleEvent;
    private EventRegistration sampleRegistration;

    @BeforeEach
    void setUp() {
        sampleEvent = Event.builder()
                .id(1L)
                .title("English Workshop")
                .startDate(LocalDateTime.of(2026, 5, 10, 14, 0))
                .location("Tunis")
                .maxAttendees(50)
                .currentAttendees(10)
                .status(EventStatus.UPCOMING)
                .isPublic(true)
                .isRegistrationOpen(true)
                .build();

        sampleRegistration = EventRegistration.builder()
                .id(1L)
                .event(sampleEvent)
                .userId(42L)
                .userName("John")
                .userEmail("john@example.com")
                .registrationDate(LocalDateTime.now())
                .status(RegistrationStatus.PENDING)
                .checkInCode("abc-123")
                .build();
    }

    // ══════════════════════════════════════════════════════
    // CREATE REGISTRATION
    // ══════════════════════════════════════════════════════

    @Test
    void create_withAvailableSpots_shouldSetStatusToPending() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(registrationRepository.save(any(EventRegistration.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EventRegistration reg = EventRegistration.builder()
                .userName("Alice")
                .userEmail("alice@example.com")
                .build();

        EventRegistration result = registrationService.create(1L, reg);

        assertEquals(RegistrationStatus.PENDING, result.getStatus());
        assertFalse(result.getRequestedWaitlist());
        assertNotNull(result.getRegistrationDate());
        assertEquals(sampleEvent, result.getEvent());
    }

    @Test
    void create_whenEventIsFull_shouldSetStatusToWaitlisted() {
        sampleEvent.setCurrentAttendees(50); // max is 50 → full
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(registrationRepository.save(any(EventRegistration.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EventRegistration reg = EventRegistration.builder()
                .userName("Bob")
                .userEmail("bob@example.com")
                .build();

        EventRegistration result = registrationService.create(1L, reg);

        assertEquals(RegistrationStatus.WAITLISTED, result.getStatus());
        assertTrue(result.getRequestedWaitlist());
    }

    @Test
    void create_whenEventIsPrivate_shouldThrow() {
        sampleEvent.setIsPublic(false);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));

        EventRegistration reg = EventRegistration.builder().build();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> registrationService.create(1L, reg));
        assertTrue(ex.getMessage().contains("not available for registration"));
    }

    @Test
    void create_whenRegistrationIsClosed_shouldThrow() {
        sampleEvent.setIsRegistrationOpen(false);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));

        EventRegistration reg = EventRegistration.builder().build();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> registrationService.create(1L, reg));
        assertTrue(ex.getMessage().contains("Registration is closed"));
    }

    @Test
    void create_whenEventNotFound_shouldThrow() {
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> registrationService.create(999L, new EventRegistration()));
    }

    // ══════════════════════════════════════════════════════
    // APPROVE
    // ══════════════════════════════════════════════════════

    @Test
    void approve_pendingWithSpace_shouldSetRegisteredAndIncrementAttendees() {
        sampleRegistration.setStatus(RegistrationStatus.PENDING);
        when(registrationRepository.findById(1L)).thenReturn(Optional.of(sampleRegistration));
        when(registrationRepository.save(any(EventRegistration.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        EventRegistration result = registrationService.approve(1L);

        assertEquals(RegistrationStatus.REGISTERED, result.getStatus());
        assertEquals(11, sampleEvent.getCurrentAttendees()); // was 10, now 11
        verify(eventRepository, times(1)).save(sampleEvent);
    }

    @Test
    void approve_pendingWhenFull_shouldSetWaitlisted() {
        sampleRegistration.setStatus(RegistrationStatus.PENDING);
        sampleEvent.setCurrentAttendees(50); // full
        when(registrationRepository.findById(1L)).thenReturn(Optional.of(sampleRegistration));
        when(registrationRepository.save(any(EventRegistration.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EventRegistration result = registrationService.approve(1L);

        assertEquals(RegistrationStatus.WAITLISTED, result.getStatus());
        verify(eventRepository, never()).save(any(Event.class)); // no attendee increment
    }

    @Test
    void approve_nonPending_shouldThrow() {
        sampleRegistration.setStatus(RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(1L)).thenReturn(Optional.of(sampleRegistration));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> registrationService.approve(1L));
        assertTrue(ex.getMessage().contains("Only PENDING"));
    }

    @Test
    void approve_notFound_shouldThrow() {
        when(registrationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> registrationService.approve(999L));
    }

    // ══════════════════════════════════════════════════════
    // REJECT
    // ══════════════════════════════════════════════════════

    @Test
    void reject_pendingRegistration_shouldSetRejected() {
        sampleRegistration.setStatus(RegistrationStatus.PENDING);
        when(registrationRepository.findById(1L)).thenReturn(Optional.of(sampleRegistration));
        when(registrationRepository.save(any(EventRegistration.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EventRegistration result = registrationService.reject(1L);

        assertEquals(RegistrationStatus.REJECTED, result.getStatus());
    }

    @Test
    void reject_nonPending_shouldThrow() {
        sampleRegistration.setStatus(RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(1L)).thenReturn(Optional.of(sampleRegistration));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> registrationService.reject(1L));
        assertTrue(ex.getMessage().contains("Only PENDING"));
    }

    // ══════════════════════════════════════════════════════
    // UPDATE
    // ══════════════════════════════════════════════════════

    @Test
    void update_shouldUpdateFields() {
        EventRegistration updated = EventRegistration.builder()
                .userName("New Name")
                .userEmail("new@example.com")
                .status(RegistrationStatus.CONFIRMED)
                .build();

        when(registrationRepository.findById(1L)).thenReturn(Optional.of(sampleRegistration));
        when(registrationRepository.save(any(EventRegistration.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EventRegistration result = registrationService.update(1L, updated);

        assertEquals("New Name", result.getUserName());
        assertEquals("new@example.com", result.getUserEmail());
        assertEquals(RegistrationStatus.CONFIRMED, result.getStatus());
    }

    @Test
    void update_notFound_shouldThrow() {
        when(registrationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> registrationService.update(999L, new EventRegistration()));
    }

    // ══════════════════════════════════════════════════════
    // DELETE
    // ══════════════════════════════════════════════════════

    @Test
    void delete_registeredUser_shouldDecrementAttendeesAndPromoteWaitlisted() {
        sampleRegistration.setStatus(RegistrationStatus.REGISTERED);
        EventRegistration waitlisted = EventRegistration.builder()
                .id(2L)
                .event(sampleEvent)
                .userName("Waitlisted User")
                .userEmail("wait@example.com")
                .status(RegistrationStatus.WAITLISTED)
                .registrationDate(LocalDateTime.now())
                .build();

        when(registrationRepository.findById(1L)).thenReturn(Optional.of(sampleRegistration));
        when(registrationRepository.findFirstByEventIdAndStatusOrderByRegistrationDateAsc(
                eq(1L), eq(RegistrationStatus.WAITLISTED)))
                .thenReturn(Optional.of(waitlisted));
        when(registrationRepository.save(any(EventRegistration.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        registrationService.delete(1L);

        assertEquals(9, sampleEvent.getCurrentAttendees()); // decremented from 10
        assertEquals(RegistrationStatus.PENDING, waitlisted.getStatus()); // promoted
        verify(registrationRepository, times(1)).deleteById(1L);
    }

    @Test
    void delete_waitlistedUser_shouldNotChangeAttendees() {
        sampleRegistration.setStatus(RegistrationStatus.WAITLISTED);
        when(registrationRepository.findById(1L)).thenReturn(Optional.of(sampleRegistration));

        registrationService.delete(1L);

        assertEquals(10, sampleEvent.getCurrentAttendees()); // unchanged
        verify(registrationRepository, times(1)).deleteById(1L);
        // No waitlist promotion for non-registered users
        verify(registrationRepository, never())
                .findFirstByEventIdAndStatusOrderByRegistrationDateAsc(any(), any());
    }

    @Test
    void delete_notFound_shouldThrow() {
        when(registrationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> registrationService.delete(999L));
    }

    // ══════════════════════════════════════════════════════
    // CHECK-IN
    // ══════════════════════════════════════════════════════

    @Test
    void checkIn_validCode_shouldSetAttended() {
        sampleRegistration.setStatus(RegistrationStatus.REGISTERED);
        when(registrationRepository.findByCheckInCode("abc-123"))
                .thenReturn(Optional.of(sampleRegistration));
        when(registrationRepository.save(any(EventRegistration.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EventRegistration result = registrationService.checkIn("abc-123");

        assertEquals(RegistrationStatus.ATTENDED, result.getStatus());
    }

    @Test
    void checkIn_alreadyCheckedIn_shouldThrow() {
        sampleRegistration.setStatus(RegistrationStatus.ATTENDED);
        when(registrationRepository.findByCheckInCode("abc-123"))
                .thenReturn(Optional.of(sampleRegistration));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> registrationService.checkIn("abc-123"));
        assertTrue(ex.getMessage().contains("Already checked in"));
    }

    @Test
    void checkIn_waitlistedUser_shouldThrow() {
        sampleRegistration.setStatus(RegistrationStatus.WAITLISTED);
        when(registrationRepository.findByCheckInCode("abc-123"))
                .thenReturn(Optional.of(sampleRegistration));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> registrationService.checkIn("abc-123"));
        assertTrue(ex.getMessage().contains("Cannot check in a waitlisted"));
    }

    @Test
    void checkIn_invalidCode_shouldThrow() {
        when(registrationRepository.findByCheckInCode("invalid"))
                .thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> registrationService.checkIn("invalid"));
        assertTrue(ex.getMessage().contains("Invalid check-in code"));
    }

    // ══════════════════════════════════════════════════════
    // RATE EVENT
    // ══════════════════════════════════════════════════════

    @Test
    void rateEvent_validRating_shouldSaveRating() {
        when(registrationRepository.findById(1L)).thenReturn(Optional.of(sampleRegistration));
        when(registrationRepository.save(any(EventRegistration.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EventRegistration result = registrationService.rateEvent(1L, 4);

        assertEquals(4, result.getRating());
    }

    @Test
    void rateEvent_ratingTooLow_shouldThrow() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> registrationService.rateEvent(1L, 0));
        assertTrue(ex.getMessage().contains("Rating must be between 1 and 5"));
    }

    @Test
    void rateEvent_ratingTooHigh_shouldThrow() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> registrationService.rateEvent(1L, 6));
        assertTrue(ex.getMessage().contains("Rating must be between 1 and 5"));
    }

    @Test
    void rateEvent_notFound_shouldThrow() {
        when(registrationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> registrationService.rateEvent(999L, 3));
    }

    // ══════════════════════════════════════════════════════
    // QUERY METHODS
    // ══════════════════════════════════════════════════════

    @Test
    void getById_shouldReturnRegistration() {
        when(registrationRepository.findById(1L)).thenReturn(Optional.of(sampleRegistration));

        EventRegistration result = registrationService.getById(1L);

        assertEquals("John", result.getUserName());
    }

    @Test
    void getById_notFound_shouldThrow() {
        when(registrationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> registrationService.getById(999L));
    }

    @Test
    void getAll_shouldReturnAllRegistrations() {
        when(registrationRepository.findAll())
                .thenReturn(Arrays.asList(sampleRegistration));

        List<EventRegistration> result = registrationService.getAll();

        assertEquals(1, result.size());
    }

    @Test
    void getByEventId_shouldReturnRegistrationsForEvent() {
        when(registrationRepository.findByEventId(1L))
                .thenReturn(Arrays.asList(sampleRegistration));

        List<EventRegistration> result = registrationService.getByEventId(1L);

        assertEquals(1, result.size());
    }

    @Test
    void getByUserId_shouldReturnRegistrationsForUser() {
        when(registrationRepository.findByUserId(42L))
                .thenReturn(Arrays.asList(sampleRegistration));

        List<EventRegistration> result = registrationService.getByUserId(42L);

        assertEquals(1, result.size());
    }

    @Test
    void getPending_shouldReturnOnlyPendingRegistrations() {
        when(registrationRepository.findByStatus(RegistrationStatus.PENDING))
                .thenReturn(Arrays.asList(sampleRegistration));

        List<EventRegistration> result = registrationService.getPending();

        assertEquals(1, result.size());
        assertEquals(RegistrationStatus.PENDING, result.get(0).getStatus());
    }

    // ══════════════════════════════════════════════════════
    // WAITLIST POSITION
    // ══════════════════════════════════════════════════════

    @Test
    void getWaitlistPosition_shouldReturn1BasedPosition() {
        when(registrationRepository.findById(1L)).thenReturn(Optional.of(sampleRegistration));
        when(registrationRepository.findWaitlistPosition(eq(1L), any(LocalDateTime.class)))
                .thenReturn(2L); // 0-based index 2

        int position = registrationService.getWaitlistPosition(1L);

        assertEquals(3, position); // 1-based: 2 + 1 = 3
    }

    // ══════════════════════════════════════════════════════
    // RATINGS QUERY
    // ══════════════════════════════════════════════════════

    @Test
    void getAvgRating_shouldReturnAverage() {
        when(registrationRepository.findAvgRatingByEventId(1L)).thenReturn(4.5);

        Double avg = registrationService.getAvgRating(1L);

        assertEquals(4.5, avg);
    }

    @Test
    void getRatingCount_shouldReturnCount() {
        when(registrationRepository.countRatingsByEventId(1L)).thenReturn(10L);

        Long count = registrationService.getRatingCount(1L);

        assertEquals(10L, count);
    }

    // ══════════════════════════════════════════════════════
    // NOTIFY EVENT CANCELLATION
    // ══════════════════════════════════════════════════════

    @Test
    void notifyEventCancellation_shouldSendEmailsToActiveRegistrations() {
        EventRegistration registered = EventRegistration.builder()
                .id(1L).userName("A").userEmail("a@test.com")
                .status(RegistrationStatus.REGISTERED).build();
        EventRegistration waitlisted = EventRegistration.builder()
                .id(2L).userName("B").userEmail("b@test.com")
                .status(RegistrationStatus.WAITLISTED).build();
        EventRegistration cancelled = EventRegistration.builder()
                .id(3L).userName("C").userEmail("c@test.com")
                .status(RegistrationStatus.CANCELLED).build();

        when(registrationRepository.findByEventId(1L))
                .thenReturn(Arrays.asList(registered, waitlisted, cancelled));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));

        registrationService.notifyEventCancellation(1L);

        // Only REGISTERED and WAITLISTED get notified, not CANCELLED
        verify(emailService, times(2)).sendEventCancellation(
                anyString(), anyString(), anyString(), any(LocalDateTime.class), anyString());
    }

    @Test
    void notifyEventCancellation_whenEventNotFound_shouldDoNothing() {
        when(registrationRepository.findByEventId(999L)).thenReturn(List.of());
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        registrationService.notifyEventCancellation(999L);

        verify(emailService, never()).sendEventCancellation(
                anyString(), anyString(), anyString(), any(), anyString());
    }

    // ══════════════════════════════════════════════════════
    // SEND ANNOUNCEMENT
    // ══════════════════════════════════════════════════════

    @Test
    void sendAnnouncement_shouldSendToActiveRegistrations() {
        EventRegistration registered = EventRegistration.builder()
                .id(1L).userName("A").userEmail("a@test.com")
                .status(RegistrationStatus.REGISTERED).build();
        EventRegistration rejected = EventRegistration.builder()
                .id(2L).userName("B").userEmail("b@test.com")
                .status(RegistrationStatus.REJECTED).build();

        when(registrationRepository.findByEventId(1L))
                .thenReturn(Arrays.asList(registered, rejected));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));

        registrationService.sendAnnouncement(1L, "Subject", "Body");

        // Only REGISTERED gets the announcement, not REJECTED
        verify(emailService, times(1)).sendAnnouncement(
                anyString(), anyString(), anyString(), eq("Subject"), eq("Body"));
    }
}
