package tn.esprit.event.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class SmsService {

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final boolean enabled;

    public SmsService(
            @Value("${twilio.account-sid:}") String accountSid,
            @Value("${twilio.auth-token:}") String authToken,
            @Value("${twilio.phone-number:}") String fromNumber) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
        this.enabled = accountSid != null && !accountSid.isBlank()
                    && authToken != null && !authToken.isBlank()
                    && fromNumber != null && !fromNumber.isBlank();

        if (enabled) {
            System.out.println("[SMS] Twilio configured successfully (using REST API)");
        } else {
            System.out.println("[SMS] Twilio credentials not configured, SMS disabled");
        }
    }

    /**
     * Sends an SMS via Twilio REST API (no SDK needed).
     */
    public void sendSms(String toPhone, String messageBody) {
        if (!enabled) {
            System.out.println("[SMS] Skipping (not configured): " + toPhone);
            return;
        }
        try {
            String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";

            String body = "To=" + URLEncoder.encode(toPhone, StandardCharsets.UTF_8)
                    + "&From=" + URLEncoder.encode(fromNumber, StandardCharsets.UTF_8)
                    + "&Body=" + URLEncoder.encode(messageBody, StandardCharsets.UTF_8);

            String auth = Base64.getEncoder().encodeToString(
                    (accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201 || response.statusCode() == 200) {
                System.out.println("[SMS] Sent successfully to " + toPhone);
            } else {
                System.err.println("[SMS] Failed (" + response.statusCode() + "): " + response.body());
            }
        } catch (Exception e) {
            System.err.println("[SMS] Error sending to " + toPhone + ": " + e.getMessage());
        }
    }
}
