package crm.ms.service;

import crm.ms.dto.*;
import crm.ms.entity.Payment;
import crm.ms.entity.PaymentItem;
import crm.ms.entity.PaymentTransaction;
import crm.ms.enums.PaymentStatus;
import crm.ms.enums.TransactionMethod;
import crm.ms.enums.TransactionStatus;
import crm.ms.enums.TransactionType;
import crm.ms.exception.BusinessException;
import crm.ms.mapper.PaymentMapper;
import crm.ms.repository.PaymentItemRepository;
import crm.ms.repository.PaymentRepository;
import crm.ms.repository.PaymentTransactionRepository;
import crm.ms.service.gateway.PaymentGatewayStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentItemRepository paymentItemRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentMapper paymentMapper;
    private final List<PaymentGatewayStrategy> gatewayStrategies;

    @Override
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {
        log.info("Creating payment invoice for bookingId={}", request.getBookingId());

        if (paymentRepository.existsByBookingId(request.getBookingId())) {
            Payment existing = paymentRepository.findByBookingId(request.getBookingId()).orElseThrow();
            log.warn("Payment invoice already exists for bookingId={}, returning existing paymentCode={}",
                    request.getBookingId(), existing.getPaymentCode());
            return paymentMapper.toResponse(existing);
        }

        String paymentCode = generatePaymentCode();

        Payment payment = Payment.builder()
                .paymentCode(paymentCode)
                .bookingId(request.getBookingId())
                .customerId(request.getCustomerId())
                .status(PaymentStatus.PENDING)
                .subtotal(request.getSubtotal())
                .taxAmount(request.getTaxAmount() != null ? request.getTaxAmount() : BigDecimal.ZERO)
                .discountAmount(request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO)
                .penaltyAmount(request.getPenaltyAmount() != null ? request.getPenaltyAmount() : BigDecimal.ZERO)
                .totalAmount(request.getTotalAmount())
                .depositRequired(request.getDepositRequired() != null ? request.getDepositRequired() : BigDecimal.ZERO)
                .depositPaid(BigDecimal.ZERO)
                .depositRefunded(BigDecimal.ZERO)
                .amountPaid(BigDecimal.ZERO)
                .amountDue(request.getTotalAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                .dueDate(request.getDueDate() != null ? request.getDueDate() : LocalDate.now().plusDays(2))
                .notes(request.getNotes())
                .build();

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            for (PaymentItemRequestDto itemDto : request.getItems()) {
                PaymentItem item = paymentMapper.toItemEntity(itemDto);
                item.setPayment(payment);
                if (item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                    item.setAmount(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                }
                payment.getPaymentItems().add(item);
            }
        }

        // Tự động bổ sung mục tiền cọc nếu chưa có trong danh sách items
        boolean hasDepositItem = payment.getPaymentItems().stream()
                .anyMatch(it -> it.getItemType() == crm.ms.enums.PaymentItemType.DEPOSIT);
        if (!hasDepositItem && payment.getDepositRequired() != null && payment.getDepositRequired().compareTo(BigDecimal.ZERO) > 0) {
            PaymentItem depositItem = PaymentItem.builder()
                    .payment(payment)
                    .itemType(crm.ms.enums.PaymentItemType.DEPOSIT)
                    .description("Tiền cọc giữ xe bắt buộc")
                    .quantity(1)
                    .unitPrice(payment.getDepositRequired())
                    .amount(payment.getDepositRequired())
                    .build();
            payment.getPaymentItems().add(depositItem);
        }

        Payment saved = paymentRepository.save(payment);
        log.info("Payment invoice created successfully: paymentCode={}, id={}", saved.getPaymentCode(), saved.getId());
        return paymentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(1, 404, "Không tìm thấy hóa đơn thanh toán với ID: " + id));
        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByBookingId(UUID bookingId) {
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BusinessException(1, 404, "Không tìm thấy hóa đơn thanh toán cho đơn đặt xe: " + bookingId));
        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public CheckoutResponse processCheckout(UUID paymentId, CheckoutRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(1, 404, "Không tìm thấy hóa đơn thanh toán với ID: " + paymentId));

        if (payment.getStatus() == PaymentStatus.FULLY_PAID || payment.getStatus() == PaymentStatus.CANCELLED) {
            throw new BusinessException(1, 400, "Hóa đơn đã hoàn thành hoặc đã bị hủy, không thể thanh toán tiếp");
        }

        PaymentGatewayStrategy strategy = findGatewayStrategy(request.getMethod());

        PaymentTransaction transaction = PaymentTransaction.builder()
                .payment(payment)
                .txnType(request.getTxnType())
                .txnMethod(request.getMethod())
                .amount(request.getAmount())
                .currency(payment.getCurrency())
                .status(TransactionStatus.PENDING)
                .build();

        PaymentTransaction savedTxn = paymentTransactionRepository.save(transaction);
        payment.getPaymentTransactions().add(savedTxn);

        CheckoutResponse response = strategy.initiatePayment(payment, savedTxn, request);
        return response;
    }

    @Override
    @Transactional
    public PaymentResponse processCallback(Map<String, String> callbackParams, String gatewayType) {
        log.info("Processing payment callback for gatewayType={}, params={}", gatewayType, callbackParams);

        TransactionMethod method = resolveMethod(gatewayType);
        PaymentGatewayStrategy strategy = findGatewayStrategy(method);

        if (!strategy.verifyCallback(callbackParams)) {
            throw new BusinessException(1, 400, "Chữ ký callback từ cổng thanh toán không hợp lệ");
        }

        String paymentIdStr = callbackParams.get("paymentId");
        Payment payment = null;
        if (paymentIdStr != null && !paymentIdStr.isEmpty()) {
            payment = paymentRepository.findById(UUID.fromString(paymentIdStr)).orElse(null);
        }

        if (payment == null) {
            String vnpTxnRef = callbackParams.get("vnp_TxnRef");
            if (vnpTxnRef != null) {
                String paymentCode = vnpTxnRef.split("_")[0];
                payment = paymentRepository.findByPaymentCode(paymentCode).orElse(null);
            }
        }

        if (payment == null) {
            throw new BusinessException(1, 404, "Không tìm thấy hóa đơn tương ứng với giao dịch callback");
        }

        String gatewayTxnId = strategy.extractGatewayTxnId(callbackParams);
        boolean isSuccess = strategy.isSuccessful(callbackParams);

        List<PaymentTransaction> txns = paymentTransactionRepository.findByPaymentIdOrderByCreatedAtDesc(payment.getId());
        PaymentTransaction targetTxn = txns.stream()
                .filter(t -> t.getStatus() == TransactionStatus.PENDING)
                .findFirst()
                .orElse(txns.isEmpty() ? null : txns.get(0));

        if (targetTxn == null) {
            targetTxn = PaymentTransaction.builder()
                    .payment(payment)
                    .txnType(TransactionType.DEPOSIT_CHARGE)
                    .txnMethod(method)
                    .amount(payment.getTotalAmount())
                    .currency(payment.getCurrency())
                    .build();
            payment.getPaymentTransactions().add(targetTxn);
        }

        targetTxn.setGatewayTxnId(gatewayTxnId);
        targetTxn.setGatewayResponse(callbackParams.toString());
        targetTxn.setProcessedAt(LocalDateTime.now());

        if (isSuccess) {
            targetTxn.setStatus(TransactionStatus.SUCCESS);

            if (targetTxn.getTxnType() == TransactionType.DEPOSIT_CHARGE) {
                payment.setDepositPaid(payment.getDepositPaid().add(targetTxn.getAmount()));
                payment.setStatus(PaymentStatus.DEPOSIT_PAID);
                log.info("Deposit successfully paid for paymentCode={}, depositPaid={}",
                        payment.getPaymentCode(), payment.getDepositPaid());
            } else if (targetTxn.getTxnType() == TransactionType.CHARGE) {
                payment.setAmountPaid(payment.getAmountPaid().add(targetTxn.getAmount()));
                BigDecimal remaining = payment.getTotalAmount().subtract(payment.getAmountPaid());
                payment.setAmountDue(remaining.max(BigDecimal.ZERO));
                if (payment.getAmountDue().compareTo(BigDecimal.ZERO) <= 0) {
                    payment.setStatus(PaymentStatus.FULLY_PAID);
                    payment.setPaidAt(LocalDateTime.now());
                } else {
                    payment.setStatus(PaymentStatus.PARTIALLY_PAID);
                }
            }
        } else {
            targetTxn.setStatus(TransactionStatus.FAILED);
            log.warn("Payment transaction failed for paymentCode={}, gatewayTxnId={}",
                    payment.getPaymentCode(), gatewayTxnId);
        }

        paymentTransactionRepository.save(targetTxn);
        Payment updatedPayment = paymentRepository.save(payment);
        return paymentMapper.toResponse(updatedPayment);
    }

    @Override
    @Transactional
    public PaymentResponse addPenaltyFee(UUID paymentId, AddPenaltyRequest request) {
        log.info("Adding penalty fee for paymentId={}, amount={}, reason={}", paymentId, request.getAmount(), request.getDescription());
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(1, 404, "Không tìm thấy hóa đơn: " + paymentId));

        PaymentItem penaltyItem = PaymentItem.builder()
                .payment(payment)
                .itemType(request.getItemType())
                .description(request.getDescription())
                .quantity(1)
                .unitPrice(request.getAmount())
                .amount(request.getAmount())
                .build();

        payment.getPaymentItems().add(penaltyItem);
        payment.setPenaltyAmount(payment.getPenaltyAmount().add(request.getAmount()));
        payment.setTotalAmount(payment.getTotalAmount().add(request.getAmount()));
        payment.setAmountDue(payment.getAmountDue().add(request.getAmount()));

        Payment saved = paymentRepository.save(payment);
        return paymentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PaymentResponse processDepositRefund(UUID paymentId, RefundDepositRequest request) {
        log.info("Processing deposit refund for paymentId={}, refundAmount={}", paymentId, request.getRefundAmount());
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(1, 404, "Không tìm thấy hóa đơn: " + paymentId));

        if (payment.getDepositPaid().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(1, 400, "Đơn này chưa thanh toán tiền cọc, không thể hoàn cọc");
        }

        BigDecimal remainingDeposit = payment.getDepositPaid().subtract(payment.getDepositRefunded());
        if (request.getRefundAmount().compareTo(remainingDeposit) > 0) {
            throw new BusinessException(1, 400, "Số tiền hoàn cọc vượt quá tiền cọc còn lại khả dụng (" + remainingDeposit + " VND)");
        }

        payment.setDepositRefunded(payment.getDepositRefunded().add(request.getRefundAmount()));
        payment.setStatus(PaymentStatus.REFUNDED);

        PaymentTransaction refundTxn = PaymentTransaction.builder()
                .payment(payment)
                .txnType(TransactionType.DEPOSIT_REFUND)
                .txnMethod(TransactionMethod.BANK_TRANSFER)
                .amount(request.getRefundAmount())
                .currency(payment.getCurrency())
                .status(TransactionStatus.SUCCESS)
                .gatewayTxnId("REFUND-" + System.currentTimeMillis())
                .gatewayResponse(String.format("Hoàn cọc %s VND về STK %s - %s (%s). Lý do: %s",
                        request.getRefundAmount(),
                        request.getCustomerBankAccount(),
                        request.getCustomerAccountName(),
                        request.getCustomerBankName(),
                        request.getReason()))
                .processedAt(LocalDateTime.now())
                .build();

        paymentTransactionRepository.save(refundTxn);
        Payment saved = paymentRepository.save(payment);
        return paymentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PaymentResponse processDepositRefundByBookingId(UUID bookingId, RefundDepositRequest request) {
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new BusinessException(1, 404, "Không tìm thấy hóa đơn cho đơn đặt xe: " + bookingId));
        return processDepositRefund(payment.getId(), request);
    }

    private PaymentGatewayStrategy findGatewayStrategy(TransactionMethod method) {
        return gatewayStrategies.stream()
                .filter(s -> s.getSupportedMethod() == method)
                .findFirst()
                .orElseGet(() -> gatewayStrategies.stream()
                        .filter(s -> s.getSupportedMethod() == TransactionMethod.CASH)
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(1, 400, "Không hỗ trợ cổng thanh toán: " + method)));
    }

    private TransactionMethod resolveMethod(String gatewayType) {
        if ("vnpay".equalsIgnoreCase(gatewayType)) return TransactionMethod.VNPAY;
        if ("payos".equalsIgnoreCase(gatewayType)) return TransactionMethod.PAYOS;
        return TransactionMethod.CASH;
    }

    private String generatePaymentCode() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomSuffix = String.format("%04d", new Random().nextInt(10000));
        return "PAY-" + dateStr + "-" + randomSuffix;
    }
}
