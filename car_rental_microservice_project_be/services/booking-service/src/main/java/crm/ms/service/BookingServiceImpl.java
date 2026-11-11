package crm.ms.service;

import crm.ms.client.ExternalValidationService;
import crm.ms.client.VehicleClient;
import crm.ms.dto.*;
import crm.ms.entity.Booking;
import crm.ms.entity.BookingStatusHistory;
import crm.ms.entity.RentalSlot;
import crm.ms.enums.BookingStatus;
import crm.ms.enums.SlotStatus;
import crm.ms.event.EmailNotificationCommand;
import crm.ms.exception.BusinessException;
import crm.ms.mapper.BookingMapper;
import crm.ms.messaging.NotificationRabbitPublisher;
import crm.ms.repository.BookingRepository;
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
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private static final DateTimeFormatter CODE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter EMAIL_DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final BookingRepository bookingRepository;
    private final RentalSlotRepository rentalSlotRepository;
    private final ExternalValidationService externalValidationService;
    private final VehicleClient vehicleClient;
    private final NotificationRabbitPublisher notificationRabbitPublisher;
    private final BookingMapper bookingMapper;

    @Value("${booking.deposit-rate:0.30}")
    private BigDecimal depositRate;

    @Value("${booking.mock-external-services:false}")
    private boolean mockExternalServices;

    @Override
    @Transactional(readOnly = true)
    public List<AvailableVehicleResponse> searchAvailableVehicles(
            LocalDateTime pickupDatetime,
            LocalDateTime returnDatetime,
            UUID categoryId,
            Integer minSeats,
            String location
    ) {
        LocalDate startDate = pickupDatetime != null ? pickupDatetime.toLocalDate() : LocalDate.now().plusDays(1);
        LocalDate endDate = returnDatetime != null ? returnDatetime.toLocalDate() : startDate.plusDays(2);

        List<UUID> busyVehicleIds = rentalSlotRepository.findBusyVehicleIds(startDate, endDate);
        Set<UUID> busySet = new HashSet<>(busyVehicleIds != null ? busyVehicleIds : Collections.emptyList());

        List<AvailableVehicleResponse> vehicles = fetchVehiclesFromServiceOrMock();

        return vehicles.stream()
                .filter(v -> !busySet.contains(v.getVehicleId()))
                .filter(v -> categoryId == null || categoryId.equals(v.getCategoryId()))
                .filter(v -> minSeats == null || (v.getSeatCount() != null && v.getSeatCount() >= minSeats))
                .filter(v -> location == null || location.isBlank() ||
                        (v.getLocation() != null && v.getLocation().toLowerCase().contains(location.trim().toLowerCase())))
                .toList();
    }

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

        log.info("Created booking {} (id={}) for vehicleId={}", saved.getBookingCode(), saved.getId(), saved.getVehicleId());
        return bookingMapper.fromEntity(saved);
    }

    @Override
    @Transactional
    public BookingResponse confirmBooking(UUID bookingId, String paymentCode, String actor) {
        Booking booking = findActiveBooking(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessException(1, 400, "Chỉ có thể xác nhận đơn ở trạng thái PENDING (hiện tại: " + booking.getStatus() + ")");
        }

        String reason = "Đã xác nhận thanh toán tiền cọc thành công" + (paymentCode != null ? " (Mã GD: " + paymentCode + ")" : "");
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setUpdatedBy(actor != null ? actor : "system");

        recordStatusHistory(booking, BookingStatus.PENDING, BookingStatus.CONFIRMED, reason, actor != null ? actor : "system");
        Booking saved = bookingRepository.saveAndFlush(booking);

        sendBookingConfirmedEmail(saved);
        log.info("Confirmed booking {} via payment confirmation", saved.getBookingCode());
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

        log.info("Cancelled booking {} (reason: {})", saved.getBookingCode(), reason);
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

        try {
            vehicleClient.updateStatus(saved.getVehicleId(), "RENTED", request.getPickupMileage());
        } catch (Exception e) {
            log.warn("Failed to notify vehicle-service about RENTED status for vehicleId={}: {}", saved.getVehicleId(), e.getMessage());
        }

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

        try {
            vehicleClient.updateStatus(saved.getVehicleId(), "AVAILABLE", request.getReturnMileage());
        } catch (Exception e) {
            log.warn("Failed to notify vehicle-service about AVAILABLE status for vehicleId={}: {}", saved.getVehicleId(), e.getMessage());
        }

        return bookingMapper.fromEntity(saved);
    }

    private List<AvailableVehicleResponse> fetchVehiclesFromServiceOrMock() {
        try {
            FeignResponseWrapper<List<AvailableVehicleResponse>> response = vehicleClient.getAvailableVehicles();
            if (response != null && response.getData() != null && !response.getData().isEmpty()) {
                return response.getData();
            }
        } catch (Exception e) {
            log.warn("vehicle-service getAvailableVehicles failed: {} (mockExternalServices={})", e.getMessage(), mockExternalServices);
        }

        if (mockExternalServices) {
            return List.of(
                    AvailableVehicleResponse.builder()
                            .vehicleId(UUID.fromString("6d2f1de6-861b-4774-ab0c-fd96d9b5a59b"))
                            .vin("VN1234567890CAMRY")
                            .licensePlate("51A-888.99")
                            .brand("Toyota")
                            .modelName("Camry 2.5Q")
                            .categoryName("Sedan")
                            .sizeClass("MIDSIZE")
                            .color("Đen")
                            .seatCount(5)
                            .fuelType("Xăng")
                            .vehicleStatus("AVAILABLE")
                            .dailyRate(BigDecimal.valueOf(1200000))
                            .location("Chi nhánh Quận 1, TP.HCM")
                            .primaryImageUrl("https://images.unsplash.com/photo-1621007947382-bb3c3994e3fb?w=600")
                            .build(),
                    AvailableVehicleResponse.builder()
                            .vehicleId(UUID.fromString("7e3f2de7-972c-5885-bc1d-fe07e0c6b60c"))
                            .vin("VN1234567890EVEREST")
                            .licensePlate("51K-666.88")
                            .brand("Ford")
                            .modelName("Everest Titanium 4x4")
                            .categoryName("SUV")
                            .sizeClass("FULLSIZE")
                            .color("Trắng ngọc trai")
                            .seatCount(7)
                            .fuelType("Dầu Diesel")
                            .vehicleStatus("AVAILABLE")
                            .dailyRate(BigDecimal.valueOf(1600000))
                            .location("Chi nhánh Quận 7, TP.HCM")
                            .primaryImageUrl("https://images.unsplash.com/photo-1533473359331-0135ef1b58bf?w=600")
                            .build(),
                    AvailableVehicleResponse.builder()
                            .vehicleId(UUID.fromString("8f4f3de8-083d-6996-cd2e-0f18f1d7c71d"))
                            .vin("VN1234567890TUCSON")
                            .licensePlate("51G-555.33")
                            .brand("Hyundai")
                            .modelName("Tucson 2.0 Turbo")
                            .categoryName("Crossover")
                            .sizeClass("COMPACT")
                            .color("Đỏ đô")
                            .seatCount(5)
                            .fuelType("Xăng")
                            .vehicleStatus("AVAILABLE")
                            .dailyRate(BigDecimal.valueOf(1100000))
                            .location("Chi nhánh Sân bay Tân Sơn Nhất")
                            .primaryImageUrl("https://images.unsplash.com/photo-1549399542-7e3f8b79c341?w=600")
                            .build()
            );
        }

        return Collections.emptyList();
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
        try {
            notificationRabbitPublisher.sendEmailCommand(cmd);
        } catch (Exception e) {
            log.warn("Failed to publish booking confirmed email command to RabbitMQ: {}", e.getMessage());
        }
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
