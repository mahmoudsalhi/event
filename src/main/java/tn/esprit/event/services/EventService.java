package tn.esprit.event.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.event.entity.Event;
import tn.esprit.event.entity.EventStatus;
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

    public Event draft(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found with id: " + id));
        event.setStatus(EventStatus.DRAFT);
        return eventRepository.save(event);
    }

    public Event undraft(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found with id: " + id));
        event.setStatus(EventStatus.UPCOMING);
        return eventRepository.save(event);
    }

    public Event duplicate(Long id) {
        Event original = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found with id: " + id));
        Event copy = Event.builder()
                .title("Copy of " + original.getTitle())
                .description(original.getDescription())
                .image(original.getImage())
                .category(original.getCategory())
                .tags(original.getTags())
                .startDate(original.getStartDate())
                .endDate(original.getEndDate())
                .location(original.getLocation())
                .latitude(original.getLatitude())
                .longitude(original.getLongitude())
                .maxAttendees(original.getMaxAttendees())
                .currentAttendees(0)
                .isRegistrationOpen(original.getIsRegistrationOpen())
                .status(EventStatus.DRAFT)
                .isFeatured(false)
                .isPublic(original.getIsPublic())
                .hostName(original.getHostName())
                .hostId(original.getHostId())
                .contactEmail(original.getContactEmail())
                .targetLevel(original.getTargetLevel())
                .skillFocus(original.getSkillFocus())
                .build();
        return eventRepository.save(copy);
    }

    public void bulkDraft(java.util.List<Long> ids) {
        ids.forEach(id -> {
            eventRepository.findById(id).ifPresent(e -> {
                e.setStatus(EventStatus.DRAFT);
                eventRepository.save(e);
            });
        });
    }

    public void bulkCancel(java.util.List<Long> ids) {
        ids.forEach(id -> {
            eventRepository.findById(id).ifPresent(e -> {
                e.setStatus(EventStatus.CANCELLED);
                eventRepository.save(e);
            });
        });
    }

    public Event getById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found with id: " + id));
    }

    public List<Event> getAll() {
        return eventRepository.findAll();
    }
}
