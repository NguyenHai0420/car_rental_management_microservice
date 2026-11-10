package crm.ms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import crm.ms.client.ExternalValidationService;
import crm.ms.dto.*;
import crm.ms.entity.Booking;
import crm.ms.entity.BookingStatusHistory;
import crm.ms.entity.Outbox;
import crm.ms.entity.RentalSlot;
import crm.ms.enums.BookingStatus;
import crm.ms.enums.SlotStatus;
import crm.ms.enums.VehicleCacheStatus;
import crm.ms.event.BookingEventPayload;
import crm.ms.event.EmailNotificationCommand;
import crm.ms.event.PaymentEventPayload;
import crm.ms.exception.BusinessException;
import crm.ms.mapper.BookingMapper;
import crm.ms.messaging.NotificationRabbitPublisher;
import crm.ms.repository.BookingRepository;
import crm.ms.repository.OutboxRepository;
import crm.ms.repository.RentalSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private static final DateTimeFormatter CODE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter EMAIL_DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final BookingRepository bookingRepository;
    private final RentalSlotRepository rentalSlotRepository;
    private final OutboxRepository outboxRepository;
    private final ExternalValidationService externalValidationService;
    private final FleetInventoryService fleetInventoryService;
    private final NotificationRabbitPublisher notificationRabbitPublisher;
    private final BookingMapper bookingMapper;
    private final ObjectMapper objectMapper;

    @Value("${booking.deposit-rate:0.30}")
    private BigDecimal depositRate;

    @Value("${booking.expiration-minutes:15}")
    private int expirationMinutes;

    @Override
    @Transactional
    public BookingResponse createBooking(BookingRequest request, String keycloakUserId, String jwtName, String jwtEmail) {
        validateBookingDates(request.getPickupDatetime(), request.getReturnDatetime());

        LocalDate startDate = request.getPickupDatetime().toLocalDate();
        LocalDate endDate = request.getReturnDatetime().toLocalDate();

        if (rentalSlotRepository.existsByVehicleIdAndSlotDateBetween(request.getVehicleId(), startDate, endDate)) {
            throw new BusinessException(1, 409, "Xe đã có lịch đặt trong khoảng thời gian đã chọn");
        }

        CustomerEligibilityDto customer = externalValidationService.validateCustomer(keycloakUserId, jwtName, jwtEmail);
        if (!customer.isEligible()) {
            String reason = customer.getReason() != null ? customer.getReason()
                    : "Khách hàng chưa đủ điều kiện thuê xe (tài khoản chưa kích hoạt hoặc GPLX chưa xác thực)";
            throw new BusinessException(1, 400, reason);
        }
        if (customer.getLicenseExpiryDate() != null && customer.getLicenseExpiryDate().isBefore(endDate)) {
            throw new BusinessException(1, 400, "Giấy phép lái xe hết hạn trước ngày kết thúc thuê xe");
        }

        VehicleAvailabilityDto vehicle = externalValidationService.validateVehicle(request.getVehicleId());
        if (!vehicle.isAvailable()) {
            throw new BusinessException(1, 409, "Xe hiện không sẵn sàng cho thuê (trạng thái: " + vehicle.getStatus() + ")");
        }

        long hours = ChronoUnit.HOURS.between(request.getPickupDatetime(), request.getReturnDatetime());
        long rentalDays = Math.max(1L, (hours + 23) / 24);
        BigDecimal dailyRate = vehicle.getDailyRate() != null ? vehicle.getDailyRate() : BigDecimal.ZERO;
        BigDecimal estimatedTotal = dailyRate.multiply(BigDecimal.valueOf(rentalDays)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal depositRequired = estimatedTotal.multiply(depositRate).setScale(2, RoundingMode.HALF_UP);

        String vehicleInfo = String.format("%s %s - %s",
                nullSafe(vehicle.getBrand()), nullSafe(vehicle.getModelName()), nullSafe(vehicle.getLicensePlate())).trim();

        Booking booking = Booking.builder()
                .bookingCode(generateBookingCode())
                .customerId(customer.getCustomerId())
                .keycloakUserId(keycloakUserId)
                .customerName(customer.getFullName() != null ? customer.getFullName() : jwtName)
                .customerEmail(customer.getEmail() != null ? customer.getEmail() : jwtEmail)
                .vehicleId(request.getVehicleId())
                .vehicleInfo(vehicleInfo)
                .status(BookingStatus.PENDING)
                .pickupDatetime(request.getPickupDatetime())
                .returnDatetime(request.getReturnDatetime())
                .pickupLocation(request.getPickupLocation())
                .returnLocation(request.getReturnLocation() != null && !request.getReturnLocation().isBlank()
                        ? request.getReturnLocation() : request.getPickupLocation())
                .dailyRate(dailyRate)
                .estimatedTotal(estimatedTotal)
                .depositRequired(depositRequired)
                .currency("VND")
                .notes(request.getNotes())
                .createdBy(keycloakUserId)
                .build();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            booking.getRentalSlots().add(RentalSlot.builder()
                    .booking(booking)
                    .vehicleId(request.getVehicleId())
                    .slotDate(date)
                    .slotStatus(SlotStatus.RESERVED)
                    .build());
        }

        recordStatusHistory(booking, null, BookingStatus.PENDING, "Khởi tạo đơn đặt xe, chờ thanh toán cọc", keycloakUserId);

        Booking saved = bookingRepository.saveAndFlush(booking);
        enqueueOutboxEvent(saved, "BookingCreated", null);

        log.info("Created booking {} (id={}) for vehicleId={}", saved.getBookingCode(), saved.getId(), saved.getVehicleId());
        return bookingMapper.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(UUID bookingId, String keycloakUserId, boolean isStaffOrAdmin) {
        Booking booking = findActiveBooking(bookingId);
        if (!isStaffOrAdmin && !keycloakUserId.equals(booking.getKeycloakUserId())) {
            throw new BusinessException(1, 403, "Bạn không có quyền xem đơn đặt xe của người khác");
        }
        return bookingMapper.fromEntity(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(String keycloakUserId) {
        return bookingRepository.findByKeycloakUserIdAndIsDeletedFalseOrderByCreatedAtDesc(keycloakUserId)
                .stream()
                .map(bookingMapper::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> getAllBookings(BookingStatus status, Pageable pageable) {
        Page<Booking> page = (status != null)
                ? bookingRepository.findByStatusAndIsDeletedFalseOrderByCreatedAtDesc(status, pageable)
                : bookingRepository.findByIsDeletedFalseOrderByCreatedAtDesc(pageable);
        return page.map(bookingMapper::fromEntity);
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(UUID bookingId, CancelBookingRequest request, String keycloakUserId, boolean isStaffOrAdmin) {
        Booking booking = findActiveBooking(bookingId);
        if (!isStaffOrAdmin && !keycloakUserId.equals(booking.getKeycloakUserId())) {
            throw new BusinessException(1, 403, "Bạn không có quyền hủy đơn đặt xe của người khác");
        }
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException(1, 400, "Chỉ có thể hủy đơn ở trạng thái PENDING hoặc CONFIRMED (hiện tại: " + booking.getStatus() + ")");
        }

        String reason = (request != null && request.getReason() != null && !request.getReason().isBlank())
                ? request.getReason().trim() : "Khách hàng / Quản trị viên yêu cầu hủy đơn";

        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(reason);
        booking.setUpdatedBy(keycloakUserId);
        booking.getRentalSlots().clear();

        recordStatusHistory(booking, oldStatus, BookingStatus.CANCELLED, reason, keycloakUserId);
        Booking saved = bookingRepository.saveAndFlush(booking);

        if (oldStatus == BookingStatus.CONFIRMED) {
            fleetInventoryService.updateCachedStatus(saved.getVehicleId(), VehicleCacheStatus.AVAILABLE);
        }
        enqueueOutboxEvent(saved, "BookingCancelled", reason);
        return bookingMapper.fromEntity(saved);
    }

    @Override
    @Transactional
    public BookingResponse pickupVehicle(UUID bookingId, PickupBookingRequest request, String actorUsername) {
        Booking booking = findActiveBooking(bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException(1, 400, "Chỉ có thể giao xe khi đơn ở trạng thái CONFIRMED (hiện tại: " + booking.getStatus() + ")");
        }

        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.PICKED_UP);
        booking.setPickupMileage(request.getPickupMileage());
        booking.setUpdatedBy(actorUsername);
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            booking.setNotes(appendNote(booking.getNotes(), "[Giao xe] " + request.getNotes()));
        }
        booking.getRentalSlots().forEach(slot -> slot.setSlotStatus(SlotStatus.ACTIVE));

        recordStatusHistory(booking, oldStatus, BookingStatus.PICKED_UP,
                "Đã bàn giao xe tại km: " + request.getPickupMileage(), actorUsername);
        Booking saved = bookingRepository.saveAndFlush(booking);

        fleetInventoryService.updateCachedStatus(saved.getVehicleId(), VehicleCacheStatus.RENTED);
        enqueueOutboxEvent(saved, "BookingPickedUp", null);
        return bookingMapper.fromEntity(saved);
    }

    @Override
    @Transactional
    public BookingResponse returnVehicle(UUID bookingId, ReturnBookingRequest request, String actorUsername) {
        Booking booking = findActiveBooking(bookingId);
        if (booking.getStatus() != BookingStatus.PICKED_UP) {
            throw new BusinessException(1, 400, "Chỉ có thể nhận trả xe khi đơn ở trạng thái PICKED_UP (hiện tại: " + booking.getStatus() + ")");
        }
        if (booking.getPickupMileage() != null && request.getReturnMileage().compareTo(booking.getPickupMileage()) < 0) {
            throw new BusinessException(1, 400, "Số km trả xe (" + request.getReturnMileage()
                    + ") không thể nhỏ hơn số km lúc nhận xe (" + booking.getPickupMileage() + ")");
        }

        LocalDateTime actualReturn = request.getActualReturnDatetime() != null
                ? request.getActualReturnDatetime() : LocalDateTime.now();

        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.RETURNED);
        booking.setReturnMileage(request.getReturnMileage());
        booking.setActualReturnDatetime(actualReturn);
        booking.setUpdatedBy(actorUsername);
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            booking.setNotes(appendNote(booking.getNotes(), "[Trả xe] " + request.getNotes()));
        }
        booking.getRentalSlots().forEach(slot -> slot.setSlotStatus(SlotStatus.COMPLETED));

        recordStatusHistory(booking, oldStatus, BookingStatus.RETURNED,
                "Khách trả xe tại km: " + request.getReturnMileage(), actorUsername);
        Booking saved = bookingRepository.saveAndFlush(booking);

        fleetInventoryService.updateCachedStatus(saved.getVehicleId(), VehicleCacheStatus.AVAILABLE);
        enqueueOutboxEvent(saved, "BookingReturned", null);
        return bookingMapper.fromEntity(saved);
    }

    @Override
    @Transactional
    public void handlePaymentEvent(PaymentEventPayload event) {
        if (event == null || event.getBookingId() == null || event.getEventType() == null) {
            return;
        }
        Booking booking = bookingRepository.findByIdAndIsDeletedFalse(event.getBookingId()).orElse(null);
        if (booking == null) {
            log.warn("Ignored payment event {} for unknown bookingId={}", event.getEventType(), event.getBookingId());
            return;
        }

        switch (event.getEventType()) {
            case "PaymentCompleted" -> {
                if (booking.getStatus() == BookingStatus.PENDING) {
                    booking.setStatus(BookingStatus.CONFIRMED);
                    booking.setUpdatedBy("payment-service");
                    recordStatusHistory(booking, BookingStatus.PENDING, BookingStatus.CONFIRMED,
                            "Đã thanh toán thành công mã GD: " + nullSafe(event.getPaymentCode()), "payment-service");
                    Booking saved = bookingRepository.saveAndFlush(booking);

                    fleetInventoryService.updateCachedStatus(saved.getVehicleId(), VehicleCacheStatus.RESERVED);
                    enqueueOutboxEvent(saved, "BookingConfirmed", null);
                    sendBookingConfirmedEmail(saved);
                } else if (booking.getStatus() == BookingStatus.RETURNED && "FULLY_PAID".equalsIgnoreCase(event.getPaymentStatus())) {
                    booking.setStatus(BookingStatus.COMPLETED);
                    booking.setUpdatedBy("payment-service");
                    recordStatusHistory(booking, BookingStatus.RETURNED, BookingStatus.COMPLETED,
                            "Đã tất toán toàn bộ chi phí thuê xe", "payment-service");
                    Booking saved = bookingRepository.saveAndFlush(booking);
                    enqueueOutboxEvent(saved, "BookingCompleted", null);
                }
            }
            case "PaymentFailed" -> {
                if (booking.getStatus() == BookingStatus.PENDING) {
                    String reason = event.getReason() != null ? event.getReason() : "Thanh toán thất bại hoặc hết hạn";
                    booking.setStatus(BookingStatus.CANCELLED);
                    booking.setCancellationReason(reason);
                    booking.setUpdatedBy("payment-service");
                    booking.getRentalSlots().clear();
                    recordStatusHistory(booking, BookingStatus.PENDING, BookingStatus.CANCELLED, reason, "payment-service");
                    Booking saved = bookingRepository.saveAndFlush(booking);
                    enqueueOutboxEvent(saved, "BookingCancelled", reason);
                }
            }
            default -> log.debug("Ignored unhandled payment eventType={}", event.getEventType());
        }
    }

    @Override
    @Transactional
    public int expireOverduePendingBookings() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(expirationMinutes);
        List<Booking> expiredList = bookingRepository.findByStatusAndCreatedAtBeforeAndIsDeletedFalse(
                BookingStatus.PENDING, cutoff
        );
        for (Booking booking : expiredList) {
            String reason = "Tự động hủy đơn do quá hạn thanh toán " + expirationMinutes + " phút";
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancellationReason(reason);
            booking.setUpdatedBy("system-scheduler");
            booking.getRentalSlots().clear();
            recordStatusHistory(booking, BookingStatus.PENDING, BookingStatus.CANCELLED, reason, "system-scheduler");
            Booking saved = bookingRepository.saveAndFlush(booking);
            enqueueOutboxEvent(saved, "BookingCancelled", reason);
            log.info("Auto-expired overdue PENDING booking {}", saved.getBookingCode());
        }
        return expiredList.size();
    }

    private void validateBookingDates(LocalDateTime pickup, LocalDateTime returnDt) {
        if (!returnDt.isAfter(pickup)) {
            throw new BusinessException(1, 400, "Thời gian trả xe phải lớn hơn thời gian nhận xe");
        }
        if (pickup.isBefore(LocalDateTime.now().minusMinutes(5))) {
            throw new BusinessException(1, 400, "Thời gian nhận xe không được nằm trong quá khứ");
        }
    }

    private Booking findActiveBooking(UUID bookingId) {
        return bookingRepository.findByIdAndIsDeletedFalse(bookingId)
                .orElseThrow(() -> new BusinessException(1, 404, "Không tìm thấy đơn đặt xe với ID: " + bookingId));
    }

    private void recordStatusHistory(Booking booking, BookingStatus oldStatus, BookingStatus newStatus, String reason, String changedBy) {
        booking.getStatusHistories().add(BookingStatusHistory.builder()
                .booking(booking)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .reason(reason)
                .changedBy(changedBy)
                .build());
    }

    private void enqueueOutboxEvent(Booking booking, String eventType, String reason) {
        BookingEventPayload payload = BookingEventPayload.builder()
                .eventType(eventType)
                .bookingId(booking.getId())
                .bookingCode(booking.getBookingCode())
                .customerId(booking.getCustomerId())
                .customerName(booking.getCustomerName())
                .customerEmail(booking.getCustomerEmail())
                .vehicleId(booking.getVehicleId())
                .vehicleInfo(booking.getVehicleInfo())
                .status(booking.getStatus().name())
                .pickupDatetime(booking.getPickupDatetime())
                .returnDatetime(booking.getReturnDatetime())
                .actualReturnDatetime(booking.getActualReturnDatetime())
                .pickupLocation(booking.getPickupLocation())
                .returnLocation(booking.getReturnLocation())
                .pickupMileage(booking.getPickupMileage())
                .returnMileage(booking.getReturnMileage())
                .dailyRate(booking.getDailyRate())
                .estimatedTotal(booking.getEstimatedTotal())
                .depositRequired(booking.getDepositRequired())
                .currency(booking.getCurrency())
                .reason(reason)
                .occurredAt(LocalDateTime.now())
                .build();
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxRepository.save(Outbox.builder()
                    .aggregateType("Booking")
                    .aggregateId(booking.getId())
                    .eventType(eventType)
                    .payload(json)
                    .isPublished(false)
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Outbox event payload", e);
        }
    }

    private void sendBookingConfirmedEmail(Booking booking) {
        if (booking.getCustomerEmail() == null || booking.getCustomerEmail().isBlank()) {
            return;
        }
        String formattedTotal = NumberFormat.getNumberInstance(Locale.US).format(booking.getEstimatedTotal());
        EmailNotificationCommand cmd = EmailNotificationCommand.builder()
                .templateCode("BOOKING_CONFIRMED")
                .channel("EMAIL")
                .recipient(booking.getCustomerEmail())
                .recipientName(booking.getCustomerName() != null ? booking.getCustomerName() : "Quý khách")
                .placeholders(Map.of(
                        "customer_name", booking.getCustomerName() != null ? booking.getCustomerName() : "Quý khách",
                        "booking_code", booking.getBookingCode(),
                        "vehicle_info", nullSafe(booking.getVehicleInfo()),
                        "pickup_datetime", booking.getPickupDatetime().format(EMAIL_DT_FMT),
                        "pickup_location", booking.getPickupLocation(),
                        "return_datetime", booking.getReturnDatetime().format(EMAIL_DT_FMT),
                        "total_amount", formattedTotal
                ))
                .sourceService("booking-service")
                .sourceEvent("BookingConfirmed")
                .referenceId(booking.getId())
                .build();
        notificationRabbitPublisher.sendEmailCommand(cmd);
    }

    private String generateBookingCode() {
        String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "BK-" + LocalDate.now().format(CODE_DATE_FMT) + "-" + suffix;
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    private String appendNote(String existing, String addition) {
        return (existing == null || existing.isBlank()) ? addition : existing + "\n" + addition;
    }
}
