package crm.ms.mapper;

import crm.ms.dto.AvailableVehicleResponse;
import crm.ms.dto.BookingResponse;
import crm.ms.entity.Booking;
import crm.ms.entity.BookingStatusHistory;
import crm.ms.entity.FleetInventoryCache;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        builder = @Builder(disableBuilder = true)
)
public interface BookingMapper {

    BookingResponse fromEntity(Booking booking);

    BookingResponse.StatusHistoryItem toHistoryItem(BookingStatusHistory history);

    AvailableVehicleResponse toAvailableVehicleResponse(FleetInventoryCache cache);
}
