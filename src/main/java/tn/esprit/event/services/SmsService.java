package tn.esprit.event.services;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    private final String fromNumber;
    private boolean initialized = false;

    public SmsService(
            @Value("${twilio.account-sid}") String accountSid,
            @Value("${twilio.auth-token}") String authToken,
            @Value("${twilio.phone-number}") String fromNumber) {
        this.fromNumber = fromNumber;
        try {
            Twilio.init(accountSid, authToken);
            this.initialized = true;
            System.out.println("[SMS] Twilio initialized successfully");
        } catch (Exception e) {
            System.err.println("[SMS] Failed to initialize Twilio: " + e.getMessage());
        }
    }

    /**
     * Sends an SMS to the given phone number.
     */
    public void sendSms(String toPhone, String messageBody) {
        if (!initialized) {
            System.err.println("[SMS] Twilio not initialized, skipping SMS to " + toPhone);
            return;
        }
        try {
            Message message = Message.creator(
                    new PhoneNumber(toPhone),
                    new PhoneNumber(fromNumber),
                    messageBody
            ).create();
            System.out.println("[SMS] Sent: " + message.getSid() + " to " + toPhone);
        } catch (Exception e) {
            System.err.println("[SMS] Failed to send to " + toPhone + ": " + e.getMessage());
        }
    }
}
