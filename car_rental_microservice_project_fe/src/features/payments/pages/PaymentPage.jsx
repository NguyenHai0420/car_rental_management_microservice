import React, { useEffect, useState } from "react";
import { usePayment } from "../hooks/payment";
import { PaymentForm } from "../components/PaymentForm";

// Hóa đơn demo mặc định nếu chưa chọn xe
const DEMO_PAYMENT = {
  id: "e4b3c2a1-0000-0000-0000-000000000001",
  paymentCode: "PAY-20261109-001",
  bookingId: "a1b2c3d4-1111-2222-3333-444455556666",
  customerId: "c7d8e9f0-9999-8888-7777-666655554444",
  status: "PENDING",
  vehicleName: "Xe Sedan (4-5 chỗ) - Mazda CX-5",
  subtotal: 3600000,
  taxAmount: 360000,
  discountAmount: 0,
  penaltyAmount: 0,
  totalAmount: 3960000,
  depositRequired: 5000000,
  depositPaid: 0,
  depositRefunded: 0,
  amountPaid: 0,
  amountDue: 3960000,
  currency: "VND",
  dueDate: "2026-11-12",
  notes: "Đơn thuê xe Mazda CX-5 (3 ngày)",
  paymentItems: [
    {
      id: "demo-item-1",
      itemType: "RENTAL_FEE",
      description: "Tiền thuê xe Mazda CX-5 (3 ngày)",
      quantity: 3,
      unitPrice: 1200000,
      amount: 3600000,
    },
    {
      id: "demo-item-2",
      itemType: "TAX",
      description: "Thuế VAT 10%",
      quantity: 1,
      unitPrice: 360000,
      amount: 360000,
    },
    {
      id: "demo-item-3",
      itemType: "DEPOSIT",
      description: "Tiền cọc giữ xe bắt buộc",
      quantity: 1,
      unitPrice: 5000000,
      amount: 5000000,
    },
  ],
};

export const PaymentPage = ({ bookingId, customOrder }) => {
  const { loading, error, fetchPaymentByBooking, initiateCheckout } = usePayment();

  // Khởi tạo ưu tiên đơn xe vừa tính/đặt từ màn hình Thuê xe
  const [activePayment, setActivePayment] = useState(() => {
    if (customOrder) return customOrder;
    const saved = sessionStorage.getItem("current_rental_payment");
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch (e) {}
    }
    return DEMO_PAYMENT;
  });

  const currentBookingId = bookingId || new URLSearchParams(window.location.search).get("bookingId");

  useEffect(() => {
    if (customOrder) {
      setActivePayment(customOrder);
      return;
    }

    // Nếu người dùng có truyền bookingId trên URL (khi merge hệ thống với đồng đội)
    if (currentBookingId) {
      fetchPaymentByBooking(currentBookingId)
        .then((data) => {
          if (data) setActivePayment(data);
        })
        .catch(() => {
          const saved = sessionStorage.getItem("current_rental_payment");
          if (saved) {
            try {
              setActivePayment(JSON.parse(saved));
              return;
            } catch (e) {}
          }
          setActivePayment(DEMO_PAYMENT);
        });
    } else {
      const saved = sessionStorage.getItem("current_rental_payment");
      if (saved) {
        try {
          setActivePayment(JSON.parse(saved));
          return;
        } catch (e) {}
      }
      setActivePayment(DEMO_PAYMENT);
    }
  }, [currentBookingId, customOrder, fetchPaymentByBooking]);

  const handleCheckoutWrapper = async (paymentId, payload) => {
    try {
      return await initiateCheckout(paymentId, payload);
    } catch (err) {
      console.warn("Backend checkout call failed, using client-side VietQR fallback:", err);
      const cleanCode = activePayment.paymentCode.replace(/-/g, "");
      return {
        transactionId: "demo-txn-id",
        paymentCode: activePayment.paymentCode,
        method: payload.method,
        amount: payload.amount,
        qrCodeUrl: `https://img.vietqr.io/image/ICB-0376359204-compact2.png?amount=${payload.amount}&addInfo=TT%20${cleanCode}&accountName=HOANG%20TU%20UYEN`,
        bankAccountNumber: "0376359204",
        bankName: "VietinBank (ICB)",
        accountHolderName: "HOANG TU UYEN",
        paymentUrl: window.location.origin + `/payment/callback?code=00&method=${payload.method}&txnId=LOCAL-${cleanCode}&amount=${payload.amount}`,
      };
    }
  };

  const isRealUserOrder = activePayment?.isCustomOrder || activePayment?.vehicleName;

  return (
    <div style={{ minHeight: "100vh", backgroundColor: "#f1f5f9", padding: "30px 16px" }}>
      <div style={{ maxWidth: "720px", margin: "0 auto 24px auto", textAlign: "center" }}>
        <h1 style={{ fontSize: "28px", fontWeight: "bold", color: "#111827", margin: "0 0 8px 0" }}>
          Cổng Thanh Toán Thuê Xe Trực Tuyến
        </h1>
        <p style={{ color: "#6b7280", margin: 0 }}>
          Hệ thống thanh toán bảo mật VietinBank Napas247 (HOANG TU UYEN)
        </p>

        {/* Thông báo trạng thái đơn hàng */}
        {isRealUserOrder ? (
          <div style={{
            display: "inline-flex",
            alignItems: "center",
            gap: "8px",
            marginTop: "12px",
            padding: "8px 18px",
            background: "#ecfdf5",
            color: "#065f46",
            borderRadius: "20px",
            fontSize: "13px",
            fontWeight: "700",
            border: "1px solid #a7f3d0",
            boxShadow: "0 2px 8px rgba(16,185,129,0.15)",
          }}>
            <span>🚘</span>
            <span>Đang thanh toán đơn: <strong>{activePayment.vehicleName}</strong></span>
          </div>
        ) : !currentBookingId && (
          <div style={{
            display: "inline-block",
            marginTop: "12px",
            padding: "6px 14px",
            background: "#eff6ff",
            color: "#1d4ed8",
            borderRadius: "20px",
            fontSize: "13px",
            fontWeight: "500",
            border: "1px solid #bfdbfe",
          }}>
            ℹ️ Đang hiển thị đơn mẫu trải nghiệm thanh toán
          </div>
        )}
      </div>

      {loading && !activePayment && (
        <div style={{ textAlign: "center", padding: "40px", color: "#2563eb", fontWeight: "600" }}>
          Đang tải thông tin hóa đơn thanh toán...
        </div>
      )}

      {error && !activePayment && (
        <div style={{
          maxWidth: "720px",
          margin: "0 auto 20px auto",
          padding: "16px",
          background: "#fee2e2",
          color: "#b91c1c",
          borderRadius: "8px",
          textAlign: "center",
        }}>
          {error}
        </div>
      )}

      <PaymentForm
        payment={activePayment}
        onCheckout={handleCheckoutWrapper}
        loading={loading}
      />
    </div>
  );
};
