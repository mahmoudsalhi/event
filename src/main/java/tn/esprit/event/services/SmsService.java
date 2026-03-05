package tn.esprit.event.services;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.phone-number}")
    private String fromNumber;

    @PostConstruct
    public void init() {
        Twilio.init(accountSid, authToken);
    }

    /**
     * Sends an SMS to the given phone number.
     */
    public void sendSms(String toPhone, String messageBody) {
        try {
            Message message = Message.creator(
                    new PhoneNumber(toPhone),
                    new PhoneNumber(fromNumber),
                    messageBody
            ).create();
            System.out.println("SMS sent: " + message.getSid() + " to " + toPhone);
        } catch (Exception e) {
            System.err.println("Failed to send SMS to " + toPhone + ": " + e.getMessage());
        }
    }
}
