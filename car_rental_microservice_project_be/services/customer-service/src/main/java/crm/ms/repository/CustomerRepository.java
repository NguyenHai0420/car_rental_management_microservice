package crm.ms.repository;

import crm.ms.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByKeycloakUserId(UUID keycloakUserId);
    Optional<Customer> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByKeycloakUserId(UUID keycloakUserId);
}