package crm.ms.mapper;

import crm.ms.dto.DeliveryStatusResponse;
import crm.ms.dto.NotificationResponse;
import crm.ms.entity.DeliveryStatus;
import crm.ms.entity.NotificationLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "deliveryEvents", source = "deliveryStatuses")
    @Mapping(target = "idempotentReplay", ignore = true)
    NotificationResponse toResponse(NotificationLog notificationLog);

    DeliveryStatusResponse toDeliveryStatusResponse(DeliveryStatus deliveryStatus);
}
