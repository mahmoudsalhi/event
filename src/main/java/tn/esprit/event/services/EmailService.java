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

    /**
     * Sends a registration confirmation email to the user.
     */
    public void sendRegistrationConfirmation(String toEmail, String userName,
                                              String eventTitle, LocalDateTime eventDate,
                                              String eventLocation) {

        String formattedDate = eventDate != null
                ? eventDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a"))
                : "TBD";

        String eventsLink = frontendUrl + "/events";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("You're registered! \uD83C\uDF89 " + eventTitle + " - MiNoLingo");
        message.setText(
            "Hi " + userName + ",\n\n" +
            "Great news! You're successfully registered for:\n\n" +
            "\uD83D\uDCCC Event: " + eventTitle + "\n" +
            "\uD83D\uDCC5 Date: " + formattedDate + "\n" +
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
}
