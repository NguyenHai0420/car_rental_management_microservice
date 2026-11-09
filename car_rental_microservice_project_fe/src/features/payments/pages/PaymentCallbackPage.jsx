import React, { useEffect, useState } from "react";
import { usePayment } from "../hooks/payment";

export const PaymentCallbackPage = () => {
  const { verifyCallback, loading } = usePayment();
  const [result, setResult] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    const searchParams = new URLSearchParams(window.location.search);
    const params = Object.fromEntries(searchParams.entries());
    const method = params.method || (params.vnp_ResponseCode ? "vnpay" : "mock");

    verifyCallback(params, method)
      .then((data) => {
        setResult(data);
      })
      .catch((err) => {
        setErrorMessage(err.response?.data?.message || err.message || "Xác thực giao dịch thất bại");
      });
  }, [verifyCallback]);

  const formatCurrency = (amount) => {
    return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount || 0);
  };

  return (
    <div style={{
      minHeight: "100vh",
      backgroundColor: "#f3f4f6",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      padding: "20px",
      fontFamily: "Segoe UI, sans-serif",
    }}>
      <div style={{
        background: "#ffffff",
        maxWidth: "480px",
        width: "100%",
        borderRadius: "16px",
        padding: "36px 28px",
        textAlign: "center",
        boxShadow: "0 10px 25px -5px rgba(0,0,0,0.1)",
      }}>
        {loading && (
          <div>
            <div style={{ fontSize: "40px", marginBottom: "16px" }}>⏳</div>
            <h2 style={{ fontSize: "20px", color: "#111827", margin: "0 0 8px 0" }}>
              Đang xác thực kết quả thanh toán...
            </h2>
            <p style={{ color: "#6b7280", fontSize: "14px", margin: 0 }}>
              Hệ thống đang đối soát dữ liệu với cổng thanh toán, vui lòng chờ trong giây lát.
            </p>
          </div>
        )}

        {!loading && result && (
          <div>
            <div style={{
              width: "64px",
              height: "64px",
              background: "#dcfce7",
              color: "#16a34a",
              borderRadius: "50%",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: "32px",
              margin: "0 auto 16px auto",
            }}>
              ✓
            </div>
            <h2 style={{ fontSize: "22px", color: "#15803d", margin: "0 0 8px 0" }}>
              Giao Dịch Thành Công!
            </h2>
            <p style={{ color: "#4b5563", fontSize: "14px", marginBottom: "24px" }}>
              Đơn đặt xe của bạn đã được cập nhật trạng thái đặt cọc thành công.
            </p>

            <div style={{
              background: "#f9fafb",
              borderRadius: "8px",
              padding: "16px",
              textAlign: "left",
              fontSize: "14px",
              display: "flex",
              flexDirection: "column",
              gap: "8px",
              marginBottom: "24px",
              border: "1px solid #e5e7eb",
            }}>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#6b7280" }}>Mã hóa đơn:</span>
                <strong>{result.paymentCode}</strong>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#6b7280" }}>Tiền cọc đã ghi nhận:</span>
                <strong style={{ color: "#15803d" }}>{formatCurrency(result.depositPaid)}</strong>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#6b7280" }}>Trạng thái hóa đơn:</span>
                <span style={{ color: "#2563eb", fontWeight: "600" }}>{result.status}</span>
              </div>
            </div>

            <button
              onClick={() => {
                window.location.href = `/bookings`;
              }}
              style={{
                width: "100%",
                padding: "12px",
                background: "#2563eb",
                color: "#fff",
                border: "none",
                borderRadius: "8px",
                fontWeight: "600",
                fontSize: "15px",
                cursor: "pointer",
              }}
            >
              Xem Chi Tiết Đơn Đặt Xe
            </button>
          </div>
        )}

        {!loading && errorMessage && (
          <div>
            <div style={{
              width: "64px",
              height: "64px",
              background: "#fee2e2",
              color: "#dc2626",
              borderRadius: "50%",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: "32px",
              margin: "0 auto 16px auto",
            }}>
              ✕
            </div>
            <h2 style={{ fontSize: "22px", color: "#b91c1c", margin: "0 0 8px 0" }}>
              Thanh Toán Thất Bại
            </h2>
            <p style={{ color: "#4b5563", fontSize: "14px", marginBottom: "24px" }}>
              {errorMessage}
            </p>

            <button
              onClick={() => {
                window.history.back();
              }}
              style={{
                width: "100%",
                padding: "12px",
                background: "#dc2626",
                color: "#fff",
                border: "none",
                borderRadius: "8px",
                fontWeight: "600",
                fontSize: "15px",
                cursor: "pointer",
              }}
            >
              Thử Lại Giao Dịch
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

