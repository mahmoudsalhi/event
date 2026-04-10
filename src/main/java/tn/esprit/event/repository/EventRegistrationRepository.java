package tn.esprit.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.event.entity.EventRegistration;
import tn.esprit.event.entity.RegistrationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRegistrationRepository extends JpaRepository<EventRegistration, Long> {

    List<EventRegistration> findByEventId(Long eventId);

    List<EventRegistration> findByUserId(Long userId);

    /** Find the first waitlisted user for a given event, ordered by registration date (FIFO) */
    Optional<EventRegistration> findFirstByEventIdAndStatusOrderByRegistrationDateAsc(
            Long eventId, RegistrationStatus status);

    /** Find a registration by its unique check-in code */
    Optional<EventRegistration> findByCheckInCode(String checkInCode);

    /** Find registrations that need SMS reminders (event starts within 24h, not yet reminded) */
    @Query("SELECT r FROM EventRegistration r JOIN FETCH r.event e " +
           "WHERE r.status = :status " +
           "AND r.phoneNumber IS NOT NULL " +
           "AND (r.smsReminderSent IS NULL OR r.smsReminderSent = false) " +
           "AND e.startDate BETWEEN :now AND :cutoff")
    List<EventRegistration> findPendingReminders(
            @Param("status") RegistrationStatus status,
            @Param("now") LocalDateTime now,
            @Param("cutoff") LocalDateTime cutoff);

    /** Find registrations for events that ended, where rating email hasn't been sent yet */
    @Query("SELECT r FROM EventRegistration r JOIN FETCH r.event e " +
           "WHERE r.status = :status " +
           "AND r.userEmail IS NOT NULL " +
           "AND (r.ratingEmailSent IS NULL OR r.ratingEmailSent = false) " +
           "AND e.endDate IS NOT NULL AND e.endDate < :now")
    List<EventRegistration> findPendingRatingEmails(
            @Param("status") RegistrationStatus status,
            @Param("now") LocalDateTime now);
}
