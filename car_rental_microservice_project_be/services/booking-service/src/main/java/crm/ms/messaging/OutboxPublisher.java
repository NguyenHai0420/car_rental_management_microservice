package crm.ms.messaging;

import crm.ms.entity.Outbox;
import crm.ms.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    public static final String BOOKING_EVENTS_TOPIC = "booking-events";

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void publishPendingEvents() {
        List<Outbox> pendingEvents = outboxRepository.findTop50ByIsPublishedFalseOrderByCreatedAtAsc();
        for (Outbox event : pendingEvents) {
            try {
                kafkaTemplate.send(
                        BOOKING_EVENTS_TOPIC,
                        event.getAggregateId().toString(),
                        event.getPayload()
                ).get(5, TimeUnit.SECONDS);

                event.setIsPublished(true);
                event.setPublishedAt(LocalDateTime.now());
                log.info("Published Outbox event [type={}, aggregateId={}] to topic {}",
                        event.getEventType(), event.getAggregateId(), BOOKING_EVENTS_TOPIC);
            } catch (Exception e) {
                log.warn("Deferred Outbox publish for event id={} (Kafka unavailable: {})",
                        event.getId(), e.getMessage());
                break;
            }
        }
    }
}

