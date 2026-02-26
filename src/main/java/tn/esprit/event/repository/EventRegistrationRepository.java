package tn.esprit.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.event.entity.EventRegistration;

import java.util.List;

@Repository
public interface EventRegistrationRepository extends JpaRepository<EventRegistration, Long> {

    List<EventRegistration> findByEventId(Long eventId);

    List<EventRegistration> findByUserId(Long userId);
}
