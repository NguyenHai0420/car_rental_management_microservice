package crm.ms;

import crm.ms.config.NotificationProperties;
import crm.ms.controller.NotificationController;
import crm.ms.dto.DeliveryStatusResponse;
import crm.ms.dto.NotificationResponse;
import crm.ms.dto.NotificationSummaryResponse;
import crm.ms.entity.DeliveryStatus;
import crm.ms.entity.NotificationLog;
import crm.ms.enums.DeliveryEventType;
import crm.ms.enums.NotificationChannel;
import crm.ms.enums.NotificationStatus;
import crm.ms.exception.BusinessException;
import crm.ms.exception.GlobalExceptionHandler;
import crm.ms.mapper.NotificationMapper;
import crm.ms.repository.DeliveryStatusRepository;
import crm.ms.repository.NotificationRepository;
import crm.ms.service.NotificationPersistenceService;
import crm.ms.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationServiceAndControllerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private DeliveryStatusRepository deliveryStatusRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private NotificationService notificationService;

    private NotificationPersistenceService persistenceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        NotificationMapper mapper = Mappers.getMapper(NotificationMapper.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-12T02:00:00Z"), ZoneOffset.UTC);
        persistenceService = new NotificationPersistenceService(
                notificationRepository,
                deliveryStatusRepository,
                mapper,
                new NotificationProperties(),
                transactionManager,
                clock
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void listNotifications_normalizesBlankFilters_mapsErrorAndSortsDeliveryEvents() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        NotificationLog first = NotificationLog.builder()
                .id(firstId)
                .idempotencyKey("key-1")
                .requestHash("hash-1")
                .channel(NotificationChannel.EMAIL)
                .recipient("first@gmail.com")
                .subject("Subject 1")
                .status(NotificationStatus.SENT)
                .retryCount(0)
                .maxRetries(3)
                .createdAt(LocalDateTime.of(2026, 11, 12, 2, 10))
                .build();

        DeliveryStatus lateEvent = DeliveryStatus.builder()
                .id(UUID.randomUUID())
                .notificationLog(first)
                .eventType(DeliveryEventType.SENT)
                .provider("SMTP")
                .detail("Accepted by Gmail SMTP")
                .eventAt(LocalDateTime.of(2026, 11, 12, 2, 10, 5))
                .build();
        DeliveryStatus earlyEvent = DeliveryStatus.builder()
                .id(UUID.randomUUID())
                .notificationLog(first)
                .eventType(DeliveryEventType.QUEUED)
                .provider("SYSTEM")
                .detail("Queued")
                .eventAt(LocalDateTime.of(2026, 11, 12, 2, 10, 0))
                .build();
        first.setDeliveryStatuses(List.of(lateEvent, earlyEvent));

        NotificationLog second = NotificationLog.builder()
                .id(secondId)
                .idempotencyKey("key-2")
                .requestHash("hash-2")
                .channel(NotificationChannel.EMAIL)
                .recipient("second@gmail.com")
                .subject("Subject 2")
                .status(NotificationStatus.FAILED)
                .errorMessage("MailAuthenticationException: auth failed")
                .retryCount(0)
                .maxRetries(3)
                .createdAt(LocalDateTime.of(2026, 11, 12, 2, 5))
                .build();

        PageRequest pageable = PageRequest.of(0, 10);
        when(notificationRepository.searchNotifications(eq(NotificationStatus.SENT), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
        when(notificationRepository.findByIdInWithDeliveryStatuses(List.of(firstId, secondId)))
                .thenReturn(List.of(second, first));

        Page<NotificationResponse> result = persistenceService.listNotifications(
                NotificationStatus.SENT,
                "   ",
                "",
                pageable
        );

        verify(notificationRepository).searchNotifications(NotificationStatus.SENT, null, null, pageable);
        assertEquals(2, result.getContent().size());
        assertEquals(firstId, result.getContent().get(0).getId());
        assertEquals(secondId, result.getContent().get(1).getId());
        assertEquals("MailAuthenticationException: auth failed", result.getContent().get(1).getErrorMessage());

        List<DeliveryStatusResponse> events = result.getContent().get(0).getDeliveryEvents();
        assertNotNull(events);
        assertEquals(DeliveryEventType.QUEUED, events.get(0).getEventType());
        assertEquals(DeliveryEventType.SENT, events.get(1).getEventType());
        assertEquals("SMTP", events.get(1).getProvider());
        assertEquals("Accepted by Gmail SMTP", events.get(1).getDetail());
    }

    @Test
    void getSummary_aggregatesCountsAcrossStatuses() {
        when(notificationRepository.countGroupedByStatus()).thenReturn(List.of(
                new Object[]{NotificationStatus.SENT, 4L},
                new Object[]{NotificationStatus.FAILED, 2L},
                new Object[]{NotificationStatus.RETRY, 1L}
        ));

        NotificationSummaryResponse summary = persistenceService.getSummary();

        assertEquals(7L, summary.getTotal());
        assertEquals(4L, summary.getSent());
        assertEquals(2L, summary.getFailed());
        assertEquals(1L, summary.getRetry());
        assertEquals(0L, summary.getDeadLetter());
    }

    @Test
    void controller_listAndSummaryAndNotFound_returnExpectedHttpResponses() throws Exception {
        UUID notifId = UUID.randomUUID();
        NotificationResponse item = NotificationResponse.builder()
                .id(notifId)
                .idempotencyKey("k-1")
                .recipient("phongnp96@gmail.com")
                .subject("Test")
                .status(NotificationStatus.SENT)
                .build();
        when(notificationService.listNotifications(eq(NotificationStatus.SENT), eq("phongnp"), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));
        when(notificationService.getSummary())
                .thenReturn(NotificationSummaryResponse.builder().total(3).sent(2).failed(1).build());
        when(notificationService.getNotificationById(notifId))
                .thenThrow(new BusinessException(404, "Not found"));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("status", "SENT")
                        .param("recipient", "phongnp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].recipient").value("phongnp96@gmail.com"))
                .andExpect(jsonPath("$.data.content[0].status").value("SENT"));

        mockMvc.perform(get("/api/v1/notifications/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.sent").value(2))
                .andExpect(jsonPath("$.data.failed").value(1));

        mockMvc.perform(get("/api/v1/notifications/{id}", notifId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Not found"));
    }
}

