package tn.esprit.event.services;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    private final String fromNumber;
    private final boolean enabled;

    public SmsService(
            @Value("${twilio.account-sid:}") String accountSid,
            @Value("${twilio.auth-token:}") String authToken,
            @Value("${twilio.phone-number:}") String fromNumber) {
        this.fromNumber = fromNumber;
        if (accountSid != null && !accountSid.isBlank()
                && authToken != null && !authToken.isBlank()) {
            try {
                Twilio.init(accountSid, authToken);
                this.enabled = true;
                System.out.println("[SMS] Twilio initialized successfully");
            } catch (Exception e) {
                this.enabled = false;
                System.err.println("[SMS] Failed to initialize Twilio: " + e.getMessage());
            }
        } else {
            this.enabled = false;
            System.out.println("[SMS] Twilio credentials not configured, SMS disabled");
        }
    }

    /**
     * Sends an SMS to the given phone number.
     */
    public void sendSms(String toPhone, String messageBody) {
        if (!enabled) {
            System.out.println("[SMS] Skipping (not configured): " + toPhone);
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
