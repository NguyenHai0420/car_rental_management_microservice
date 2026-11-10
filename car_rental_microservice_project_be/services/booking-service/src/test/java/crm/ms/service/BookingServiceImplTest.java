package crm.ms.service;

import crm.ms.client.ExternalValidationService;
import crm.ms.client.VehicleClient;
import crm.ms.dto.*;
import crm.ms.entity.Booking;
import crm.ms.enums.BookingStatus;
import crm.ms.event.EmailNotificationCommand;
import crm.ms.exception.BusinessException;
import crm.ms.mapper.BookingMapper;
import crm.ms.messaging.NotificationRabbitPublisher;
import crm.ms.repository.BookingRepository;
import crm.ms.repository.RentalSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private RentalSlotRepository rentalSlotRepository;
    @Mock
    private ExternalValidationService externalValidationService;
    @Mock
    private VehicleClient vehicleClient;
    @Mock
    private NotificationRabbitPublisher notificationRabbitPublisher;

    private BookingServiceImpl bookingService;

    @BeforeEach
    void setUp() {
        BookingMapper mapper = Mappers.getMapper(BookingMapper.class);
        bookingService = new BookingServiceImpl(
                bookingRepository,
                rentalSlotRepository,
                externalValidationService,
                vehicleClient,
                notificationRabbitPublisher,
                mapper
        );
        ReflectionTestUtils.setField(bookingService, "depositRate", new BigDecimal("0.30"));
        ReflectionTestUtils.setField(bookingService, "mockExternalServices", false);
    }

    @Test
    void createBooking_success_createsSlotsAndHistory() {
        UUID vehicleId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        LocalDateTime pickup = LocalDateTime.now().plusDays(1).withHour(8).withMinute(0);
        LocalDateTime dropoff = pickup.plusDays(2);

        when(rentalSlotRepository.existsByVehicleIdAndSlotDateBetween(any(), any(), any())).thenReturn(false);
        when(externalValidationService.validateCustomer(eq("kc-sub-1"), any(), any()))
                .thenReturn(CustomerEligibilityDto.builder()
                        .customerId(customerId)
                        .fullName("Nguyen Van A")
                        .email("customer1@carrental.vn")
                        .eligible(true)
                        .licenseExpiryDate(LocalDate.now().plusYears(2))
                        .build());
        when(externalValidationService.validateVehicle(vehicleId))
                .thenReturn(VehicleAvailabilityDto.builder()
                        .vehicleId(vehicleId)
                        .brand("Toyota")
                        .modelName("Camry")
                        .licensePlate("51A-888.99")
                        .status("AVAILABLE")
                        .available(true)
                        .dailyRate(new BigDecimal("1000000.00"))
                        .build());

        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        BookingRequest request = BookingRequest.builder()
                .vehicleId(vehicleId)
                .pickupDatetime(pickup)
                .returnDatetime(dropoff)
                .pickupLocation("Chi nhanh Quan 1")
                .build();

        BookingResponse res = bookingService.createBooking(request, "kc-sub-1", "Nguyen Van A", "customer1@carrental.vn");

        assertThat(res.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(res.getEstimatedTotal()).isEqualByComparingTo("2000000.00");
        assertThat(res.getDepositRequired()).isEqualByComparingTo("600000.00");

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).saveAndFlush(bookingCaptor.capture());
        assertThat(bookingCaptor.getValue().getRentalSlots()).hasSize(3);
    }

    @Test
    void createBooking_overlappingDates_throws409Conflict() {
        UUID vehicleId = UUID.randomUUID();
        LocalDateTime pickup = LocalDateTime.now().plusDays(1);
        LocalDateTime dropoff = pickup.plusDays(1);

        when(rentalSlotRepository.existsByVehicleIdAndSlotDateBetween(eq(vehicleId), any(), any())).thenReturn(true);

        BookingRequest request = BookingRequest.builder()
                .vehicleId(vehicleId)
                .pickupDatetime(pickup)
                .returnDatetime(dropoff)
                .pickupLocation("Chi nhanh Quan 1")
                .build();

        assertThatThrownBy(() -> bookingService.createBooking(request, "kc-sub-1", "A", "a@test.com"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(409);
    }

    @Test
    void cancelBooking_clearsRentalSlotsAndChangesStatusToCancelled() {
        UUID bookingId = UUID.randomUUID();
        Booking existing = Booking.builder()
                .id(bookingId)
                .bookingCode("BK-20261109-TEST")
                .customerId(UUID.randomUUID())
                .keycloakUserId("kc-sub-1")
                .vehicleId(UUID.randomUUID())
                .status(BookingStatus.PENDING)
                .pickupDatetime(LocalDateTime.now().plusDays(2))
                .returnDatetime(LocalDateTime.now().plusDays(4))
                .pickupLocation("Quan 1")
                .dailyRate(new BigDecimal("1000000"))
                .estimatedTotal(new BigDecimal("2000000"))
                .build();
        existing.getRentalSlots().add(crm.ms.entity.RentalSlot.builder().booking(existing).vehicleId(existing.getVehicleId()).slotDate(LocalDate.now().plusDays(2)).build());

        when(bookingRepository.findByIdAndIsDeletedFalse(bookingId)).thenReturn(Optional.of(existing));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse cancelled = bookingService.cancelBooking(
                bookingId, new CancelBookingRequest("Doi ke hoach"), "kc-sub-1", false
        );

        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(existing.getRentalSlots()).isEmpty();
    }

    @Test
    void confirmBooking_confirmsAndPublishesRabbitNotification() {
        UUID bookingId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        Booking existing = Booking.builder()
                .id(bookingId)
                .bookingCode("BK-20261109-CONF")
                .customerId(UUID.randomUUID())
                .customerName("Nguyen Van A")
                .customerEmail("customer1@carrental.vn")
                .vehicleId(vehicleId)
                .vehicleInfo("Toyota Camry - 51A-888.99")
                .status(BookingStatus.PENDING)
                .pickupDatetime(LocalDateTime.now().plusDays(1))
                .returnDatetime(LocalDateTime.now().plusDays(3))
                .pickupLocation("Chi nhanh Quan 1")
                .dailyRate(new BigDecimal("1000000"))
                .estimatedTotal(new BigDecimal("2000000"))
                .build();

        when(bookingRepository.findByIdAndIsDeletedFalse(bookingId)).thenReturn(Optional.of(existing));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse res = bookingService.confirmBooking(bookingId, "PAY-001", "payment-service");

        assertThat(res.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(notificationRabbitPublisher).sendEmailCommand(any(EmailNotificationCommand.class));
    }

    @Test
    void pickupAndReturnVehicle_enforcesMileageValidation() {
        UUID bookingId = UUID.randomUUID();
        Booking existing = Booking.builder()
                .id(bookingId)
                .bookingCode("BK-20261109-RIDE")
                .customerId(UUID.randomUUID())
                .vehicleId(UUID.randomUUID())
                .status(BookingStatus.CONFIRMED)
                .pickupDatetime(LocalDateTime.now())
                .returnDatetime(LocalDateTime.now().plusDays(1))
                .pickupLocation("Quan 1")
                .dailyRate(new BigDecimal("1000000"))
                .estimatedTotal(new BigDecimal("1000000"))
                .build();

        when(bookingRepository.findByIdAndIsDeletedFalse(bookingId)).thenReturn(Optional.of(existing));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse pickedUp = bookingService.pickupVehicle(
                bookingId, PickupBookingRequest.builder().pickupMileage(new BigDecimal("15000.0")).build(), "owner1"
        );
        assertThat(pickedUp.getStatus()).isEqualTo(BookingStatus.PICKED_UP);

        assertThatThrownBy(() -> bookingService.returnVehicle(
                bookingId, ReturnBookingRequest.builder().returnMileage(new BigDecimal("14999.0")).build(), "owner1"
        )).isInstanceOf(BusinessException.class);

        BookingResponse returned = bookingService.returnVehicle(
                bookingId, ReturnBookingRequest.builder().returnMileage(new BigDecimal("15250.5")).build(), "owner1"
        );
        assertThat(returned.getStatus()).isEqualTo(BookingStatus.RETURNED);
        assertThat(returned.getReturnMileage()).isEqualByComparingTo("15250.5");
    }
}
