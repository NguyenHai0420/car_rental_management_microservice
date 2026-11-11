package crm.ms.service;

import crm.ms.client.BookingClient;
import crm.ms.client.VehicleClient;
import crm.ms.client.dto.ExternalBookingConflictResponse;
import crm.ms.client.dto.ExternalVehicleResponse;
import crm.ms.dto.CancelMaintenanceRequest;
import crm.ms.dto.CompleteMaintenanceRequest;
import crm.ms.dto.InspectionChecklistRequest;
import crm.ms.dto.InspectionChecklistResponse;
import crm.ms.dto.MaintenanceConflictResponse;
import crm.ms.dto.MaintenanceRequest;
import crm.ms.dto.MaintenanceResponse;
import crm.ms.dto.ServiceRecordRequest;
import crm.ms.dto.ServiceRecordResponse;
import crm.ms.dto.UpdateInspectionChecklistRequest;
import crm.ms.dto.UpdateMaintenanceRequest;
import crm.ms.entity.InspectionChecklist;
import crm.ms.entity.MaintenanceSchedule;
import crm.ms.entity.MaintenanceStandard;
import crm.ms.entity.ServiceRecord;
import crm.ms.enums.InspectionResult;
import crm.ms.enums.MaintenanceStatus;
import crm.ms.enums.Priority;
import crm.ms.exception.BusinessException;
import crm.ms.exception.ExternalServiceUnavailableException;
import crm.ms.mapper.MaintenanceMapper;
import crm.ms.repository.InspectionChecklistRepository;
import crm.ms.repository.MaintenanceRepository;
import crm.ms.repository.MaintenanceStandardRepository;
import crm.ms.repository.ServiceRecordRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MaintenanceServiceImpl implements MaintenanceService {

    private static final String VEHICLE_STATUS_DECOMMISSIONED = "DECOMMISSIONED";

    private static final Set<MaintenanceStatus> ACTIVE_CONFLICT_STATUSES = EnumSet.of(
            MaintenanceStatus.SCHEDULED,
            MaintenanceStatus.START_REQUESTED,
            MaintenanceStatus.IN_PROGRESS,
            MaintenanceStatus.COMPLETE_REQUESTED,
            MaintenanceStatus.OVERDUE
    );

    private static final Set<MaintenanceStatus> MUTABLE_STATUSES = EnumSet.of(
            MaintenanceStatus.SCHEDULED,
            MaintenanceStatus.OVERDUE
    );

    private final MaintenanceRepository maintenanceRepository;
    private final MaintenanceStandardRepository standardRepository;
    private final ServiceRecordRepository serviceRecordRepository;
    private final InspectionChecklistRepository checklistRepository;
    private final MaintenanceVehicleLockService vehicleLockService;
    private final VehicleClient vehicleClient;
    private final BookingClient bookingClient;
    private final MaintenanceMapper maintenanceMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final long retryDelaySeconds;

    public MaintenanceServiceImpl(
            MaintenanceRepository maintenanceRepository,
            MaintenanceStandardRepository standardRepository,
            ServiceRecordRepository serviceRecordRepository,
            InspectionChecklistRepository checklistRepository,
            MaintenanceVehicleLockService vehicleLockService,
            VehicleClient vehicleClient,
            BookingClient bookingClient,
            MaintenanceMapper maintenanceMapper,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${maintenance.sync.retry-delay-seconds:30}") long retryDelaySeconds) {
        this.maintenanceRepository = maintenanceRepository;
        this.standardRepository = standardRepository;
        this.serviceRecordRepository = serviceRecordRepository;
        this.checklistRepository = checklistRepository;
        this.vehicleLockService = vehicleLockService;
        this.vehicleClient = vehicleClient;
        this.bookingClient = bookingClient;
        this.maintenanceMapper = maintenanceMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.retryDelaySeconds = retryDelaySeconds;
    }

    @Override
    public MaintenanceResponse createSchedule(MaintenanceRequest request) {
        validateHalfOpenInterval(request.getScheduledStartAt(), request.getScheduledEndAt());
        UUID vehicleId = request.getVehicleId();

        // Bước 1: Kiểm tra service ngoài NGOÀI transaction DB (Fail-Closed tuyệt đối)
        ExternalVehicleResponse vehicle = vehicleClient.getVehicleById(vehicleId);
        if (VEHICLE_STATUS_DECOMMISSIONED.equalsIgnoreCase(vehicle.getStatus())) {
            throw BusinessException.conflict("Cannot schedule maintenance for a DECOMMISSIONED vehicle: " + vehicleId);
        }

        ExternalBookingConflictResponse bookingConflict = bookingClient.checkBookingConflict(
                vehicleId, request.getScheduledStartAt(), request.getScheduledEndAt());
        if (bookingConflict.isHasConflict()) {
            throw BusinessException.conflict(buildBookingConflictMessage(vehicleId, bookingConflict));
        }

        // Bước 2: Khởi tạo lock row & transaction DB ngắn hạn kiểm tra xung đột nội bộ
        vehicleLockService.ensureLockRowExists(vehicleId);

        return transactionTemplate.execute(status -> {
            vehicleLockService.acquireLock(vehicleId);

            List<MaintenanceSchedule> conflicts = maintenanceRepository.findConflictingSchedules(
                    vehicleId,
                    request.getScheduledStartAt(),
                    request.getScheduledEndAt(),
                    ACTIVE_CONFLICT_STATUSES,
                    null
            );

            if (!conflicts.isEmpty()) {
                MaintenanceSchedule firstConflict = conflicts.get(0);
                throw BusinessException.conflict(String.format(
                        "Vehicle %s already has a conflicting maintenance schedule (%s) in [%s, %s)",
                        vehicleId,
                        firstConflict.getId(),
                        firstConflict.getScheduledStartAt(),
                        firstConflict.getScheduledEndAt()
                ));
            }

            MaintenanceStandard standard = resolveStandard(request.getStandardId());

            MaintenanceSchedule schedule = MaintenanceSchedule.builder()
                    .vehicleId(vehicleId)
                    .standard(standard)
                    .status(MaintenanceStatus.SCHEDULED)
                    .scheduledStartAt(request.getScheduledStartAt())
                    .scheduledEndAt(request.getScheduledEndAt())
                    .scheduledAtKm(request.getScheduledAtKm())
                    .estimatedCost(request.getEstimatedCost())
                    .assignedTo(request.getAssignedTo())
                    .priority(request.getPriority() != null ? request.getPriority() : Priority.MEDIUM)
                    .notes(request.getNotes())
                    .createdBy(request.getCreatedBy())
                    .isAutoGenerated(false)
                    .build();

            MaintenanceSchedule saved = maintenanceRepository.saveAndFlush(schedule);
            log.info("Created maintenance schedule id={} for vehicleId={} in [{}, {})",
                    saved.getId(), vehicleId, saved.getScheduledStartAt(), saved.getScheduledEndAt());
            return maintenanceMapper.toResponse(saved);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceResponse getScheduleById(UUID id) {
        MaintenanceSchedule schedule = maintenanceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Maintenance schedule not found: " + id));
        return maintenanceMapper.toResponse(schedule);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MaintenanceResponse> getSchedules(
            UUID vehicleId,
            MaintenanceStatus status,
            Instant from,
            Instant to,
            Pageable pageable) {
        if (from != null && to != null && !to.isAfter(from)) {
            throw BusinessException.badRequest("Query parameter 'to' must be strictly after 'from'");
        }

        Specification<MaintenanceSchedule> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (vehicleId != null) {
                predicates.add(cb.equal(root.get("vehicleId"), vehicleId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null && to != null) {
                predicates.add(cb.lessThan(root.get("scheduledStartAt"), to));
                predicates.add(cb.greaterThan(root.get("scheduledEndAt"), from));
            } else if (from != null) {
                predicates.add(cb.greaterThan(root.get("scheduledEndAt"), from));
            } else if (to != null) {
                predicates.add(cb.lessThan(root.get("scheduledStartAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return maintenanceRepository.findAll(spec, pageable)
                .map(maintenanceMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceResponse> getSchedulesByVehicle(UUID vehicleId) {
        return maintenanceRepository.findByVehicleIdOrderByScheduledStartAtDesc(vehicleId)
                .stream()
                .map(maintenanceMapper::toResponse)
                .toList();
    }

    @Override
    public MaintenanceResponse updateSchedule(UUID id, UpdateMaintenanceRequest request) {
        validateHalfOpenInterval(request.getScheduledStartAt(), request.getScheduledEndAt());

        MaintenanceSchedule existing = maintenanceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Maintenance schedule not found: " + id));
        if (!MUTABLE_STATUSES.contains(existing.getStatus())) {
            throw BusinessException.conflict(String.format(
                    "Cannot update maintenance schedule %s in status %s", id, existing.getStatus()));
        }
        UUID vehicleId = existing.getVehicleId();

        // Kiểm tra booking trùng lịch NGOÀI transaction DB (Fail-Closed)
        ExternalBookingConflictResponse bookingConflict = bookingClient.checkBookingConflict(
                vehicleId, request.getScheduledStartAt(), request.getScheduledEndAt());
        if (bookingConflict.isHasConflict()) {
            throw BusinessException.conflict(buildBookingConflictMessage(vehicleId, bookingConflict));
        }

        vehicleLockService.ensureLockRowExists(vehicleId);

        return transactionTemplate.execute(status -> {
            vehicleLockService.acquireLock(vehicleId);

            MaintenanceSchedule schedule = maintenanceRepository.findById(id)
                    .orElseThrow(() -> BusinessException.notFound("Maintenance schedule not found: " + id));

            if (!MUTABLE_STATUSES.contains(schedule.getStatus())) {
                throw BusinessException.conflict(String.format(
                        "Cannot update maintenance schedule %s in status %s",
                        id, schedule.getStatus()
                ));
            }

            List<MaintenanceSchedule> conflicts = maintenanceRepository.findConflictingSchedules(
                    vehicleId,
                    request.getScheduledStartAt(),
                    request.getScheduledEndAt(),
                    ACTIVE_CONFLICT_STATUSES,
                    id
            );

            if (!conflicts.isEmpty()) {
                MaintenanceSchedule firstConflict = conflicts.get(0);
                throw BusinessException.conflict(String.format(
                        "Vehicle %s already has a conflicting maintenance schedule (%s) in [%s, %s)",
                        vehicleId,
                        firstConflict.getId(),
                        firstConflict.getScheduledStartAt(),
                        firstConflict.getScheduledEndAt()
                ));
            }

            if (request.getStandardId() != null) {
                schedule.setStandard(resolveStandard(request.getStandardId()));
            }
            schedule.setScheduledStartAt(request.getScheduledStartAt());
            schedule.setScheduledEndAt(request.getScheduledEndAt());
            schedule.setScheduledAtKm(request.getScheduledAtKm());
            schedule.setEstimatedCost(request.getEstimatedCost());
            schedule.setAssignedTo(request.getAssignedTo());
            if (request.getPriority() != null) {
                schedule.setPriority(request.getPriority());
            }
            schedule.setNotes(request.getNotes());

            MaintenanceSchedule saved = maintenanceRepository.saveAndFlush(schedule);
            log.info("Updated maintenance schedule id={} for vehicleId={}", id, vehicleId);
            return maintenanceMapper.toResponse(saved);
        });
    }

    @Override
    public MaintenanceResponse startMaintenance(UUID id) {
        MaintenanceSchedule snapshot = maintenanceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Maintenance schedule not found: " + id));
        UUID vehicleId = snapshot.getVehicleId();

        vehicleLockService.ensureLockRowExists(vehicleId);

        // Bước 1: Commit ý định START_REQUESTED kèm idempotency key trong transaction ngắn
        MaintenanceSchedule requested = transactionTemplate.execute(txStatus -> {
            vehicleLockService.acquireLock(vehicleId);
            MaintenanceSchedule schedule = maintenanceRepository.findById(id)
                    .orElseThrow(() -> BusinessException.notFound("Maintenance schedule not found: " + id));

            if (!MUTABLE_STATUSES.contains(schedule.getStatus())) {
                throw BusinessException.conflict(String.format(
                        "Cannot start maintenance schedule %s in status %s", id, schedule.getStatus()));
            }

            schedule.setPreSyncStatus(schedule.getStatus());
            schedule.setStatus(MaintenanceStatus.START_REQUESTED);
            schedule.setResolvedVehicleStatus(null);
            schedule.setSyncRequestId(UUID.randomUUID());
            schedule.setSyncRetryCount(0);
            schedule.setSyncExhausted(false);
            schedule.setLastSyncError(null);
            schedule.setNextSyncAttemptAt(Instant.now(clock));
            return maintenanceRepository.saveAndFlush(schedule);
        });

        UUID syncRequestId = requested.getSyncRequestId();

        // Bước 2: Gọi Vehicle Service NGOÀI DB transaction (Domain command: enter maintenance)
        ExternalVehicleResponse vehicleResponse;
        try {
            vehicleResponse = vehicleClient.startVehicleMaintenance(
                    vehicleId,
                    id,
                    syncRequestId,
                    "Starting maintenance schedule " + id
            );
        } catch (ExternalServiceUnavailableException ex) {
            // Timeout/5xx có thể là timeout-after-commit: GIỮ NGUYÊN START_REQUESTED để Reconciliation xử lý theo operationId
            recordPendingSyncFailure(id, vehicleId, ex.getMessage());
            throw new ExternalServiceUnavailableException(
                    "Vehicle Service timed out or unavailable while starting maintenance " + id
                            + ". Schedule preserved in START_REQUESTED for automatic reconciliation.",
                    ex
            );
        } catch (BusinessException ex) {
            // Vehicle Service từ chối rõ ràng (400/403/404/409) -> Bù trừ hoàn trả trạng thái trước đó
            compensateFailedStart(id, vehicleId, ex.getMessage());
            throw ex;
        }

        // Bước 3: Xác nhận hoàn tất chuyển sang IN_PROGRESS trong transaction ngắn
        String resolvedStatus = vehicleResponse.getStatus();
        return transactionTemplate.execute(txStatus -> {
            vehicleLockService.acquireLock(vehicleId);
            MaintenanceSchedule schedule = maintenanceRepository.findById(id)
                    .orElseThrow(() -> BusinessException.notFound("Maintenance schedule not found: " + id));

            if (schedule.getStatus() == MaintenanceStatus.START_REQUESTED) {
                Instant now = Instant.now(clock);
                schedule.setStatus(MaintenanceStatus.IN_PROGRESS);
                schedule.setResolvedVehicleStatus(resolvedStatus);
                schedule.setStartedAt(now);
                schedule.setStartedDate(LocalDate.ofInstant(now, ZoneOffset.UTC));
                schedule.setNextSyncAttemptAt(null);
                schedule.setLastSyncError(null);
                schedule.setSyncExhausted(false);
                schedule = maintenanceRepository.saveAndFlush(schedule);
            }
            log.info("Started maintenance schedule id={} for vehicleId={} (vehicle status={})",
                    id, vehicleId, resolvedStatus);
            return maintenanceMapper.toResponse(schedule);
        });
    }

    @Override
    public MaintenanceResponse completeMaintenance(UUID id, CompleteMaintenanceRequest request) {
        MaintenanceSchedule snapshot = maintenanceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Maintenance schedule not found: " + id));
        if (snapshot.getStatus() != MaintenanceStatus.IN_PROGRESS) {
            throw BusinessException.conflict(String.format(
                    "Cannot complete maintenance schedule %s in status %s (expected IN_PROGRESS)",
                    id, snapshot.getStatus()));
        }

        // Điều kiện nghiệp vụ: Phải có ít nhất 1 checklist item và không được có mục FAIL hoặc NOT_CHECKED
        List<InspectionChecklist> checklistItems = checklistRepository.findByScheduleIdOrderByCreatedAtAsc(id);
        validateInspectionChecklistForCompletion(id, checklistItems);

        UUID vehicleId = snapshot.getVehicleId();
        BigDecimal resolvedActualCost = resolveActualCost(id, snapshot.getActualCost(), request);

        vehicleLockService.ensureLockRowExists(vehicleId);

        // Bước 1: Commit ý định COMPLETE_REQUESTED trong transaction ngắn
        MaintenanceSchedule requested = transactionTemplate.execute(txStatus -> {
            vehicleLockService.acquireLock(vehicleId);
            MaintenanceSchedule schedule = maintenanceRepository.findById(id)
                    .orElseThrow(() -> BusinessException.notFound("Maintenance schedule not found: " + id));

            if (schedule.getStatus() != MaintenanceStatus.IN_PROGRESS) {
                throw BusinessException.conflict(String.format(
                        "Cannot complete maintenance schedule %s in status %s", id, schedule.getStatus()));
            }

            if (resolvedActualCost != null) {
                schedule.setActualCost(resolvedActualCost);
            }
            if (request != null) {
                if (request.getCompletedAtKm() != null) {
                    schedule.setCompletedAtKm(request.getCompletedAtKm());
                }
                if (request.getNotes() != null) {
                    schedule.setNotes(request.getNotes());
                }
            }

            schedule.setPreSyncStatus(MaintenanceStatus.IN_PROGRESS);
            schedule.setStatus(MaintenanceStatus.COMPLETE_REQUESTED);
            schedule.setResolvedVehicleStatus(null);
            schedule.setSyncRequestId(UUID.randomUUID());
            schedule.setSyncRetryCount(0);
            schedule.setSyncExhausted(false);
            schedule.setLastSyncError(null);
            schedule.setNextSyncAttemptAt(Instant.now(clock));
            return maintenanceRepository.saveAndFlush(schedule);
        });

        UUID syncRequestId = requested.getSyncRequestId();

        // Bước 2: Gửi domain command báo kết thúc bảo trì sang Vehicle Service.
        // Vehicle Service toàn quyền quyết định trạng thái xe (AVAILABLE, RESERVED, ...) theo nghiệp vụ sở hữu của mình.
        ExternalVehicleResponse vehicleResponse;
        try {
            vehicleResponse = vehicleClient.completeVehicleMaintenance(
                    vehicleId,
                    id,
                    syncRequestId,
                    "Completing maintenance schedule " + id
            );
        } catch (ExternalServiceUnavailableException ex) {
            recordPendingSyncFailure(id, vehicleId, ex.getMessage());
            throw new ExternalServiceUnavailableException(
                    "Vehicle Service timed out or unavailable while completing maintenance " + id
                            + ". Schedule preserved in COMPLETE_REQUESTED for automatic reconciliation.",
                    ex
            );
        } catch (BusinessException ex) {
            compensateFailedComplete(id, vehicleId, ex.getMessage());
            throw ex;
        }

        // Bước 3: Chốt COMPLETED và ghi nhận trạng thái xe do Vehicle Service trả về
        String resolvedStatus = vehicleResponse.getStatus();
        return transactionTemplate.execute(txStatus -> {
            vehicleLockService.acquireLock(vehicleId);
            MaintenanceSchedule schedule = maintenanceRepository.findById(id)
                    .orElseThrow(() -> BusinessException.notFound("Maintenance schedule not found: " + id));

            if (schedule.getStatus() == MaintenanceStatus.COMPLETE_REQUESTED) {
                Instant completedTime = Instant.now(clock);
                schedule.setStatus(MaintenanceStatus.COMPLETED);
                schedule.setResolvedVehicleStatus(resolvedStatus);
                schedule.setCompletedAt(completedTime);
                schedule.setCompletedDate(LocalDate.ofInstant(completedTime, ZoneOffset.UTC));
                schedule.setNextSyncAttemptAt(null);
                schedule.setLastSyncError(null);
                schedule.setSyncExhausted(false);
                schedule = maintenanceRepository.saveAndFlush(schedule);
            }
            log.info("Completed maintenance schedule id={} for vehicleId={} (vehicle resolved status={})",
                    id, vehicleId, resolvedStatus);
            return maintenanceMapper.toResponse(schedule);
        });
    }

    @Override
    public MaintenanceResponse cancelSchedule(UUID id, CancelMaintenanceRequest request) {
        MaintenanceSchedule existing = maintenanceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Maintenance schedule not found: " + id));
        UUID vehicleId = existing.getVehicleId();

        vehicleLockService.ensureLockRowExists(vehicleId);

        return transactionTemplate.execute(status -> {
            vehicleLockService.acquireLock(vehicleId);

            MaintenanceSchedule schedule = maintenanceRepository.findById(id)
                    .orElseThrow(() -> BusinessException.notFound("Maintenance schedule not found: " + id));

            if (!MUTABLE_STATUSES.contains(schedule.getStatus())) {
                throw BusinessException.conflict(String.format(
                        "Cannot cancel maintenance schedule %s in status %s",
                        id, schedule.getStatus()
                ));
            }

            schedule.setStatus(MaintenanceStatus.CANCELLED);
            schedule.setCancellationReason(request.getCancellationReason());

            MaintenanceSchedule saved = maintenanceRepository.saveAndFlush(schedule);
            log.info("Cancelled maintenance schedule id={} for vehicleId={}", id, vehicleId);
            return maintenanceMapper.toResponse(saved);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceConflictResponse checkInternalConflicts(
            UUID vehicleId,
            Instant startAt,
            Instant endAt,
            UUID excludeScheduleId) {
        validateHalfOpenInterval(startAt, endAt);

        List<MaintenanceResponse> conflicts = maintenanceRepository.findConflictingSchedules(
                        vehicleId,
                        startAt,
                        endAt,
                        ACTIVE_CONFLICT_STATUSES,
                        excludeScheduleId
                )
                .stream()
                .map(maintenanceMapper::toResponse)
                .toList();

        return MaintenanceConflictResponse.builder()
                .vehicleId(vehicleId)
                .requestedStartAt(startAt)
                .requestedEndAt(endAt)
                .hasConflict(!conflicts.isEmpty())
                .conflictingSchedules(conflicts)
                .build();
    }

    @Override
    @Transactional
    public ServiceRecordResponse addServiceRecord(UUID scheduleId, ServiceRecordRequest request) {
        MaintenanceSchedule schedule = maintenanceRepository.findById(scheduleId)
                .orElseThrow(() -> BusinessException.notFound("Maintenance schedule not found: " + scheduleId));

        if (schedule.getStatus() == MaintenanceStatus.COMPLETED
                || schedule.getStatus() == MaintenanceStatus.CANCELLED) {
            throw BusinessException.conflict(
                    "Cannot add service record to maintenance schedule in status " + schedule.getStatus());
        }

        BigDecimal partsCost = request.getPartsCost() != null ? request.getPartsCost() : BigDecimal.ZERO;
        BigDecimal laborCost = request.getLaborCost() != null ? request.getLaborCost() : BigDecimal.ZERO;
        BigDecimal totalCost = partsCost.add(laborCost);

        ServiceRecord record = ServiceRecord.builder()
                .schedule(schedule)
                .vehicleId(schedule.getVehicleId())
                .serviceType(request.getServiceType())
                .description(request.getDescription())
                .serviceDate(request.getServiceDate())
                .mechanicName(request.getMechanicName())
                .workshopName(request.getWorkshopName())
                .workshopAddress(request.getWorkshopAddress())
                .partsCost(partsCost)
                .laborCost(laborCost)
                .totalCost(totalCost)
                .findings(request.getFindings())
                .partsReplaced(request.getPartsReplaced())
                .warrantyUntil(request.getWarrantyUntil())
                .build();

        return maintenanceMapper.toServiceRecordResponse(serviceRecordRepository.saveAndFlush(record));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceRecordResponse> getServiceRecords(UUID scheduleId) {
        if (!maintenanceRepository.existsById(scheduleId)) {
            throw BusinessException.notFound("Maintenance schedule not found: " + scheduleId);
        }
        return serviceRecordRepository.findByScheduleIdOrderByServiceDateDesc(scheduleId)
                .stream()
                .map(maintenanceMapper::toServiceRecordResponse)
                .toList();
    }

    @Override
    @Transactional
    public InspectionChecklistResponse addChecklistItem(UUID scheduleId, InspectionChecklistRequest request) {
        MaintenanceSchedule schedule = maintenanceRepository.findById(scheduleId)
                .orElseThrow(() -> BusinessException.notFound("Maintenance schedule not found: " + scheduleId));

        if (schedule.getStatus() == MaintenanceStatus.COMPLETED
                || schedule.getStatus() == MaintenanceStatus.CANCELLED) {
            throw BusinessException.conflict(
                    "Cannot add checklist item to maintenance schedule in status " + schedule.getStatus());
        }

        InspectionResult result = request.getResult() != null
                ? request.getResult()
                : InspectionResult.NOT_CHECKED;

        LocalDateTime inspectedAt = result != InspectionResult.NOT_CHECKED
                ? LocalDateTime.now(clock.withZone(ZoneOffset.UTC))
                : null;

        InspectionChecklist item = InspectionChecklist.builder()
                .schedule(schedule)
                .checkItem(request.getCheckItem())
                .checkCategory(request.getCheckCategory())
                .result(result)
                .measuredValue(request.getMeasuredValue())
                .acceptableRange(request.getAcceptableRange())
                .inspectorNotes(request.getInspectorNotes())
                .inspectorName(request.getInspectorName())
                .inspectedAt(inspectedAt)
                .build();

        return maintenanceMapper.toChecklistResponse(checklistRepository.saveAndFlush(item));
    }

    @Override
    @Transactional
    public InspectionChecklistResponse updateChecklistItem(
            UUID scheduleId,
            UUID checklistId,
            UpdateInspectionChecklistRequest request) {
        MaintenanceSchedule schedule = maintenanceRepository.findById(scheduleId)
                .orElseThrow(() -> BusinessException.notFound("Maintenance schedule not found: " + scheduleId));

        if (schedule.getStatus() == MaintenanceStatus.COMPLETED
                || schedule.getStatus() == MaintenanceStatus.CANCELLED) {
            throw BusinessException.conflict(
                    "Cannot update checklist item of maintenance schedule in status " + schedule.getStatus());
        }

        InspectionChecklist item = checklistRepository.findByIdAndScheduleId(checklistId, scheduleId)
                .orElseThrow(() -> BusinessException.notFound(
                        "Checklist item " + checklistId + " not found for schedule " + scheduleId));

        item.setResult(request.getResult());
        if (request.getMeasuredValue() != null) {
            item.setMeasuredValue(request.getMeasuredValue());
        }
        if (request.getInspectorNotes() != null) {
            item.setInspectorNotes(request.getInspectorNotes());
        }
        if (request.getInspectorName() != null) {
            item.setInspectorName(request.getInspectorName());
        }
        item.setInspectedAt(request.getResult() != InspectionResult.NOT_CHECKED
                ? LocalDateTime.now(clock.withZone(ZoneOffset.UTC))
                : null);

        return maintenanceMapper.toChecklistResponse(checklistRepository.saveAndFlush(item));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InspectionChecklistResponse> getChecklistItems(UUID scheduleId) {
        if (!maintenanceRepository.existsById(scheduleId)) {
            throw BusinessException.notFound("Maintenance schedule not found: " + scheduleId);
        }
        return checklistRepository.findByScheduleIdOrderByCreatedAtAsc(scheduleId)
                .stream()
                .map(maintenanceMapper::toChecklistResponse)
                .toList();
    }

    private void recordPendingSyncFailure(UUID scheduleId, UUID vehicleId, String errorMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            vehicleLockService.acquireLock(vehicleId);
            maintenanceRepository.findById(scheduleId).ifPresent(schedule -> {
                schedule.setSyncRetryCount(schedule.getSyncRetryCount() + 1);
                schedule.setNextSyncAttemptAt(Instant.now(clock).plusSeconds(retryDelaySeconds));
                schedule.setLastSyncError(truncateError(errorMessage));
                maintenanceRepository.saveAndFlush(schedule);
            });
        });
    }

    private void compensateFailedStart(UUID scheduleId, UUID vehicleId, String errorMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            vehicleLockService.acquireLock(vehicleId);
            maintenanceRepository.findById(scheduleId).ifPresent(schedule -> {
                if (schedule.getStatus() == MaintenanceStatus.START_REQUESTED) {
                    MaintenanceStatus restoreTo = schedule.getPreSyncStatus() != null
                            ? schedule.getPreSyncStatus()
                            : MaintenanceStatus.SCHEDULED;
                    schedule.setStatus(restoreTo);
                    schedule.setResolvedVehicleStatus(null);
                    schedule.setNextSyncAttemptAt(null);
                    schedule.setLastSyncError(truncateError(errorMessage));
                    maintenanceRepository.saveAndFlush(schedule);
                }
            });
        });
    }

    private void compensateFailedComplete(UUID scheduleId, UUID vehicleId, String errorMessage) {
        transactionTemplate.executeWithoutResult(status -> {
            vehicleLockService.acquireLock(vehicleId);
            maintenanceRepository.findById(scheduleId).ifPresent(schedule -> {
                if (schedule.getStatus() == MaintenanceStatus.COMPLETE_REQUESTED) {
                    schedule.setStatus(MaintenanceStatus.IN_PROGRESS);
                    schedule.setResolvedVehicleStatus(null);
                    schedule.setNextSyncAttemptAt(null);
                    schedule.setLastSyncError(truncateError(errorMessage));
                    maintenanceRepository.saveAndFlush(schedule);
                }
            });
        });
    }

    private void validateInspectionChecklistForCompletion(UUID scheduleId, List<InspectionChecklist> items) {
        if (items.isEmpty()) {
            throw BusinessException.badRequest(
                    "Cannot complete maintenance schedule " + scheduleId
                            + " without at least one inspection checklist item");
        }
        List<String> blockingItems = items.stream()
                .filter(i -> i.getResult() == InspectionResult.FAIL || i.getResult() == InspectionResult.NOT_CHECKED)
                .map(i -> i.getCheckItem() + " (" + i.getResult() + ")")
                .toList();
        if (!blockingItems.isEmpty()) {
            throw BusinessException.badRequest(
                    "Cannot complete maintenance schedule " + scheduleId
                            + ": unresolved or failed inspection checklist items: "
                            + String.join(", ", blockingItems));
        }
    }

    private BigDecimal resolveActualCost(
            UUID scheduleId,
            BigDecimal currentActualCost,
            CompleteMaintenanceRequest request) {
        if (request != null && request.getActualCost() != null) {
            return request.getActualCost();
        }
        if (currentActualCost != null) {
            return currentActualCost;
        }
        List<ServiceRecord> records = serviceRecordRepository.findByScheduleIdOrderByServiceDateDesc(scheduleId);
        if (records.isEmpty()) {
            return null;
        }
        return records.stream()
                .map(r -> r.getTotalCost() != null ? r.getTotalCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String buildBookingConflictMessage(UUID vehicleId, ExternalBookingConflictResponse response) {
        String codes = response.getConflictingBookings().stream()
                .map(ExternalBookingConflictResponse.ConflictingBookingSummary::getBookingCode)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.joining(", "));
        if (codes.isBlank()) {
            return "Vehicle " + vehicleId + " has conflicting rental bookings during the requested maintenance window";
        }
        return "Vehicle " + vehicleId + " has conflicting rental bookings (" + codes + ")";
    }

    private void validateHalfOpenInterval(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null) {
            throw BusinessException.badRequest("Both scheduledStartAt and scheduledEndAt (UTC) are required");
        }
        if (!endAt.isAfter(startAt)) {
            throw BusinessException.badRequest(
                    "Invalid time interval [start, end): scheduledEndAt must be strictly after scheduledStartAt");
        }
    }

    private MaintenanceStandard resolveStandard(UUID standardId) {
        if (standardId == null) {
            return null;
        }
        return standardRepository.findById(standardId)
                .orElseThrow(() -> BusinessException.notFound("Maintenance standard not found: " + standardId));
    }

    private String truncateError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
