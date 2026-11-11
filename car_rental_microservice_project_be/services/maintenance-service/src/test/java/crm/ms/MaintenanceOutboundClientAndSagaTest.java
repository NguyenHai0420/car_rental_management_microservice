package crm.ms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import crm.ms.client.BookingClient;
import crm.ms.client.RestBookingClient;
import crm.ms.client.RestVehicleClient;
import crm.ms.client.dto.ExternalVehicleResponse;
import crm.ms.config.CorrelationContextFilter;
import crm.ms.config.MaintenanceClientProperties;
import crm.ms.dto.CompleteMaintenanceRequest;
import crm.ms.entity.InspectionChecklist;
import crm.ms.entity.MaintenanceSchedule;
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
import crm.ms.service.MaintenanceServiceImpl;
import crm.ms.service.MaintenanceSyncScheduler;
import crm.ms.service.MaintenanceVehicleLockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Isolated stub-server tests (MOCK_TESTED) for RestVehicleClient, RestBookingClient,
 * isolated per-service credentials, HTTP error semantics, and Saga reconciliation.
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceOutboundClientAndSagaTest {

    private record CapturedRequest(
            String method,
            String path,
            String query,
            Headers headers,
            String body) {
    }

    private record StubReply(int status, String body, long delayMs) {
        StubReply(int status, String body) {
            this(status, body, 0L);
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private HttpServer server;
    private int port;
    private final List<CapturedRequest> capturedRequests = new CopyOnWriteArrayList<>();
    private final AtomicReference<Function<CapturedRequest, StubReply>> responder = new AtomicReference<>();

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
    private BookingClient bookingClient;

    @BeforeEach
    void startStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", this::handleExchange);
        server.start();
    }

    @AfterEach
    void stopStubServer() {
        MDC.clear();
        if (server != null) {
            server.stop(0);
        }
        capturedRequests.clear();
    }

    private void handleExchange(HttpExchange exchange) throws IOException {
        byte[] reqBytes = exchange.getRequestBody().readAllBytes();
        Headers copyHeaders = new Headers();
        exchange.getRequestHeaders().forEach(copyHeaders::put);
        CapturedRequest captured = new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getQuery(),
                copyHeaders,
                new String(reqBytes, StandardCharsets.UTF_8)
        );
        capturedRequests.add(captured);

        StubReply reply = responder.get().apply(captured);
        if (reply.delayMs() > 0) {
            try {
                Thread.sleep(reply.delayMs());
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] respBytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), respBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(respBytes);
        }
    }

    private RestClient buildRestClient(long readTimeoutMs) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl("http://127.0.0.1:" + port)
                .build();
    }

    @Test
    void clientPropertiesMaskSecretsAndSeparateVehicleFromBookingTokens() {
        MaintenanceClientProperties props = new MaintenanceClientProperties();
        props.getVehicleService().setInternalToken("super-secret-vehicle-token");
        props.getBookingService().setInternalToken("super-secret-booking-token");

        assertFalse(props.getVehicleService().toString().contains("super-secret-vehicle-token"));
        assertTrue(props.getVehicleService().toString().contains("[REDACTED]"));
        assertFalse(props.getBookingService().toString().contains("super-secret-booking-token"));
        assertEquals("maintenance-service", props.getServiceName());
    }

    @Test
    void vehicleClientSendsIsolatedInternalHeadersWithoutUserSpoofing_MOCK_TESTED() {
        UUID vehicleId = UUID.randomUUID();
        UUID maintId = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        MDC.put(CorrelationContextFilter.MDC_CORRELATION_ID, "corr-veh-123");

        responder.set(req -> new StubReply(200, """
                {"code":"OK","message":"Vehicle","data":{"id":"%s","vin":"VIN123","licensePlate":"30A-11111","status":"IN_MAINTENANCE"}}
                """.formatted(vehicleId)));

        RestVehicleClient client = new RestVehicleClient(
                buildRestClient(2000), objectMapper, "maintenance-service", "vehicle-only-token");

        ExternalVehicleResponse res = client.startVehicleMaintenance(vehicleId, maintId, opId, "Routine service");

        assertEquals("IN_MAINTENANCE", res.getStatus());
        assertEquals(1, capturedRequests.size());
        CapturedRequest req = capturedRequests.get(0);
        assertEquals("POST", req.method());
        assertEquals("/api/v1/vehicles/" + vehicleId + "/maintenance/start", req.path());
        assertEquals("vehicle-only-token", req.headers().getFirst("X-Internal-Service-Token"));
        assertEquals("maintenance-service", req.headers().getFirst("X-Service-Name"));
        assertEquals("corr-veh-123", req.headers().getFirst("X-Correlation-ID"));
        assertEquals(opId.toString(), req.headers().getFirst("X-Idempotency-Key"));
        assertNull(req.headers().getFirst("X-User-ID"));
        assertNull(req.headers().getFirst("X-User-Roles"));
    }

    @Test
    void vehicleClientClassifies400_401_403_404_409_5xx_AndTimeoutProperly_MOCK_TESTED() {
        UUID vehicleId = UUID.randomUUID();
        RestVehicleClient client = new RestVehicleClient(
                buildRestClient(250), objectMapper, "maintenance-service", "veh-token");

        // 400 -> 400
        responder.set(req -> new StubReply(400, "{\"message\":\"Bad vehicle payload\"}"));
        BusinessException ex400 = assertThrows(BusinessException.class, () -> client.getVehicleById(vehicleId));
        assertEquals(400, ex400.getCode());

        // 401 -> 502 (not 403 user error)
        responder.set(req -> new StubReply(401, "{\"message\":\"Invalid token\"}"));
        BusinessException ex401 = assertThrows(BusinessException.class, () -> client.getVehicleById(vehicleId));
        assertEquals(502, ex401.getCode());
        assertTrue(ex401.getMessage().contains("401"));

        // 403 -> 502 (distinct internal authorization message)
        responder.set(req -> new StubReply(403, "{\"message\":\"Service not allowed\"}"));
        BusinessException ex403 = assertThrows(BusinessException.class, () -> client.getVehicleById(vehicleId));
        assertEquals(502, ex403.getCode());
        assertTrue(ex403.getMessage().contains("403"));

        // 404 entity -> 404
        responder.set(req -> new StubReply(404, "{\"message\":\"Vehicle not found\"}"));
        BusinessException ex404 = assertThrows(BusinessException.class, () -> client.getVehicleById(vehicleId));
        assertEquals(404, ex404.getCode());

        // 404 missing integration endpoint -> 502
        responder.set(req -> new StubReply(404, "{\"message\":\"No static resource api/v1/vehicles/1/maintenance/start\"}"));
        BusinessException ex404Route = assertThrows(BusinessException.class,
                () -> client.startVehicleMaintenance(vehicleId, UUID.randomUUID(), UUID.randomUUID(), "test"));
        assertEquals(502, ex404Route.getCode());

        // 409 -> 409
        responder.set(req -> new StubReply(409, "{\"message\":\"Vehicle is RENTED\"}"));
        BusinessException ex409 = assertThrows(BusinessException.class,
                () -> client.startVehicleMaintenance(vehicleId, UUID.randomUUID(), UUID.randomUUID(), "test"));
        assertEquals(409, ex409.getCode());

        // 5xx -> 503 ExternalServiceUnavailableException
        responder.set(req -> new StubReply(500, "{\"message\":\"DB down\"}"));
        assertThrows(ExternalServiceUnavailableException.class,
                () -> client.startVehicleMaintenance(vehicleId, UUID.randomUUID(), UUID.randomUUID(), "test"));

        // Timeout -> 503 ExternalServiceUnavailableException
        responder.set(req -> new StubReply(200, "{\"status\":\"AVAILABLE\"}", 700));
        assertThrows(ExternalServiceUnavailableException.class, () -> client.getVehicleById(vehicleId));
    }

    @Test
    void bookingClientFailsClosedAndDistinguishesDownstreamStatuses_MOCK_TESTED() {
        UUID vehicleId = UUID.randomUUID();
        RestBookingClient client = new RestBookingClient(
                buildRestClient(1000), objectMapper, "maintenance-service", "booking-only-token");

        Instant start = Instant.parse("2026-11-15T08:00:00Z");
        Instant end = Instant.parse("2026-11-15T17:00:00Z");

        responder.set(req -> new StubReply(401, "{\"message\":\"Missing JWT\"}"));
        BusinessException ex401 = assertThrows(BusinessException.class,
                () -> client.checkBookingConflict(vehicleId, start, end));
        assertEquals(502, ex401.getCode());
        assertEquals("booking-only-token", capturedRequests.get(0).headers().getFirst("X-Internal-Service-Token"));
        assertNull(capturedRequests.get(0).headers().getFirst("X-User-ID"));

        responder.set(req -> new StubReply(404, "{\"message\":\"No static resource conflicts\"}"));
        BusinessException ex404Route = assertThrows(BusinessException.class,
                () -> client.checkBookingConflict(vehicleId, start, end));
        assertEquals(502, ex404Route.getCode());

        responder.set(req -> new StubReply(400, "{\"message\":\"Invalid interval\"}"));
        BusinessException ex400 = assertThrows(BusinessException.class,
                () -> client.checkBookingConflict(vehicleId, start, end));
        assertEquals(400, ex400.getCode());

        responder.set(req -> new StubReply(503, "{\"message\":\"Unavailable\"}"));
        assertThrows(ExternalServiceUnavailableException.class,
                () -> client.checkBookingConflict(vehicleId, start, end));
    }

    @Test
    void operationStatusDistinguishesVehicle404FromOperationNotFoundAndProtectsSaga_MOCK_TESTED() {
        UUID vehicleId = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        RestVehicleClient client = new RestVehicleClient(
                buildRestClient(1000), objectMapper, "maintenance-service", "veh-token");

        // 200 NOT_FOUND
        responder.set(req -> new StubReply(200, """
                {"code":"OK","data":{"operationId":"%s","vehicleId":"%s","outcome":"NOT_FOUND"}}
                """.formatted(opId, vehicleId)));
        assertEquals("NOT_FOUND", client.getOperationStatus(vehicleId, opId).getOutcome());

        // 200 APPLIED without resultingVehicleStatus must NOT be accepted as valid
        responder.set(req -> new StubReply(200, """
                {"code":"OK","data":{"operationId":"%s","vehicleId":"%s","outcome":"APPLIED","resultingVehicleStatus":null}}
                """.formatted(opId, vehicleId)));
        assertThrows(ExternalServiceUnavailableException.class, () -> client.getOperationStatus(vehicleId, opId));

        // 404 ambiguous operation not found -> ExternalServiceUnavailableException (MUST NOT rollback blindly)
        responder.set(req -> new StubReply(404, "{\"message\":\"Operation receipt not found\"}"));
        assertThrows(ExternalServiceUnavailableException.class, () -> client.getOperationStatus(vehicleId, opId));

        // 401/403 during reconciliation -> ExternalServiceUnavailableException (preserve START_REQUESTED)
        responder.set(req -> new StubReply(401, "{\"message\":\"Expired internal token\"}"));
        assertThrows(ExternalServiceUnavailableException.class, () -> client.getOperationStatus(vehicleId, opId));

        // Confirmed missing vehicle -> BusinessException 404
        responder.set(req -> new StubReply(404, "{\"message\":\"Vehicle not found: " + vehicleId + "\"}"));
        BusinessException missingVeh = assertThrows(BusinessException.class, () -> client.getOperationStatus(vehicleId, opId));
        assertEquals(404, missingVeh.getCode());
    }

    private PlatformTransactionManager noopTxManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }

    @Test
    void startTimeoutPreservesStartRequestedAndReconcilesApplied_MOCK_TESTED() {
        RestVehicleClient vehicleClient = new RestVehicleClient(
                buildRestClient(2000), objectMapper, "maintenance-service", "veh-secret");
        Clock fixedClock = Clock.systemUTC();
        MaintenanceMapper mapper = Mappers.getMapper(MaintenanceMapper.class);

        MaintenanceServiceImpl maintenanceService = new MaintenanceServiceImpl(
                maintenanceRepository, standardRepository, serviceRecordRepository, checklistRepository,
                vehicleLockService, vehicleClient, bookingClient, mapper, noopTxManager(), fixedClock, 30L);
        MaintenanceSyncScheduler syncScheduler = new MaintenanceSyncScheduler(
                maintenanceRepository, vehicleLockService, vehicleClient, noopTxManager(), fixedClock, 5, 30L);

        UUID scheduleId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        MaintenanceSchedule schedule = MaintenanceSchedule.builder()
                .id(scheduleId)
                .vehicleId(vehicleId)
                .status(MaintenanceStatus.SCHEDULED)
                .priority(Priority.HIGH)
                .scheduledStartAt(Instant.parse("2026-11-20T08:00:00Z"))
                .scheduledEndAt(Instant.parse("2026-11-20T12:00:00Z"))
                .build();

        when(maintenanceRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        when(maintenanceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        responder.set(req -> {
            if ("POST".equals(req.method())) {
                return new StubReply(503, "{\"message\":\"Vehicle service timeout\"}");
            }
            return new StubReply(200, """
                    {"code":"OK","data":{"operationId":"%s","vehicleId":"%s","outcome":"APPLIED","resultingVehicleStatus":"IN_MAINTENANCE"}}
                    """.formatted(schedule.getSyncRequestId(), vehicleId));
        });

        assertThrows(ExternalServiceUnavailableException.class, () -> maintenanceService.startMaintenance(scheduleId));
        assertEquals(MaintenanceStatus.START_REQUESTED, schedule.getStatus());
        assertNotNull(schedule.getSyncRequestId());
        assertEquals(1, schedule.getSyncRetryCount());

        when(maintenanceRepository.findPendingSyncSchedules(any(), any())).thenReturn(List.of(schedule));
        syncScheduler.reconcilePendingSchedules();

        assertEquals(MaintenanceStatus.IN_PROGRESS, schedule.getStatus());
        assertEquals("IN_MAINTENANCE", schedule.getResolvedVehicleStatus());
        assertNull(schedule.getNextSyncAttemptAt());
    }

    @Test
    void completeTimeoutReplaysOnNotFoundAndFinalizesWithVehicleDecision_MOCK_TESTED() {
        RestVehicleClient vehicleClient = new RestVehicleClient(
                buildRestClient(2000), objectMapper, "maintenance-service", "veh-secret");
        Clock fixedClock = Clock.systemUTC();
        MaintenanceMapper mapper = Mappers.getMapper(MaintenanceMapper.class);

        MaintenanceServiceImpl maintenanceService = new MaintenanceServiceImpl(
                maintenanceRepository, standardRepository, serviceRecordRepository, checklistRepository,
                vehicleLockService, vehicleClient, bookingClient, mapper, noopTxManager(), fixedClock, 30L);
        MaintenanceSyncScheduler syncScheduler = new MaintenanceSyncScheduler(
                maintenanceRepository, vehicleLockService, vehicleClient, noopTxManager(), fixedClock, 5, 30L);

        UUID scheduleId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        MaintenanceSchedule schedule = MaintenanceSchedule.builder()
                .id(scheduleId)
                .vehicleId(vehicleId)
                .status(MaintenanceStatus.IN_PROGRESS)
                .priority(Priority.MEDIUM)
                .scheduledStartAt(Instant.parse("2026-11-20T08:00:00Z"))
                .scheduledEndAt(Instant.parse("2026-11-20T12:00:00Z"))
                .build();

        when(maintenanceRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        when(maintenanceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(checklistRepository.findByScheduleIdOrderByCreatedAtAsc(scheduleId)).thenReturn(List.of(
                InspectionChecklist.builder().id(UUID.randomUUID()).checkItem("Brakes").result(InspectionResult.PASS).build()
        ));

        responder.set(req -> {
            if ("POST".equals(req.method())) {
                return new StubReply(500, "{\"message\":\"Transient 500\"}");
            }
            return new StubReply(404, "{\"message\":\"Operation receipt missing\"}");
        });

        assertThrows(ExternalServiceUnavailableException.class,
                () -> maintenanceService.completeMaintenance(scheduleId, new CompleteMaintenanceRequest()));
        assertEquals(MaintenanceStatus.COMPLETE_REQUESTED, schedule.getStatus());
        UUID originalOpId = schedule.getSyncRequestId();

        // Ambiguous 404 on /operations/{id} must NOT roll back COMPLETE_REQUESTED
        when(maintenanceRepository.findPendingSyncSchedules(any(), any())).thenReturn(List.of(schedule));
        syncScheduler.reconcilePendingSchedules();
        assertEquals(MaintenanceStatus.COMPLETE_REQUESTED, schedule.getStatus());
        assertEquals(2, schedule.getSyncRetryCount());

        // Next tick receives 200 NOT_FOUND -> replays POST complete with SAME idempotency key -> Vehicle decides AVAILABLE
        responder.set(req -> {
            if ("GET".equals(req.method())) {
                return new StubReply(200, """
                        {"code":"OK","data":{"operationId":"%s","vehicleId":"%s","outcome":"NOT_FOUND"}}
                        """.formatted(originalOpId, vehicleId));
            }
            return new StubReply(200, """
                    {"code":"OK","data":{"id":"%s","status":"AVAILABLE"}}
                    """.formatted(vehicleId));
        });

        syncScheduler.reconcilePendingSchedules();
        assertEquals(MaintenanceStatus.COMPLETED, schedule.getStatus());
        assertEquals("AVAILABLE", schedule.getResolvedVehicleStatus());
        CapturedRequest replayedPost = capturedRequests.get(capturedRequests.size() - 1);
        assertEquals("POST", replayedPost.method());
        assertEquals(originalOpId.toString(), replayedPost.headers().getFirst("X-Idempotency-Key"));
    }

    @Test
    void startReconciliationCompensatesOnlyOnExplicitRejectedReceipt_MOCK_TESTED() {
        RestVehicleClient vehicleClient = new RestVehicleClient(
                buildRestClient(2000), objectMapper, "maintenance-service", "veh-secret");
        Clock fixedClock = Clock.systemUTC();
        MaintenanceSyncScheduler syncScheduler = new MaintenanceSyncScheduler(
                maintenanceRepository, vehicleLockService, vehicleClient, noopTxManager(), fixedClock, 5, 30L);

        UUID scheduleId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        MaintenanceSchedule schedule = MaintenanceSchedule.builder()
                .id(scheduleId)
                .vehicleId(vehicleId)
                .preSyncStatus(MaintenanceStatus.SCHEDULED)
                .status(MaintenanceStatus.START_REQUESTED)
                .syncRequestId(opId)
                .nextSyncAttemptAt(Instant.now().minusSeconds(5))
                .priority(Priority.LOW)
                .build();

        when(maintenanceRepository.findPendingSyncSchedules(any(), any())).thenReturn(List.of(schedule));
        when(maintenanceRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        when(maintenanceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        responder.set(req -> new StubReply(200, """
                {"code":"OK","data":{"operationId":"%s","vehicleId":"%s","outcome":"REJECTED","rejectionReason":"Vehicle is RENTED"}}
                """.formatted(opId, vehicleId)));

        syncScheduler.reconcilePendingSchedules();
        assertEquals(MaintenanceStatus.SCHEDULED, schedule.getStatus());
        assertEquals("Vehicle is RENTED", schedule.getLastSyncError());
    }
}

