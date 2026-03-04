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

    /** Find registrations needing SMS reminders: event starts within window, has phone, not yet reminded */
    @Query("SELECT r FROM EventRegistration r JOIN FETCH r.event e " +
           "WHERE e.startDate BETWEEN :now AND :cutoff " +
           "AND r.phoneNumber IS NOT NULL " +
           "AND r.smsReminderSent = false " +
           "AND r.status <> 'CANCELLED'")
    List<EventRegistration> findRegistrationsNeedingReminder(
            @Param("now") LocalDateTime now,
            @Param("cutoff") LocalDateTime cutoff);
}
