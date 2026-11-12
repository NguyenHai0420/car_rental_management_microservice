package crm.ms;

import crm.ms.client.VehicleClient;
import crm.ms.controller.MaintenanceController;
import crm.ms.dto.MaintenanceConflictResponse;
import crm.ms.dto.MaintenanceResponse;
import crm.ms.entity.MaintenanceSchedule;
import crm.ms.enums.MaintenanceStatus;
import crm.ms.enums.Priority;
import crm.ms.exception.BusinessException;
import crm.ms.exception.GlobalExceptionHandler;
import crm.ms.mapper.MaintenanceMapper;
import crm.ms.repository.InspectionChecklistRepository;
import crm.ms.repository.MaintenanceRepository;
import crm.ms.repository.MaintenanceStandardRepository;
import crm.ms.repository.ServiceRecordRepository;
import crm.ms.service.MaintenanceService;
import crm.ms.service.MaintenanceServiceImpl;
import crm.ms.service.MaintenanceVehicleLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MaintenanceControllerAndConflictTest {

    @Mock
    private MaintenanceRepository maintenanceRepository;
    @Mock
    private MaintenanceStandardRepository standardRepository;
    @Mock
    private ServiceRecordRepository serviceRecordRepository;
    @Mock
    private InspectionChecklistRepository checklistRepository;
    @Mock
    private MaintenanceVehicleLockService vehicleLockService;
    @Mock
    private VehicleClient vehicleClient;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private MaintenanceService mockedMaintenanceService;

    private MaintenanceServiceImpl maintenanceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MaintenanceMapper mapper = Mappers.getMapper(MaintenanceMapper.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-12T02:00:00Z"), ZoneOffset.UTC);
        maintenanceService = new MaintenanceServiceImpl(
                maintenanceRepository,
                standardRepository,
                serviceRecordRepository,
                checklistRepository,
                vehicleLockService,
                vehicleClient,
                mapper,
                transactionManager,
                clock,
                30L
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new MaintenanceController(mockedMaintenanceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void checkInternalConflicts_detectsOverlapAndRejectsInvalidInterval() {
        UUID vehicleId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        Instant start = Instant.parse("2026-11-15T08:00:00Z");
        Instant end = Instant.parse("2026-11-15T17:00:00Z");

        MaintenanceSchedule existing = MaintenanceSchedule.builder()
                .id(scheduleId)
                .vehicleId(vehicleId)
                .status(MaintenanceStatus.SCHEDULED)
                .priority(Priority.HIGH)
                .scheduledStartAt(start)
                .scheduledEndAt(end)
                .build();

        when(maintenanceRepository.findConflictingSchedules(eq(vehicleId), eq(start), eq(end), any(), isNull()))
                .thenReturn(List.of(existing));

        MaintenanceConflictResponse conflict = maintenanceService.checkInternalConflicts(vehicleId, start, end, null);
        assertTrue(conflict.isHasConflict());
        assertEquals(1, conflict.getConflictingSchedules().size());
        assertEquals(scheduleId, conflict.getConflictingSchedules().get(0).getId());

        assertThrows(BusinessException.class,
                () -> maintenanceService.checkInternalConflicts(vehicleId, end, start, null));
    }

    @Test
    void controller_listSchedulesAndConflicts_returnExpectedHttp200() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        MaintenanceResponse schedule = MaintenanceResponse.builder()
                .id(scheduleId)
                .vehicleId(vehicleId)
                .status(MaintenanceStatus.SCHEDULED)
                .priority(Priority.MEDIUM)
                .build();

        when(mockedMaintenanceService.getSchedules(eq(vehicleId), eq(MaintenanceStatus.SCHEDULED), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(schedule), PageRequest.of(0, 10), 1));
        when(mockedMaintenanceService.checkInternalConflicts(
                eq(vehicleId),
                eq(Instant.parse("2026-11-15T08:00:00Z")),
                eq(Instant.parse("2026-11-15T17:00:00Z")),
                isNull()))
                .thenReturn(MaintenanceConflictResponse.builder()
                        .vehicleId(vehicleId)
                        .hasConflict(false)
                        .conflictingSchedules(List.of())
                        .build());

        mockMvc.perform(get("/api/v1/maintenances")
                        .param("vehicleId", vehicleId.toString())
                        .param("status", "SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(scheduleId.toString()))
                .andExpect(jsonPath("$.data.content[0].status").value("SCHEDULED"));

        mockMvc.perform(get("/api/v1/maintenances/vehicle/{vehicleId}/conflicts", vehicleId)
                        .param("startAt", "2026-11-15T08:00:00Z")
                        .param("endAt", "2026-11-15T17:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasConflict").value(false));
    }
}

