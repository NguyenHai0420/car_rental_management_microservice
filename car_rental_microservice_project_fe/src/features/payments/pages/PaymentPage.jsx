import React, { useEffect } from "react";
import { usePayment } from "../hooks/payment";
import { PaymentForm } from "../components/PaymentForm";

export const PaymentPage = ({ bookingId }) => {
  const { payment, loading, error, fetchPaymentByBooking, initiateCheckout } = usePayment();

  // Lấy bookingId từ URL search params nếu không truyền qua prop
  const currentBookingId = bookingId || new URLSearchParams(window.location.search).get("bookingId");

  useEffect(() => {
    if (currentBookingId) {
      fetchPaymentByBooking(currentBookingId).catch(() => {
        // Có thể là chưa có hóa đơn thì hiển thị form rỗng hoặc tạo mới
      });
    }
  }, [currentBookingId, fetchPaymentByBooking]);

  return (
    <div style={{ minHeight: "100vh", backgroundColor: "#f3f4f6", padding: "40px 16px" }}>
      <div style={{ maxWidth: "720px", margin: "0 auto 24px auto", textAlign: "center" }}>
        <h1 style={{ fontSize: "28px", fontWeight: "bold", color: "#111827", margin: "0 0 8px 0" }}>
          Cổng Thanh Toán Thuê Xe Trực Tuyến
        </h1>
        <p style={{ color: "#6b7280", margin: 0 }}>
          Hệ thống thanh toán bảo mật đa kênh (VNPAY / PayOS VietQR / Mock)
        </p>
      </div>

      {loading && !payment && (
        <div style={{ textAlign: "center", padding: "40px", color: "#2563eb", fontWeight: "600" }}>
          Đang tải thông tin hóa đơn thanh toán...
        </div>
      )}

      {error && !payment && (
        <div style={{
          maxWidth: "720px",
          margin: "0 auto",
          padding: "16px",
          background: "#fee2e2",
          color: "#b91c1c",
          borderRadius: "8px",
          textAlign: "center",
        }}>
          {error}
        </div>
      )}

      {payment ? (
        <PaymentForm payment={payment} onCheckout={initiateCheckout} loading={loading} />
      ) : (
        !loading && (
          <div style={{ maxWidth: "720px", margin: "0 auto", background: "#fff", padding: "32px", borderRadius: "12px", textAlign: "center" }}>
            <p style={{ color: "#4b5563" }}>Vui lòng chọn đơn đặt xe để tiến hành thanh toán.</p>
            <span style={{ fontSize: "13px", color: "#9ca3af" }}>
              (Ví dụ truy cập: <code>/payment?bookingId=uuid-cua-ban</code>)
            </span>
          </div>
        )
      )}
    </div>
  );
};
