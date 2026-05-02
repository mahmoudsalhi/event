package tn.esprit.event.services;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.event.entity.Event;
import tn.esprit.event.entity.EventRegistration;
import tn.esprit.event.entity.RegistrationStatus;
import tn.esprit.event.repository.EventRegistrationRepository;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EventReminderScheduler {

    private final EventRegistrationRepository registrationRepository;
    private final EmailService emailService;

    /**
     * Runs every hour. Finds events that have ended and sends rating request emails
     * to registered users who haven't received one yet.
     */
    @Scheduled(fixedRate = 3600000) // every hour
    @Transactional
    public void sendPostEventRatingEmails() {
        LocalDateTime now = LocalDateTime.now();

        List<EventRegistration> pending = registrationRepository.findPendingRatingEmails(
                RegistrationStatus.REGISTERED, now);

        if (pending.isEmpty()) {
            System.out.println("[Rating] No pending rating emails at " + now);
            return;
        }

        System.out.println("[Rating] Found " + pending.size() + " rating emails to send");

        for (EventRegistration reg : pending) {
            try {
                Event event = reg.getEvent();

                emailService.sendRatingRequest(
                        reg.getUserEmail(),
                        reg.getUserName() != null ? reg.getUserName() : "there",
                        event.getTitle(),
                        event.getStartDate()
                );

                reg.setRatingEmailSent(true);
                registrationRepository.save(reg);

                System.out.println("[Rating] Email sent to " + reg.getUserEmail());

            } catch (Exception e) {
                System.err.println("[Rating] Failed for registration " + reg.getId() + ": " + e.getMessage());
            }
        }
    }
}
