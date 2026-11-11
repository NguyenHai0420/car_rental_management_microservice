package crm.ms.messaging;

import crm.ms.config.RabbitMQConfig;
import crm.ms.event.EmailNotificationCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRabbitPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void sendEmailCommand(EmailNotificationCommand command) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.EMAIL_ROUTING_KEY,
                    command
            );
            log.info("Published email command [template={}, recipient={}] to RabbitMQ",
                    command.getTemplateCode(), command.getRecipient());
        } catch (Exception e) {
            log.error("Failed to publish email command to RabbitMQ for ref={}: {}",
                    command.getReferenceId(), e.getMessage());
        }
    }
}

