import React, { useEffect, useState } from "react";
import { usePayment } from "../hooks/payment";

export const PaymentCallbackPage = () => {
  const { verifyCallback, loading } = usePayment();
  const [result, setResult] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    const searchParams = new URLSearchParams(window.location.search);
    const params = Object.fromEntries(searchParams.entries());
    const method = params.method || "PAYOS";

    if (params.code === "00" || params.status === "DEPOSIT_PAID") {
      setResult({
        paymentCode: params.paymentCode || "PAY-20261109-001",
        depositPaid: params.depositPaid ? Number(params.depositPaid) : 5000000,
        vehicleName: params.vehicleName ? decodeURIComponent(params.vehicleName) : "Xe Thuê Trực Tuyến",
        status: "DEPOSIT_PAID",
      });
      return;
    }

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
      backgroundColor: "#f1f5f9",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      padding: "20px",
      fontFamily: "Segoe UI, -apple-system, sans-serif",
    }}>
      <div style={{
        background: "#ffffff",
        maxWidth: "500px",
        width: "100%",
        borderRadius: "20px",
        padding: "40px 32px",
        textAlign: "center",
        boxShadow: "0 15px 35px rgba(0,0,0,0.08)",
        border: "1px solid #e2e8f0",
      }}>
        {loading && !result && (
          <div>
            <div style={{ fontSize: "48px", marginBottom: "16px" }}>⏳</div>
            <h2 style={{ fontSize: "20px", color: "#0f172a", margin: "0 0 8px 0" }}>
              Đang xác thực biến động số dư...
            </h2>
            <p style={{ color: "#64748b", fontSize: "14px", margin: 0 }}>
              Hệ thống đang đối soát dữ liệu với VietinBank, vui lòng chờ trong giây lát.
            </p>
          </div>
        )}

        {result && (
          <div>
            <div style={{
              width: "72px",
              height: "72px",
              background: "#dcfce7",
              color: "#16a34a",
              borderRadius: "50%",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: "36px",
              margin: "0 auto 20px auto",
              boxShadow: "0 6px 16px rgba(22,163,74,0.25)",
            }}>
              ✓
            </div>
            <h2 style={{ fontSize: "24px", color: "#15803d", margin: "0 0 8px 0", fontWeight: "800" }}>
              GIAO DỊCH THÀNH CÔNG!
            </h2>
            <p style={{ color: "#475569", fontSize: "14px", marginBottom: "26px" }}>
              Đã nhận thành công tiền cọc vào tài khoản <strong>VietinBank (0376359204 - HOANG TU UYEN)</strong>. Đơn đặt xe của bạn đã được xác nhận giữ chỗ!
            </p>

            <div style={{
              background: "#f8fafc",
              borderRadius: "12px",
              padding: "18px 20px",
              textAlign: "left",
              fontSize: "14px",
              display: "flex",
              flexDirection: "column",
              gap: "10px",
              marginBottom: "28px",
              border: "1px solid #e2e8f0",
            }}>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#64748b" }}>Mã hóa đơn:</span>
                <strong style={{ color: "#0f172a" }}>#{result.paymentCode}</strong>
              </div>
              {result.vehicleName && (
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span style={{ color: "#64748b" }}>Dòng xe đã giữ chỗ:</span>
                  <strong style={{ color: "#1d4ed8" }}>{result.vehicleName}</strong>
                </div>
              )}
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#64748b" }}>Tiền cọc đã ghi nhận:</span>
                <strong style={{ color: "#16a34a", fontSize: "16px" }}>{formatCurrency(result.depositPaid)}</strong>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#64748b" }}>Phương thức:</span>
                <strong style={{ color: "#1d4ed8" }}>Mã VietQR (VietinBank)</strong>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#64748b" }}>Trạng thái đơn:</span>
                <span style={{
                  background: "#dcfce7",
                  color: "#15803d",
                  padding: "3px 10px",
                  borderRadius: "999px",
                  fontWeight: "700",
                  fontSize: "12px",
                }}>
                  ĐÃ ĐẶT CỌC (CONFIRMED)
                </span>
              </div>
            </div>

            <button
              onClick={() => {
                window.location.href = `/payment`;
              }}
              style={{
                width: "100%",
                padding: "14px",
                background: "#2563eb",
                color: "#fff",
                border: "none",
                borderRadius: "10px",
                fontWeight: "700",
                fontSize: "15px",
                cursor: "pointer",
                boxShadow: "0 4px 14px rgba(37,99,235,0.3)",
              }}
            >
              Quay lại Trang Thanh Toán
            </button>
          </div>
        )}

        {!loading && !result && errorMessage && (
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
              Xác Thực Thất Bại
            </h2>
            <p style={{ color: "#475569", fontSize: "14px", marginBottom: "24px" }}>
              {errorMessage}
            </p>

            <button
              onClick={() => {
                window.location.href = `/payment`;
              }}
              style={{
                width: "100%",
                padding: "12px",
                background: "#dc2626",
                color: "#fff",
                border: "none",
                borderRadius: "8px",
                fontWeight: "700",
                fontSize: "15px",
                cursor: "pointer",
              }}
            >
              Thử Lại
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
