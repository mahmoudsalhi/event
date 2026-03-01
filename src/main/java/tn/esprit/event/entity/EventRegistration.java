package tn.esprit.event.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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
<<<<<<< HEAD:backend/microservices/event/src/main/java/tn/esprit/event/entity/EventRegistration.java
    @ManyToOne(fetch = FetchType.EAGER)
=======
    @ManyToOne(fetch = FetchType.LAZY)
>>>>>>> 78d0ae216a736e7724d059bd6bfe986d9ca5fefe:Backend/Microservices/event/src/main/java/tn/esprit/event/entity/EventRegistration.java
    @JoinColumn(name = "event_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private Event event;

<<<<<<< HEAD:backend/microservices/event/src/main/java/tn/esprit/event/entity/EventRegistration.java
    // Read-only mapping of the FK column so JSON includes eventId
    // without triggering lazy-load of the full Event
=======
    // Expose the FK as a readable field in JSON (read-only, managed by JPA)
>>>>>>> 78d0ae216a736e7724d059bd6bfe986d9ca5fefe:Backend/Microservices/event/src/main/java/tn/esprit/event/entity/EventRegistration.java
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
}
