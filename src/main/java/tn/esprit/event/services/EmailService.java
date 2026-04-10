package tn.esprit.event.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private String formatEventDate(LocalDateTime eventDate) {
        return eventDate != null
                ? eventDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a"))
                : "TBD";
    }

    /**
     * Sends a registration confirmation email.
     */
    public void sendRegistrationConfirmation(String toEmail, String userName,
                                              String eventTitle, LocalDateTime eventDate,
                                              String eventLocation) {

        String eventsLink = frontendUrl + "/events";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("You're registered! \uD83C\uDF89 " + eventTitle + " - MiNoLingo");
        message.setText(
            "Hi " + userName + ",\n\n" +
            "Great news! You're successfully registered for:\n\n" +
            "\uD83D\uDCCC Event: " + eventTitle + "\n" +
            "\uD83D\uDCC5 Date: " + formatEventDate(eventDate) + "\n" +
            "\uD83D\uDCCD Location: " + (eventLocation != null ? eventLocation : "To be announced") + "\n\n" +
            "What's next?\n" +
            "- Mark your calendar\n" +
            "- Show up and have fun!\n\n" +
            "View your events: " + eventsLink + "\n\n" +
            "See you there!\n" +
            "The MiNoLingo Team"
        );

        mailSender.send(message);
    }

    /**
     * Sends a waitlist confirmation email.
     */
    public void sendWaitlistConfirmation(String toEmail, String userName,
                                          String eventTitle, LocalDateTime eventDate,
                                          String eventLocation) {

        String eventsLink = frontendUrl + "/events";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("You're on the waitlist \u23F3 " + eventTitle + " - MiNoLingo");
        message.setText(
            "Hi " + userName + ",\n\n" +
            "The event below is currently full, but you've been added to the waitlist!\n\n" +
            "\uD83D\uDCCC Event: " + eventTitle + "\n" +
            "\uD83D\uDCC5 Date: " + formatEventDate(eventDate) + "\n" +
            "\uD83D\uDCCD Location: " + (eventLocation != null ? eventLocation : "To be announced") + "\n\n" +
            "What happens next?\n" +
            "- If a spot opens up, you'll be automatically registered\n" +
            "- We'll send you an email to let you know right away\n\n" +
            "View your events: " + eventsLink + "\n\n" +
            "Fingers crossed!\n" +
            "The MiNoLingo Team"
        );

        mailSender.send(message);
    }

    /**
     * Sends a promotion email when a waitlisted user gets a spot.
     */
    public void sendWaitlistPromotion(String toEmail, String userName,
                                       String eventTitle, LocalDateTime eventDate,
                                       String eventLocation) {

        String eventsLink = frontendUrl + "/events";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("A spot opened up! \uD83C\uDF89 " + eventTitle + " - MiNoLingo");
        message.setText(
            "Hi " + userName + ",\n\n" +
            "Great news! A spot just opened up and you've been automatically registered for:\n\n" +
            "\uD83D\uDCCC Event: " + eventTitle + "\n" +
            "\uD83D\uDCC5 Date: " + formatEventDate(eventDate) + "\n" +
            "\uD83D\uDCCD Location: " + (eventLocation != null ? eventLocation : "To be announced") + "\n\n" +
            "You're all set — no further action needed!\n\n" +
            "View your events: " + eventsLink + "\n\n" +
            "See you there!\n" +
            "The MiNoLingo Team"
        );

        mailSender.send(message);
    }

    /**
     * Sends a post-event email asking the user to rate the event.
     */
    public void sendRatingRequest(String toEmail, String userName,
                                   String eventTitle, LocalDateTime eventDate) {

        String eventsLink = frontendUrl + "/events";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("How was " + eventTitle + "? Rate it! ⭐ - MiNoLingo");
        message.setText(
            "Hi " + userName + ",\n\n" +
            "Thanks for attending:\n\n" +
            "📌 Event: " + eventTitle + "\n" +
            "📅 Date: " + formatEventDate(eventDate) + "\n\n" +
            "We'd love to hear your feedback! Rate this event with 1-5 stars " +
            "Your rating helps us improve future events!\n\n" +
            "Thanks,\n" +
            "The MiNoLingo Team"
        );

        mailSender.send(message);
    }
}
