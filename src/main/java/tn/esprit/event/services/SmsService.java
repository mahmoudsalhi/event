package tn.esprit.event.services;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.phone-number:}")
    private String fromNumber;

    private boolean initialized = false;

    @PostConstruct
    public void init() {
        if (accountSid != null && !accountSid.isEmpty()
                && !accountSid.startsWith("${")
                && authToken != null && !authToken.isEmpty()) {
            Twilio.init(accountSid, authToken);
            initialized = true;
            System.out.println("[SMS] Twilio initialized successfully.");
        } else {
            System.out.println("[SMS] Twilio credentials not configured. SMS will be disabled.");
        }
    }

    /**
     * Sends an SMS to the given phone number.
     */
    public void sendSms(String toPhone, String messageBody) {
        if (!initialized) {
            System.out.println("[SMS] Skipped (Twilio not configured): " + messageBody);
            return;
        }
        try {
            Message message = Message.creator(
                    new PhoneNumber(toPhone),
                    new PhoneNumber(fromNumber),
                    messageBody
            ).create();
            System.out.println("SMS sent: SID=" + message.getSid() + " to=" + toPhone);
        } catch (Exception e) {
            System.err.println("Failed to send SMS to " + toPhone + ": " + e.getMessage());
        }
    }
}
