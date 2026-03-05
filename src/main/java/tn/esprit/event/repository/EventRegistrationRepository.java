package tn.esprit.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.event.entity.EventRegistration;
import tn.esprit.event.entity.RegistrationStatus;

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
}
