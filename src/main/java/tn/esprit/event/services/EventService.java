package tn.esprit.event.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.event.entity.Event;
import tn.esprit.event.repository.EventRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    public Event create(Event event) {
        return eventRepository.save(event);
    }

    public Event update(Long id, Event event) {
        Event existing = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found with id: " + id));

        existing.setTitle(event.getTitle());
        existing.setDescription(event.getDescription());
        existing.setImage(event.getImage());
        existing.setCategory(event.getCategory());
        existing.setTags(event.getTags());

        existing.setStartDate(event.getStartDate());
        existing.setEndDate(event.getEndDate());

        existing.setLocation(event.getLocation());
        existing.setLatitude(event.getLatitude());
        existing.setLongitude(event.getLongitude());

        existing.setMaxAttendees(event.getMaxAttendees());
        existing.setCurrentAttendees(event.getCurrentAttendees());
        existing.setIsRegistrationOpen(event.getIsRegistrationOpen());
        existing.setRegistrationDeadline(event.getRegistrationDeadline());

        existing.setStatus(event.getStatus());
        existing.setIsFeatured(event.getIsFeatured());
        existing.setIsPublic(event.getIsPublic());

        existing.setHostName(event.getHostName());
        existing.setHostId(event.getHostId());
        existing.setContactEmail(event.getContactEmail());

        existing.setTargetLevel(event.getTargetLevel());
        existing.setSkillFocus(event.getSkillFocus());

        return eventRepository.save(existing);
    }

    public void delete(Long id) {
        if (!eventRepository.existsById(id)) {
            throw new RuntimeException("Event not found with id: " + id);
        }
        eventRepository.deleteById(id);
    }

    public Event getById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found with id: " + id));
    }

    public List<Event> getAll() {
        return eventRepository.findAll();
    }
}
