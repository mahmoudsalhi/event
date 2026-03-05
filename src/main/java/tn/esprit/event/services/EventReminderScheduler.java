package tn.esprit.event.services;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.esprit.event.entity.Event;
import tn.esprit.event.entity.EventRegistration;
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
    public void sendUpcomingEventReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.plusHours(24);

        List<EventRegistration> pendingReminders = registrationRepository.findPendingReminders(now, cutoff);

        if (pendingReminders.isEmpty()) {
            System.out.println("[Reminder] No pending SMS reminders at " + now);
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
                    "\uD83D\uDD14 Reminder: \"%s\" starts %s!\n\uD83D\uDCCD %s\nSee you there! \u2014 MiNoLingo",
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
