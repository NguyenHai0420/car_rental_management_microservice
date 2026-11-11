package crm.ms.mapper;

import crm.ms.dto.*;
import crm.ms.entity.Payment;
import crm.ms.entity.PaymentItem;
import crm.ms.entity.PaymentTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Builder;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        builder = @Builder(disableBuilder = true)
)
public interface PaymentMapper {
    PaymentResponse toResponse(Payment payment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "payment", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    PaymentItem toItemEntity(PaymentItemRequestDto dto);

    PaymentItemResponseDto toItemResponse(PaymentItem item);
    List<PaymentItemResponseDto> toItemResponses(List<PaymentItem> items);

    PaymentTransactionResponseDto toTransactionResponse(PaymentTransaction transaction);
    List<PaymentTransactionResponseDto> toTransactionResponses(List<PaymentTransaction> transactions);
}
