package crm.ms.service;

import crm.ms.entity.MaintenanceVehicleLock;
import crm.ms.repository.MaintenanceVehicleLockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Quản lý hàng khóa vật lý theo vehicleId trong maintenance_db.
 * Tách riêng transaction khởi tạo dòng khóa để không giữ đồng thời 2 connection DB
 * và không làm bẩn Hibernate Session của transaction nghiệp vụ chính khi có race ở lần tạo đầu tiên.
 */
@Service
@Slf4j
public class MaintenanceVehicleLockService {

    private final MaintenanceVehicleLockRepository lockRepository;
    private final TransactionTemplate requiresNewTxTemplate;

    public MaintenanceVehicleLockService(
            MaintenanceVehicleLockRepository lockRepository,
            PlatformTransactionManager transactionManager) {
        this.lockRepository = lockRepository;
        this.requiresNewTxTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Đảm bảo tồn tại một hàng dữ liệu vật lý đã commit trong maintenance_vehicle_locks cho vehicleId.
     */
    public void ensureLockRowExists(UUID vehicleId) {
        requiresNewTxTemplate.executeWithoutResult(status -> {
            if (lockRepository.existsById(vehicleId)) {
                return;
            }
            try {
                lockRepository.saveAndFlush(MaintenanceVehicleLock.builder()
                        .vehicleId(vehicleId)
                        .build());
            } catch (DataIntegrityViolationException ex) {
                status.setRollbackOnly();
                log.debug("Concurrent initialization of vehicle lock row handled cleanly for vehicleId={}", vehicleId);
            }
        });
    }

    /**
     * Khóa bi quan (SELECT ... FOR UPDATE) trên hàng dữ liệu thực tế của vehicleId.
     * Phương thức này phải được gọi bên trong transaction nghiệp vụ chính đang mở.
     */
    public MaintenanceVehicleLock acquireLock(UUID vehicleId) {
        return lockRepository.findByVehicleIdForUpdate(vehicleId)
                .orElseThrow(() -> new IllegalStateException(
                        "Vehicle lock row missing after initialization for vehicleId=" + vehicleId));
    }
}

