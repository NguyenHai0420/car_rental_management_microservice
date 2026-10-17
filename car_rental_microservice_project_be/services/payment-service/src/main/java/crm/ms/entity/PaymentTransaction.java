package crm.ms.entity;

import crm.ms.enums.TransactionMethod;
import crm.ms.enums.TransactionStatus;
import crm.ms.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lịch sử giao dịch: lần charge, refund, từng lần thử lại.
 * gateway_txn_id để đối soát với cổng thanh toán bên ngoài.
 */
@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_ptxns_payment", columnList = "payment_id"),
        @Index(name = "idx_ptxns_status", columnList = "status"),
        @Index(name = "idx_ptxns_gateway", columnList = "gateway_txn_id"),
        @Index(name = "idx_ptxns_processed", columnList = "processed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ptxns_payment"))
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 20)
    private TransactionType txnType;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_method", nullable = false, length = 20)
    private TransactionMethod txnMethod;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TransactionStatus status = TransactionStatus.PENDING;

    /** ID giao dịch từ cổng thanh toán */
    @Column(name = "gateway_txn_id", length = 100)
    private String gatewayTxnId;

    /** Response message từ gateway */
    @Column(name = "gateway_response", length = 500)
    private String gatewayResponse;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
