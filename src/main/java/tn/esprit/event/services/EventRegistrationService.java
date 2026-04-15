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

        if (Boolean.FALSE.equals(event.getIsPublic())) {
            throw new RuntimeException("This event is not available for registration.");
        }

        if (Boolean.FALSE.equals(event.getIsRegistrationOpen())) {
            throw new RuntimeException("Registration is closed for this event.");
        }

        registration.setEvent(event);
        registration.setRegistrationDate(LocalDateTime.now());

        // All registrations start as PENDING — admin must approve
        registration.setStatus(RegistrationStatus.PENDING);

        // Track whether the event was full at request time so admin UI can show context
        boolean isFull = event.getMaxAttendees() != null
                && (event.getCurrentAttendees() != null ? event.getCurrentAttendees() : 0)
                        >= event.getMaxAttendees();
        registration.setRequestedWaitlist(isFull);

        EventRegistration saved = registrationRepository.save(registration);

        // Send "request received" email
        sendEmailSafely(() -> emailService.sendPendingConfirmation(
                registration.getUserEmail(),
                registration.getUserName() != null ? registration.getUserName() : "there",
                event.getTitle(),
                event.getStartDate(),
                event.getLocation(),
                isFull
        ), registration.getUserEmail(), "pending confirmation");

        return saved;
    }

    /**
     * Admin approves a PENDING registration.
     * If the event still has capacity → REGISTERED + increment attendees.
     * If the event is full → WAITLISTED (no attendee increment).
     */
    @Transactional
    public EventRegistration approve(Long registrationId) {
        EventRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new RuntimeException("Registration not found: " + registrationId));

        if (registration.getStatus() != RegistrationStatus.PENDING) {
            throw new RuntimeException("Only PENDING registrations can be approved");
        }

        Event event = registration.getEvent();

        boolean isFull = event.getMaxAttendees() != null
                && (event.getCurrentAttendees() != null ? event.getCurrentAttendees() : 0)
                        >= event.getMaxAttendees();

        if (isFull) {
            registration.setStatus(RegistrationStatus.WAITLISTED);
            EventRegistration saved = registrationRepository.save(registration);

            sendEmailSafely(() -> emailService.sendWaitlistConfirmation(
                    registration.getUserEmail(),
                    registration.getUserName() != null ? registration.getUserName() : "there",
                    event.getTitle(),
                    event.getStartDate(),
                    event.getLocation()
            ), registration.getUserEmail(), "waitlist confirmation");

            return saved;
        } else {
            registration.setStatus(RegistrationStatus.REGISTERED);
            event.setCurrentAttendees(
                    (event.getCurrentAttendees() != null ? event.getCurrentAttendees() : 0) + 1
            );
            eventRepository.save(event);
            EventRegistration saved = registrationRepository.save(registration);

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

    /**
     * Admin rejects a PENDING registration.
     */
    @Transactional
    public EventRegistration reject(Long registrationId) {
        EventRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new RuntimeException("Registration not found: " + registrationId));

        if (registration.getStatus() != RegistrationStatus.PENDING) {
            throw new RuntimeException("Only PENDING registrations can be rejected");
        }

        Event event = registration.getEvent();
        registration.setStatus(RegistrationStatus.REJECTED);
        EventRegistration saved = registrationRepository.save(registration);

        sendEmailSafely(() -> emailService.sendRejectionEmail(
                registration.getUserEmail(),
                registration.getUserName() != null ? registration.getUserName() : "there",
                event.getTitle()
        ), registration.getUserEmail(), "rejection");

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

    public List<EventRegistration> getPending() {
        return registrationRepository.findByStatus(RegistrationStatus.PENDING);
    }

    public int getWaitlistPosition(Long registrationId) {
        EventRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new RuntimeException("Registration not found: " + registrationId));
        Long position = registrationRepository.findWaitlistPosition(
                registration.getEvent().getId(), registration.getRegistrationDate());
        return position.intValue() + 1; // 1-based
    }

    public void sendAnnouncement(Long eventId, String subject, String body) {
        List<EventRegistration> registrations = registrationRepository.findByEventId(eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found: " + eventId));
        registrations.stream()
                .filter(r -> r.getStatus() == RegistrationStatus.REGISTERED
                          || r.getStatus() == RegistrationStatus.WAITLISTED
                          || r.getStatus() == RegistrationStatus.CONFIRMED)
                .forEach(r -> sendEmailSafely(() -> emailService.sendAnnouncement(
                        r.getUserEmail(),
                        r.getUserName() != null ? r.getUserName() : "there",
                        event.getTitle(),
                        subject,
                        body
                ), r.getUserEmail(), "announcement"));
    }

    public Double getAvgRating(Long eventId) {
        return registrationRepository.findAvgRatingByEventId(eventId);
    }

    public Long getRatingCount(Long eventId) {
        return registrationRepository.countRatingsByEventId(eventId);
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

    /**
     * Rate an event: sets the rating (1-5) on the registration.
     */
    @Transactional
    public EventRegistration rateEvent(Long registrationId, int rating) {
        if (rating < 1 || rating > 5) {
            throw new RuntimeException("Rating must be between 1 and 5");
        }

        EventRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new RuntimeException("Registration not found with id: " + registrationId));

        registration.setRating(rating);
        return registrationRepository.save(registration);
    }
}
