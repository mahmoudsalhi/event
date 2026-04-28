package tn.esprit.event.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.event.entity.Event;
import tn.esprit.event.entity.EventStatus;
import tn.esprit.event.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventRegistrationService registrationService;

    private EventService eventService;

    private Event sampleEvent;

    @BeforeEach
    void setUp() {
        // EventService uses @Lazy constructor injection, so we instantiate manually
        eventService = new EventService(eventRepository, registrationService);

        sampleEvent = Event.builder()
                .id(1L)
                .title("English Workshop")
                .description("A beginner workshop")
                .category("Workshop")
                .startDate(LocalDateTime.of(2026, 5, 10, 14, 0))
                .endDate(LocalDateTime.of(2026, 5, 10, 16, 0))
                .location("Tunis")
                .maxAttendees(50)
                .currentAttendees(10)
                .status(EventStatus.UPCOMING)
                .isPublic(true)
                .isRegistrationOpen(true)
                .isFeatured(false)
                .hostName("Mahmoud")
                .hostId(100L)
                .contactEmail("host@example.com")
                .targetLevel("BEGINNER")
                .skillFocus("Speaking")
                .build();
    }

    // ══════════════════════════════════════════════════════
    // CREATE
    // ══════════════════════════════════════════════════════

    @Test
    void create_shouldSaveAndReturnEvent() {
        when(eventRepository.save(any(Event.class))).thenReturn(sampleEvent);

        Event result = eventService.create(sampleEvent);

        assertNotNull(result);
        assertEquals("English Workshop", result.getTitle());
        verify(eventRepository, times(1)).save(sampleEvent);
    }

    // ══════════════════════════════════════════════════════
    // UPDATE
    // ══════════════════════════════════════════════════════

    @Test
    void update_shouldUpdateExistingEventFields() {
        Event updated = Event.builder()
                .title("Updated Title")
                .description("Updated description")
                .category("Seminar")
                .startDate(LocalDateTime.of(2026, 6, 1, 10, 0))
                .location("Sfax")
                .maxAttendees(100)
                .status(EventStatus.UPCOMING)
                .isPublic(true)
                .isRegistrationOpen(true)
                .isFeatured(true)
                .hostName("Ahmed")
                .hostId(200L)
                .contactEmail("ahmed@example.com")
                .targetLevel("ADVANCED")
                .skillFocus("Writing")
                .build();

        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        Event result = eventService.update(1L, updated);

        assertEquals("Updated Title", result.getTitle());
        assertEquals("Updated description", result.getDescription());
        assertEquals("Seminar", result.getCategory());
        assertEquals("Sfax", result.getLocation());
        assertEquals(100, result.getMaxAttendees());
        // currentAttendees should NOT be overwritten (stays at 10)
        assertEquals(10, result.getCurrentAttendees());
        assertEquals("ADVANCED", result.getTargetLevel());
    }

    @Test
    void update_shouldThrowWhenEventNotFound() {
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> eventService.update(999L, sampleEvent));
        assertTrue(ex.getMessage().contains("Event not found"));
    }

    // ══════════════════════════════════════════════════════
    // DELETE
    // ══════════════════════════════════════════════════════

    @Test
    void delete_shouldDeleteExistingEvent() {
        when(eventRepository.existsById(1L)).thenReturn(true);

        eventService.delete(1L);

        verify(eventRepository, times(1)).deleteById(1L);
    }

    @Test
    void delete_shouldThrowWhenEventNotFound() {
        when(eventRepository.existsById(999L)).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> eventService.delete(999L));
        assertTrue(ex.getMessage().contains("Event not found"));
    }

    // ══════════════════════════════════════════════════════
    // DRAFT / UNDRAFT / UNCANCEL
    // ══════════════════════════════════════════════════════

    @Test
    void draft_shouldSetStatusToDraft() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        Event result = eventService.draft(1L);

        assertEquals(EventStatus.DRAFT, result.getStatus());
    }

    @Test
    void draft_shouldThrowWhenEventNotFound() {
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> eventService.draft(999L));
    }

    @Test
    void undraft_shouldSetStatusToUpcoming() {
        sampleEvent.setStatus(EventStatus.DRAFT);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        Event result = eventService.undraft(1L);

        assertEquals(EventStatus.UPCOMING, result.getStatus());
    }

    @Test
    void uncancel_shouldSetStatusToUpcoming() {
        sampleEvent.setStatus(EventStatus.CANCELLED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        Event result = eventService.uncancel(1L);

        assertEquals(EventStatus.UPCOMING, result.getStatus());
    }

    // ══════════════════════════════════════════════════════
    // DUPLICATE
    // ══════════════════════════════════════════════════════

    @Test
    void duplicate_shouldCreateCopyWithDraftStatus() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        Event result = eventService.duplicate(1L);

        assertEquals("Copy of English Workshop", result.getTitle());
        assertEquals(EventStatus.DRAFT, result.getStatus());
        assertEquals(0, result.getCurrentAttendees());
        assertFalse(result.getIsFeatured());
        assertEquals(sampleEvent.getCategory(), result.getCategory());
        assertEquals(sampleEvent.getMaxAttendees(), result.getMaxAttendees());
    }

    @Test
    void duplicate_shouldThrowWhenEventNotFound() {
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> eventService.duplicate(999L));
    }

    // ══════════════════════════════════════════════════════
    // GET BY ID / GET ALL
    // ══════════════════════════════════════════════════════

    @Test
    void getById_shouldReturnEventWhenFound() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));

        Event result = eventService.getById(1L);

        assertNotNull(result);
        assertEquals("English Workshop", result.getTitle());
    }

    @Test
    void getById_shouldThrowWhenNotFound() {
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> eventService.getById(999L));
    }

    @Test
    void getAll_shouldReturnAllEvents() {
        Event event2 = Event.builder().id(2L).title("Speaking Club").build();
        when(eventRepository.findAll()).thenReturn(Arrays.asList(sampleEvent, event2));

        List<Event> result = eventService.getAll();

        assertEquals(2, result.size());
    }

    // ══════════════════════════════════════════════════════
    // BULK OPERATIONS
    // ══════════════════════════════════════════════════════

    @Test
    void bulkDraft_shouldDraftAllFoundEvents() {
        Event event2 = Event.builder().id(2L).title("Club").status(EventStatus.UPCOMING).build();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(eventRepository.findById(2L)).thenReturn(Optional.of(event2));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        eventService.bulkDraft(Arrays.asList(1L, 2L));

        assertEquals(EventStatus.DRAFT, sampleEvent.getStatus());
        assertEquals(EventStatus.DRAFT, event2.getStatus());
        verify(eventRepository, times(2)).save(any(Event.class));
    }

    @Test
    void bulkDraft_shouldSkipMissingEvents() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        eventService.bulkDraft(Arrays.asList(1L, 999L));

        verify(eventRepository, times(1)).save(any(Event.class));
    }

    @Test
    void bulkCancel_shouldCancelEventsAndNotifyRegistrations() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        eventService.bulkCancel(List.of(1L));

        assertEquals(EventStatus.CANCELLED, sampleEvent.getStatus());
        verify(registrationService, times(1)).notifyEventCancellation(1L);
    }
}
