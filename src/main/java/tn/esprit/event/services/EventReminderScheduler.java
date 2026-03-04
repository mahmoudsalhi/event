package tn.esprit.event.services;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tn.esprit.event.entity.EventRegistration;
import tn.esprit.event.repository.EventRegistrationRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventReminderScheduler {

    private final EventRegistrationRepository registrationRepository;
    private final SmsService smsService;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("EEEE, MMM d 'at' h:mm a");

    /**
     * Runs every hour. Finds events starting within the next 24 hours
     * and sends SMS reminders to registered users who haven't been reminded yet.
     */
    @Scheduled(fixedRate = 3600000) // every 1 hour
    public void sendEventReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime in24Hours = now.plusHours(24);

        List<EventRegistration> toRemind =
                registrationRepository.findRegistrationsNeedingReminder(now, in24Hours);

        if (toRemind.isEmpty()) {
            System.out.println("[Reminder] No reminders to send at " + now);
            return;
        }

        System.out.println("[Reminder] Sending " + toRemind.size() + " SMS reminders...");

        for (EventRegistration reg : toRemind) {
            try {
                String eventTitle = reg.getEvent().getTitle();
                String eventTime = reg.getEvent().getStartDate().format(TIME_FMT);
                String location = reg.getEvent().getLocation();

                String message = String.format(
                        "\uD83D\uDD14 Reminder: \"%s\" starts %s! \uD83D\uDCCD %s — See you there! — MiNoLingo",
                        eventTitle, eventTime,
                        location != null ? location : "Location TBD"
                );

                smsService.sendSms(reg.getPhoneNumber(), message);

                // Mark as reminded so we don't send again
                reg.setSmsReminderSent(true);
                registrationRepository.save(reg);

            } catch (Exception e) {
                System.err.println("[Reminder] Failed for registration #" + reg.getId() + ": " + e.getMessage());
            }
        }

        System.out.println("[Reminder] Done sending reminders.");
    }
}
