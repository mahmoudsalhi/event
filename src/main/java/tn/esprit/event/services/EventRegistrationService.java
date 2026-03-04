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
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventRegistrationService {

    private final EventRegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final EmailService emailService;

    @Transactional
    public EventRegistration create(Long eventId, EventRegistration registration) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found with id: " + eventId));

        registration.setEvent(event);
        registration.setRegistrationDate(LocalDateTime.now());

        // Check if event is full → waitlist instead of register
        boolean isFull = event.getMaxAttendees() != null
                && (event.getCurrentAttendees() != null ? event.getCurrentAttendees() : 0)
                        >= event.getMaxAttendees();

        if (isFull) {
            // ── WAITLIST ──
            registration.setStatus(RegistrationStatus.WAITLISTED);
            // Do NOT increment attendee count for waitlisted users

            EventRegistration saved = registrationRepository.save(registration);

            // Send waitlist email
            sendEmailSafely(() -> emailService.sendWaitlistConfirmation(
                    registration.getUserEmail(),
                    registration.getUserName() != null ? registration.getUserName() : "there",
                    event.getTitle(),
                    event.getStartDate(),
                    event.getLocation()
            ), registration.getUserEmail(), "waitlist confirmation");

            return saved;

        } else {
            // ── REGISTER ──
            if (registration.getStatus() == null) {
                registration.setStatus(RegistrationStatus.REGISTERED);
            }

            // Increment current attendees
            event.setCurrentAttendees(
                    (event.getCurrentAttendees() != null ? event.getCurrentAttendees() : 0) + 1
            );
            eventRepository.save(event);

            EventRegistration saved = registrationRepository.save(registration);

            // Send confirmation email
            sendEmailSafely(() -> emailService.sendRegistrationConfirmation(
                    registration.getUserEmail(),
                    registration.getUserName() != null ? registration.getUserName() : "there",
                    event.getTitle(),
                    event.getStartDate(),
                    event.getLocation()
            ), registration.getUserEmail(), "registration confirmation");

            return saved;
        }
    }

    public EventRegistration update(Long id, EventRegistration registration) {
        EventRegistration existing = registrationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Registration not found with id: " + id));

        existing.setUserName(registration.getUserName());
        existing.setUserEmail(registration.getUserEmail());
        existing.setStatus(registration.getStatus());

        return registrationRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        EventRegistration registration = registrationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Registration not found with id: " + id));

        Event event = registration.getEvent();
        boolean wasRegistered = registration.getStatus() == RegistrationStatus.REGISTERED
                || registration.getStatus() == RegistrationStatus.CONFIRMED;

        // Delete the registration
        registrationRepository.deleteById(id);

        if (wasRegistered) {
            // Decrement current attendees
            if (event.getCurrentAttendees() != null && event.getCurrentAttendees() > 0) {
                event.setCurrentAttendees(event.getCurrentAttendees() - 1);
                eventRepository.save(event);
            }

            // Auto-promote the first waitlisted user (FIFO)
            promoteNextWaitlisted(event);
        }
        // If WAITLISTED user cancels: just delete, no attendee change, no promotion
    }

    /**
     * Promotes the first waitlisted user for the given event to REGISTERED.
     */
    private void promoteNextWaitlisted(Event event) {
        Optional<EventRegistration> nextInLine = registrationRepository
                .findFirstByEventIdAndStatusOrderByRegistrationDateAsc(
                        event.getId(), RegistrationStatus.WAITLISTED);

        if (nextInLine.isPresent()) {
            EventRegistration promoted = nextInLine.get();
            promoted.setStatus(RegistrationStatus.REGISTERED);
            registrationRepository.save(promoted);

            // Increment attendee count for the newly promoted user
            event.setCurrentAttendees(
                    (event.getCurrentAttendees() != null ? event.getCurrentAttendees() : 0) + 1
            );
            eventRepository.save(event);

            log.info("Auto-promoted user {} from waitlist for event '{}'",
                    promoted.getUserEmail(), event.getTitle());

            // Send promotion email
            sendEmailSafely(() -> emailService.sendWaitlistPromotion(
                    promoted.getUserEmail(),
                    promoted.getUserName() != null ? promoted.getUserName() : "there",
                    event.getTitle(),
                    event.getStartDate(),
                    event.getLocation()
            ), promoted.getUserEmail(), "waitlist promotion");
        }
    }

    /**
     * Sends an email safely — never lets email failure break the main operation.
     */
    private void sendEmailSafely(Runnable emailAction, String toEmail, String emailType) {
        try {
            if (toEmail != null && !toEmail.isBlank()) {
                emailAction.run();
                log.info("{} email sent to {}", emailType, toEmail);
            }
        } catch (Exception e) {
            log.error("Failed to send {} email to {}: {}", emailType, toEmail, e.getMessage());
        }
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

    /**
     * QR Check-in: looks up registration by checkInCode and marks as ATTENDED.
     */
    @Transactional
    public EventRegistration checkIn(String checkInCode) {
        EventRegistration registration = registrationRepository.findByCheckInCode(checkInCode)
                .orElseThrow(() -> new RuntimeException("Invalid check-in code: " + checkInCode));

        if (registration.getStatus() == RegistrationStatus.ATTENDED) {
            throw new RuntimeException("Already checked in");
        }

        if (registration.getStatus() == RegistrationStatus.WAITLISTED) {
            throw new RuntimeException("Cannot check in a waitlisted registration");
        }

        registration.setStatus(RegistrationStatus.ATTENDED);
        return registrationRepository.save(registration);
    }
}
