import React, { useState } from "react";

export const PaymentForm = ({ payment, onCheckout, loading }) => {
  const [selectedMethod, setSelectedMethod] = useState("PAYOS"); // PAYOS, VNPAY, CASH (MOCK)
  const [txnType, setTxnType] = useState("DEPOSIT_CHARGE"); // DEPOSIT_CHARGE hoặc CHARGE
  const [qrModal, setQrModal] = useState(null);

  if (!payment) {
    return (
      <div style={{ padding: "20px", textAlign: "center", color: "#666" }}>
        Không tìm thấy thông tin hóa đơn thanh toán.
      </div>
    );
  }

  const formatCurrency = (amount) => {
    return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount || 0);
  };

  const getStatusBadge = (status) => {
    const styles = {
      PENDING: { bg: "#fef3c7", text: "#b45309", label: "Chờ thanh toán" },
      DEPOSIT_PAID: { bg: "#dcfce7", text: "#15803d", label: "Đã đặt cọc" },
      FULLY_PAID: { bg: "#dbeafe", text: "#1d4ed8", label: "Đã thanh toán đủ" },
      PARTIALLY_PAID: { bg: "#e0e7ff", text: "#4338ca", label: "Thanh toán 1 phần" },
      FAILED: { bg: "#fee2e2", text: "#b91c1c", label: "Thất bại" },
      CANCELLED: { bg: "#f3f4f6", text: "#6b7280", label: "Đã hủy" },
    };
    const s = styles[status] || { bg: "#eee", text: "#333", label: status };
    return (
      <span style={{
        backgroundColor: s.bg,
        color: s.text,
        padding: "4px 10px",
        borderRadius: "999px",
        fontWeight: "600",
        fontSize: "12px",
      }}>
        {s.label}
      </span>
    );
  };

  const handlePay = async (e) => {
    e.preventDefault();
    const amountToPay = txnType === "DEPOSIT_CHARGE" 
      ? (payment.depositPaid > 0 ? 0 : 5000000) // hoặc lấy tiền cọc từ tier
      : payment.amountDue;

    const payload = {
      txnType: txnType,
      method: selectedMethod,
      amount: amountToPay > 0 ? amountToPay : payment.totalAmount,
      returnUrl: window.location.origin + "/payment/callback",
    };

    const res = await onCheckout(payment.id, payload);
    if (res?.qrCodeUrl && selectedMethod === "PAYOS") {
      setQrModal(res);
    } else if (res?.paymentUrl) {
      window.location.href = res.paymentUrl;
    }
  };

  return (
    <div style={{ maxWidth: "720px", margin: "0 auto", padding: "24px", fontFamily: "Segoe UI, sans-serif" }}>
      {/* Thẻ Hóa Đơn */}
      <div style={{
        background: "#ffffff",
        borderRadius: "12px",
        boxShadow: "0 4px 20px rgba(0,0,0,0.08)",
        border: "1px solid #e5e7eb",
        overflow: "hidden",
        marginBottom: "24px",
      }}>
        <div style={{
          padding: "20px 24px",
          borderBottom: "1px solid #f3f4f6",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}>
          <div>
            <h2 style={{ margin: "0 0 6px 0", fontSize: "20px", color: "#111827" }}>
              Hóa đơn #{payment.paymentCode}
            </h2>
            <span style={{ fontSize: "13px", color: "#6b7280" }}>
              Hạn thanh toán: {payment.dueDate || "Trong 24h"}
            </span>
          </div>
          <div>{getStatusBadge(payment.status)}</div>
        </div>

        {/* Bảng Chi Tiết Khoản Phí */}
        <div style={{ padding: "24px" }}>
          <h3 style={{ fontSize: "15px", color: "#374151", marginBottom: "16px", textTransform: "uppercase", letterSpacing: "0.5px" }}>
            Chi tiết khoản mục thuê xe
          </h3>

          <div style={{ display: "flex", flexDirection: "column", gap: "10px", marginBottom: "20px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", color: "#4b5563" }}>
              <span>Tổng tiền thuê xe (Subtotal)</span>
              <strong>{formatCurrency(payment.subtotal)}</strong>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", color: "#4b5563" }}>
              <span>Thuế giá trị gia tăng (VAT 10%)</span>
              <span>{formatCurrency(payment.taxAmount)}</span>
            </div>
            {payment.discountAmount > 0 && (
              <div style={{ display: "flex", justifyContent: "space-between", color: "#16a34a" }}>
                <span>Khuyến mãi / Giảm giá</span>
                <span>-{formatCurrency(payment.discountAmount)}</span>
              </div>
            )}
            <hr style={{ border: "none", borderTop: "1px dashed #e5e7eb", margin: "10px 0" }} />
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: "18px", color: "#111827" }}>
              <strong>Tổng tiền thanh toán</strong>
              <strong style={{ color: "#2563eb" }}>{formatCurrency(payment.totalAmount)}</strong>
            </div>
          </div>

          {/* Trạng thái thanh toán thực tế */}
          <div style={{
            background: "#f9fafb",
            padding: "16px",
            borderRadius: "8px",
            border: "1px solid #f3f4f6",
            display: "grid",
            gridTemplateColumns: "1fr 1fr",
            gap: "12px",
            fontSize: "14px",
          }}>
            <div>
              <span style={{ color: "#6b7280" }}>Tiền cọc đã đóng:</span>
              <div style={{ fontWeight: "600", color: "#16a34a" }}>{formatCurrency(payment.depositPaid)}</div>
            </div>
            <div>
              <span style={{ color: "#6b7280" }}>Số tiền còn nợ:</span>
              <div style={{ fontWeight: "600", color: payment.amountDue > 0 ? "#dc2626" : "#16a34a" }}>
                {formatCurrency(payment.amountDue)}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Form Chọn Phương Thức Thanh Toán */}
      <div style={{
        background: "#ffffff",
        borderRadius: "12px",
        boxShadow: "0 4px 20px rgba(0,0,0,0.08)",
        border: "1px solid #e5e7eb",
        padding: "24px",
      }}>
        <h3 style={{ fontSize: "17px", color: "#111827", margin: "0 0 16px 0" }}>
          Chọn loại giao dịch & Phương thức thanh toán
        </h3>

        {/* Chọn Loại Giao Dịch */}
        <div style={{ marginBottom: "20px" }}>
          <label style={{ display: "block", fontSize: "14px", fontWeight: "600", marginBottom: "8px", color: "#374151" }}>
            Loại giao dịch:
          </label>
          <div style={{ display: "flex", gap: "12px" }}>
            <button
              type="button"
              onClick={() => setTxnType("DEPOSIT_CHARGE")}
              style={{
                flex: 1,
                padding: "10px 16px",
                borderRadius: "8px",
                border: txnType === "DEPOSIT_CHARGE" ? "2px solid #2563eb" : "1px solid #d1d5db",
                background: txnType === "DEPOSIT_CHARGE" ? "#eff6ff" : "#fff",
                color: txnType === "DEPOSIT_CHARGE" ? "#1e40af" : "#374151",
                fontWeight: "600",
                cursor: "pointer",
              }}
            >
              Thanh toán tiền cọc xe
            </button>
            <button
              type="button"
              onClick={() => setTxnType("CHARGE")}
              style={{
                flex: 1,
                padding: "10px 16px",
                borderRadius: "8px",
                border: txnType === "CHARGE" ? "2px solid #2563eb" : "1px solid #d1d5db",
                background: txnType === "CHARGE" ? "#eff6ff" : "#fff",
                color: txnType === "CHARGE" ? "#1e40af" : "#374151",
                fontWeight: "600",
                cursor: "pointer",
              }}
            >
              Thanh toán tiền thuê xe
            </button>
          </div>
        </div>

        {/* Chọn Cổng Thanh Toán */}
        <div style={{ marginBottom: "24px" }}>
          <label style={{ display: "block", fontSize: "14px", fontWeight: "600", marginBottom: "8px", color: "#374151" }}>
            Cổng thanh toán:
          </label>
          <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
            {/* PayOS VietQR */}
            <label style={{
              display: "flex",
              alignItems: "center",
              padding: "12px 16px",
              border: selectedMethod === "PAYOS" ? "2px solid #2563eb" : "1px solid #e5e7eb",
              borderRadius: "8px",
              background: selectedMethod === "PAYOS" ? "#f0fdf4" : "#fff",
              cursor: "pointer",
            }}>
              <input
                type="radio"
                name="method"
                value="PAYOS"
                checked={selectedMethod === "PAYOS"}
                onChange={(e) => setSelectedMethod(e.target.value)}
                style={{ marginRight: "12px", accentColor: "#2563eb" }}
              />
              <div style={{ flex: 1 }}>
                <strong style={{ color: "#111827", display: "block" }}>PayOS — Quét mã VietQR Chuyển khoản</strong>
                <span style={{ fontSize: "13px", color: "#6b7280" }}>
                  Mã QR động chuyển trực tiếp vào tài khoản ngân hàng chính chủ
                </span>
              </div>
              <span style={{ fontSize: "20px" }}>📱</span>
            </label>

            {/* VNPAY */}
            <label style={{
              display: "flex",
              alignItems: "center",
              padding: "12px 16px",
              border: selectedMethod === "VNPAY" ? "2px solid #2563eb" : "1px solid #e5e7eb",
              borderRadius: "8px",
              background: selectedMethod === "VNPAY" ? "#eff6ff" : "#fff",
              cursor: "pointer",
            }}>
              <input
                type="radio"
                name="method"
                value="VNPAY"
                checked={selectedMethod === "VNPAY"}
                onChange={(e) => setSelectedMethod(e.target.value)}
                style={{ marginRight: "12px", accentColor: "#2563eb" }}
              />
              <div style={{ flex: 1 }}>
                <strong style={{ color: "#111827", display: "block" }}>VNPAY Sandbox</strong>
                <span style={{ fontSize: "13px", color: "#6b7280" }}>
                  Thẻ ATM nội địa, Visa, Master, VNPAY-QR
                </span>
              </div>
              <span style={{ fontSize: "20px" }}>💳</span>
            </label>

            {/* Mock Gateway */}
            <label style={{
              display: "flex",
              alignItems: "center",
              padding: "12px 16px",
              border: selectedMethod === "CASH" ? "2px solid #2563eb" : "1px solid #e5e7eb",
              borderRadius: "8px",
              background: selectedMethod === "CASH" ? "#fdf4ff" : "#fff",
              cursor: "pointer",
            }}>
              <input
                type="radio"
                name="method"
                value="CASH"
                checked={selectedMethod === "CASH"}
                onChange={(e) => setSelectedMethod(e.target.value)}
                style={{ marginRight: "12px", accentColor: "#2563eb" }}
              />
              <div style={{ flex: 1 }}>
                <strong style={{ color: "#111827", display: "block" }}>Cổng thanh toán giả lập (Mock Gateway)</strong>
                <span style={{ fontSize: "13px", color: "#6b7280" }}>
                  Dành riêng cho việc kiểm thử nhanh (Demo local)
                </span>
              </div>
              <span style={{ fontSize: "20px" }}>⚡</span>
            </label>
          </div>
        </div>

        <button
          onClick={handlePay}
          disabled={loading}
          style={{
            width: "100%",
            padding: "14px",
            background: "#2563eb",
            color: "#ffffff",
            border: "none",
            borderRadius: "8px",
            fontWeight: "bold",
            fontSize: "16px",
            cursor: loading ? "not-allowed" : "pointer",
            boxShadow: "0 4px 12px rgba(37,99,235,0.3)",
          }}
        >
          {loading ? "Đang xử lý..." : "Tiến hành thanh toán ngay"}
        </button>
      </div>

      {/* Modal Hiển Thị Mã VietQR Chuyển Khoản Trực Tiếp */}
      {qrModal && (
        <div style={{
          position: "fixed",
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: "rgba(0,0,0,0.6)",
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          zIndex: 9999,
          padding: "20px",
        }}>
          <div style={{
            background: "#fff",
            borderRadius: "16px",
            maxWidth: "420px",
            width: "100%",
            padding: "24px",
            textAlign: "center",
            boxShadow: "0 20px 25px -5px rgba(0,0,0,0.2)",
          }}>
            <h3 style={{ margin: "0 0 8px 0", color: "#111827" }}>Quét Mã VietQR Chuyển Khoản</h3>
            <p style={{ margin: "0 0 16px 0", fontSize: "13px", color: "#6b7280" }}>
              Mở ứng dụng ngân hàng bất kỳ để quét mã và chuyển tiền trực tiếp
            </p>

            {qrModal.qrCodeUrl && (
              <img
                src={qrModal.qrCodeUrl}
                alt="VietQR Code"
                style={{ width: "240px", height: "240px", margin: "0 auto 16px auto", borderRadius: "8px", border: "1px solid #e5e7eb" }}
              />
            )}

            <div style={{
              background: "#f9fafb",
              padding: "12px",
              borderRadius: "8px",
              fontSize: "13px",
              textAlign: "left",
              marginBottom: "16px",
            }}>
              <div><strong>Ngân hàng:</strong> {qrModal.bankName}</div>
              <div><strong>Số tài khoản:</strong> {qrModal.bankAccountNumber}</div>
              <div><strong>Chủ tài khoản:</strong> {qrModal.accountHolderName}</div>
              <div><strong>Số tiền:</strong> {formatCurrency(qrModal.amount)}</div>
              <div><strong>Nội dung CK:</strong> TT {qrModal.paymentCode?.replace("-", "")}</div>
            </div>

            <div style={{ display: "flex", gap: "10px" }}>
              <button
                onClick={() => setQrModal(null)}
                style={{
                  flex: 1,
                  padding: "10px",
                  borderRadius: "8px",
                  border: "1px solid #d1d5db",
                  background: "#fff",
                  cursor: "pointer",
                }}
              >
                Đóng
              </button>
              <button
                onClick={() => {
                  window.location.href = qrModal.paymentUrl;
                }}
                style={{
                  flex: 1,
                  padding: "10px",
                  borderRadius: "8px",
                  border: "none",
                  background: "#16a34a",
                  color: "#fff",
                  fontWeight: "600",
                  cursor: "pointer",
                }}
              >
                Tôi đã chuyển khoản
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
