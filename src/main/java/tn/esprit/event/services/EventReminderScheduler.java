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
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EventReminderScheduler {

    private final EventRegistrationRepository registrationRepository;
    private final SmsService smsService;

    /**
     * Runs every hour. Finds events starting within the next 24 hours
     * and sends SMS reminders to registered users who haven't been reminded yet.
     */
    @Scheduled(fixedRate = 3600000) // every hour
    @Transactional
    public void sendUpcomingEventReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.plusHours(24);

        System.out.println("[Reminder] Checking for reminders between " + now + " and " + cutoff);

        List<EventRegistration> pendingReminders = registrationRepository.findPendingReminders(
                RegistrationStatus.REGISTERED, now, cutoff);

        if (pendingReminders.isEmpty()) {
            System.out.println("[Reminder] No pending SMS reminders");
            return;
        }

        System.out.println("[Reminder] Found " + pendingReminders.size() + " reminders to send");

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEEE 'at' h:mm a");

        for (EventRegistration reg : pendingReminders) {
            try {
                Event event = reg.getEvent();
                String time = event.getStartDate() != null
                        ? event.getStartDate().format(fmt)
                        : "soon";

                String message = String.format(
                    "Reminder: \"%s\" starts %s! Location: %s. See you there! - MiNoLingo",
                    event.getTitle(),
                    time,
                    event.getLocation() != null ? event.getLocation() : "Check app for details"
                );

                smsService.sendSms(reg.getPhoneNumber(), message);

                // Mark as sent
                reg.setSmsReminderSent(true);
                registrationRepository.save(reg);

            } catch (Exception e) {
                System.err.println("[Reminder] Failed for registration " + reg.getId() + ": " + e.getMessage());
            }
        }
    }
}
