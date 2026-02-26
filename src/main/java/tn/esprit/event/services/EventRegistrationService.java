package tn.esprit.event.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.event.entity.Event;
import tn.esprit.event.entity.EventRegistration;
import tn.esprit.event.entity.RegistrationStatus;
import tn.esprit.event.repository.EventRegistrationRepository;
import tn.esprit.event.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventRegistrationService {

    private final EventRegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final EmailService emailService;

    public EventRegistration create(Long eventId, EventRegistration registration) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found with id: " + eventId));

        registration.setEvent(event);
        registration.setRegistrationDate(LocalDateTime.now());

        if (registration.getStatus() == null) {
            registration.setStatus(RegistrationStatus.REGISTERED);
        }

        // Increment current attendees on the event
        event.setCurrentAttendees(
                (event.getCurrentAttendees() != null ? event.getCurrentAttendees() : 0) + 1
        );
        eventRepository.save(event);

        EventRegistration saved = registrationRepository.save(registration);

        // Send confirmation email (non-blocking: don't let email failure break registration)
        try {
            if (registration.getUserEmail() != null && !registration.getUserEmail().isBlank()) {
                emailService.sendRegistrationConfirmation(
                        registration.getUserEmail(),
                        registration.getUserName() != null ? registration.getUserName() : "there",
                        event.getTitle(),
                        event.getStartDate(),
                        event.getLocation()
                );
                log.info("Confirmation email sent to {} for event '{}'",
                        registration.getUserEmail(), event.getTitle());
            }
        } catch (Exception e) {
            log.error("Failed to send confirmation email to {}: {}",
                    registration.getUserEmail(), e.getMessage());
        }

        return saved;
    }

    public EventRegistration update(Long id, EventRegistration registration) {
        EventRegistration existing = registrationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Registration not found with id: " + id));

        existing.setUserName(registration.getUserName());
        existing.setUserEmail(registration.getUserEmail());
        existing.setStatus(registration.getStatus());

        return registrationRepository.save(existing);
    }

    public void delete(Long id) {
        EventRegistration registration = registrationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Registration not found with id: " + id));

        // Decrement current attendees on the event
        Event event = registration.getEvent();
        if (event.getCurrentAttendees() != null && event.getCurrentAttendees() > 0) {
            event.setCurrentAttendees(event.getCurrentAttendees() - 1);
            eventRepository.save(event);
        }

        registrationRepository.deleteById(id);
    }

    public EventRegistration getById(Long id) {
        return registrationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Registration not found with id: " + id));
    }

    public List<EventRegistration> getAll() {
        return registrationRepository.findAll();
    }

    public List<EventRegistration> getByEventId(Long eventId) {
        return registrationRepository.findByEventId(eventId);
    }

    public List<EventRegistration> getByUserId(Long userId) {
        return registrationRepository.findByUserId(userId);
    }
}
