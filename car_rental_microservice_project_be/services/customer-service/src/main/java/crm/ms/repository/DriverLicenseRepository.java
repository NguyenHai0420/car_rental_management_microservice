package crm.ms.repository;

import crm.ms.entity.DriverLicense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DriverLicenseRepository extends JpaRepository<DriverLicense, UUID> {
    Optional<DriverLicense> findByCustomerId(UUID customerId);
    Optional<DriverLicense> findByLicenseNumber(String licenseNumber);
    boolean existsByLicenseNumber(String licenseNumber);
}