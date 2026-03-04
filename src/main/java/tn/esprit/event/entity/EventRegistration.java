package tn.esprit.event.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "event_registrations")
public class EventRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ── Link to Event ─────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private Event event;

    // Expose the FK as a readable field in JSON (read-only, managed by JPA)
    @Column(name = "event_id", insertable = false, updatable = false)
    private Long eventId;

    // ── User Info ─────────────────────────────────────────
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "user_email")
    private String userEmail;

    // ── Registration Details ──────────────────────────────
    @Column(name = "registration_date")
    private LocalDateTime registrationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private RegistrationStatus status;

    // ── QR Check-in ──────────────────────────────────────
    @Column(name = "check_in_code", unique = true)
    private String checkInCode;

    // ── SMS Reminder ─────────────────────────────────────
    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "sms_reminder_sent")
    private Boolean smsReminderSent = false;

    @PrePersist
    private void generateCheckInCode() {
        if (this.checkInCode == null) {
            this.checkInCode = UUID.randomUUID().toString();
        }
        if (this.smsReminderSent == null) {
            this.smsReminderSent = false;
        }
    }
}
