package crm.ms.repository;

import crm.ms.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    List<PaymentTransaction> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
    Optional<PaymentTransaction> findByGatewayTxnId(String gatewayTxnId);
}

