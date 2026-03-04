package tn.esprit.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.event.entity.Event;
import tn.esprit.event.entity.EventRegistration;
import tn.esprit.event.services.EventService;
import tn.esprit.event.services.EventRegistrationService;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final EventRegistrationService registrationService;

    // ══════════════════════════════════════════════════════
    // EVENT CRUD
    // ══════════════════════════════════════════════════════

    @PostMapping("/create-event")
    public ResponseEntity<Event> create(@RequestBody Event event) {
        Event created = eventService.create(event);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/get-all-events")
    public ResponseEntity<List<Event>> getAll() {
        return ResponseEntity.ok(eventService.getAll());
    }

    @GetMapping("/get-event-by-id/{id}")
    public ResponseEntity<Event> getById(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getById(id));
    }

    @PutMapping("/update-event/{id}")
    public ResponseEntity<Event> update(@PathVariable Long id, @RequestBody Event event) {
        return ResponseEntity.ok(eventService.update(id, event));
    }

    @DeleteMapping("/delete-event/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        eventService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ══════════════════════════════════════════════════════
    // REGISTRATION ENDPOINTS (nested under /api/events)
    // ══════════════════════════════════════════════════════

    @PostMapping("/registrations/create/{eventId}")
    public ResponseEntity<EventRegistration> createRegistration(
            @PathVariable Long eventId,
            @RequestBody EventRegistration registration) {
        EventRegistration created = registrationService.create(eventId, registration);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/registrations/get-all")
    public ResponseEntity<List<EventRegistration>> getAllRegistrations() {
        return ResponseEntity.ok(registrationService.getAll());
    }

    @GetMapping("/registrations/get-by-id/{id}")
    public ResponseEntity<EventRegistration> getRegistrationById(@PathVariable Long id) {
        return ResponseEntity.ok(registrationService.getById(id));
    }

    @GetMapping("/registrations/get-by-event/{eventId}")
    public ResponseEntity<List<EventRegistration>> getRegistrationsByEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(registrationService.getByEventId(eventId));
    }

    @GetMapping("/registrations/get-by-user/{userId}")
    public ResponseEntity<List<EventRegistration>> getRegistrationsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(registrationService.getByUserId(userId));
    }

    @PutMapping("/registrations/update/{id}")
    public ResponseEntity<EventRegistration> updateRegistration(
            @PathVariable Long id,
            @RequestBody EventRegistration registration) {
        return ResponseEntity.ok(registrationService.update(id, registration));
    }

    @DeleteMapping("/registrations/delete/{id}")
    public ResponseEntity<Void> deleteRegistration(@PathVariable Long id) {
        registrationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/registrations/check-in/{code}")
    public ResponseEntity<?> checkIn(@PathVariable String code) {
        try {
            EventRegistration updated = registrationService.checkIn(code);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    java.util.Map.of("error", e.getMessage())
            );
        }
    }
}
