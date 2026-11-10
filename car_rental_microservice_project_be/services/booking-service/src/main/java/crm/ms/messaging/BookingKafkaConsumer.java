package crm.ms.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import crm.ms.event.PaymentEventPayload;
import crm.ms.event.VehicleEventPayload;
import crm.ms.service.BookingService;
import crm.ms.service.FleetInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingKafkaConsumer {

    private final FleetInventoryService fleetInventoryService;
    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "vehicle-events", groupId = "${spring.kafka.consumer.group-id:booking-service-group}")
    public void onVehicleEvent(String rawJson) {
        try {
            VehicleEventPayload payload = objectMapper.readValue(rawJson, VehicleEventPayload.class);
            fleetInventoryService.syncFromVehicleEvent(payload);
        } catch (Exception e) {
            log.error("Failed to process vehicle-events message: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "payment-events", groupId = "${spring.kafka.consumer.group-id:booking-service-group}")
    public void onPaymentEvent(String rawJson) {
        try {
            PaymentEventPayload payload = objectMapper.readValue(rawJson, PaymentEventPayload.class);
            bookingService.handlePaymentEvent(payload);
        } catch (Exception e) {
            log.error("Failed to process payment-events message: {}", e.getMessage(), e);
        }
    }
}

