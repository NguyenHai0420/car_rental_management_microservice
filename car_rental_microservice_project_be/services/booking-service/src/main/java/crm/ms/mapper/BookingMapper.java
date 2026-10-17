package crm.ms.mapper;

import crm.ms.dto.BookingRequest;
import crm.ms.dto.BookingResponse;
import crm.ms.entity.Booking;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BookingMapper {
    Booking toEntity(BookingRequest bookingRequest);
    BookingResponse fromEntity(Booking booking);
}
