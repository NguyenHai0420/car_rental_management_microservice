import React, { useState, useEffect, useRef } from "react";
import { paymentService } from "../services/payment";

export const PaymentForm = ({ payment }) => {
  const [copiedField, setCopiedField] = useState(null);
  const [autoRedirecting, setAutoRedirecting] = useState(false);
  const pollingRef = useRef(null);

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

  const copyToClipboard = (text, fieldName) => {
    navigator.clipboard.writeText(text);
    setCopiedField(fieldName);
    setTimeout(() => setCopiedField(null), 2500);
  };

  // 1. Khoản tiền cọc giữ xe (lấy từ đơn hàng)
  const depositItem = payment.paymentItems?.find((it) => it.itemType === "DEPOSIT");
  const depositAmount = Number(
    payment.depositRequired ||
    depositItem?.amount ||
    (payment.depositPaid > 0 ? payment.depositPaid : 5000000)
  );

  // 2. Tiền thuê xe trước thuế và thuế VAT
  const subtotal = Number(
    payment.subtotal ||
    (payment.rentalAmount ? Math.round(payment.rentalAmount / 1.1) : 4800000)
  );

  const taxAmount = Number(
    payment.taxAmount ||
    Math.round(subtotal * 0.1)
  );

  // Tiền thuê xe đã gồm VAT 10%
  const rentalAmount = Number(
    payment.rentalAmount ||
    (subtotal + taxAmount)
  );

  // 3. TỔNG CỘNG HÓA ĐƠN THANH TOÁN CHUNG = Tiền thuê (đã gồm VAT) + Tiền cọc xe
  const totalPaymentAmount = rentalAmount + depositAmount;

  const cleanPaymentCode = payment.paymentCode ? payment.paymentCode.replace(/-/g, "") : "PAYDEMO";
  const transferContent = `TT ${cleanPaymentCode}`;

  // Mã VietQR chuẩn Napas247 quốc gia: Quét 1 lần thanh toán chung cả tiền thuê và tiền cọc
  const directVietQrUrl = `https://img.vietqr.io/image/ICB-0376359204-compact2.png?amount=${totalPaymentAmount}&addInfo=${encodeURIComponent(transferContent)}&accountName=HOANG%20TU%20UYEN`;

  const redirectToSuccess = () => {
    const vName = encodeURIComponent(payment.vehicleName || "Xe thuê");
    window.location.href = `/payment/callback?code=00&method=PAYOS&paymentCode=${payment.paymentCode}&amountPaid=${totalPaymentAmount}&depositPaid=${depositAmount}&rentalPaid=${rentalAmount}&vehicleName=${vName}&status=FULLY_PAID`;
  };

  // === LẮNG NGHE KẾT QUẢ CHUYỂN KHOẢN TỪ BACKEND (CHỈ NHẢY TRANG KHI ĐÃ CÓ TIỀN THẬT) ===
  useEffect(() => {
    if (payment.status === "DEPOSIT_PAID" || payment.status === "FULLY_PAID") {
      redirectToSuccess();
      return;
    }

    // Polling ngầm kiểm tra trạng thái từ backend:
    // KHÔNG BAO GIỜ TỰ NHẢY NẾU CHƯA CÓ TIỀN. Màn hình thanh toán sẽ giữ nguyên mãi mãi cho đến khi nhận được tiền!
    pollingRef.current = setInterval(async () => {
      try {
        let updated = null;
        if (payment.id && !payment.id.startsWith("pay-")) {
          updated = await paymentService.getPaymentById(payment.id);
        } else if (payment.bookingId) {
          updated = await paymentService.getPaymentByBookingId(payment.bookingId);
        }

        if (updated && (updated.status === "DEPOSIT_PAID" || updated.status === "FULLY_PAID")) {
          clearInterval(pollingRef.current);
          setAutoRedirecting(true);
          setTimeout(() => {
            redirectToSuccess();
          }, 1000);
        }
      } catch (e) {
        // Chờ phản hồi ngân hàng
      }
    }, 2000);

    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current);
    };
  }, [payment.id, payment.bookingId, payment.status]);

  return (
    <div style={{ maxWidth: "720px", margin: "0 auto", padding: "12px", fontFamily: "Segoe UI, -apple-system, sans-serif" }}>
      {/* Banner Trạng Thái Tự Động Nhảy Trang */}
      {autoRedirecting && (
        <div style={{
          background: "#16a34a",
          color: "#fff",
          padding: "16px 20px",
          borderRadius: "14px",
          marginBottom: "20px",
          textAlign: "center",
          fontWeight: "bold",
          fontSize: "17px",
          boxShadow: "0 10px 25px rgba(22,163,74,0.35)",
        }}>
          🎉 ĐÃ NHẬN TIỀN VÀO VIETINBANK (HOANG TU UYEN)! Đang chuyển hướng...
        </div>
      )}

      {/* THẺ HÓA ĐƠN TỔNG HỢP DUY NHẤT (GỒM ĐỦ: TIỀN THUÊ, VAT & TIỀN CỌC XE) */}
      <div style={{
        background: "#ffffff",
        borderRadius: "16px",
        boxShadow: "0 4px 20px rgba(0,0,0,0.06)",
        border: "1px solid #e2e8f0",
        overflow: "hidden",
        marginBottom: "24px",
      }}>
        <div style={{
          padding: "18px 24px",
          borderBottom: "1px solid #f1f5f9",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          background: "#f8fafc",
        }}>
          <div>
            <h2 style={{ margin: "0 0 4px 0", fontSize: "19px", color: "#0f172a", fontWeight: "800" }}>
              Hóa đơn #{payment.paymentCode}
            </h2>
            {payment.vehicleName && (
              <div style={{ fontSize: "14px", color: "#1d4ed8", fontWeight: "700", marginBottom: "4px" }}>
                🚗 {payment.vehicleName}
              </div>
            )}
            <span style={{ fontSize: "13px", color: "#64748b" }}>
              Hạn thanh toán: {payment.dueDate || "Trong 24 giờ"}
            </span>
          </div>
          <span style={{
            backgroundColor: "#fef3c7",
            color: "#b45309",
            padding: "6px 14px",
            borderRadius: "999px",
            fontWeight: "700",
            fontSize: "12px",
          }}>
            Chờ thanh toán
          </span>
        </div>

        {/* BẢNG BÓC TÁCH MINH BẠCH TRÊN HÓA ĐƠN */}
        <div style={{ padding: "20px 24px" }}>
          {/* 1. Tiền thuê xe trước thuế */}
          <div style={{ display: "flex", justifyContent: "space-between", color: "#475569", marginBottom: "10px", fontSize: "14px" }}>
            <span>Tiền thuê xe (Subtotal):</span>
            <strong style={{ color: "#0f172a" }}>{formatCurrency(subtotal)}</strong>
          </div>

          {/* 2. Thuế VAT 10% */}
          <div style={{ display: "flex", justifyContent: "space-between", color: "#475569", marginBottom: "10px", fontSize: "14px" }}>
            <span>Thuế giá trị gia tăng (VAT 10%):</span>
            <span>{formatCurrency(taxAmount)}</span>
          </div>

          {/* Tổng tiền thuê đã gồm VAT */}
          <div style={{ display: "flex", justifyContent: "space-between", color: "#1e40af", marginBottom: "12px", fontSize: "14px", fontWeight: "600" }}>
            <span>Tổng tiền thuê xe (đã gồm VAT):</span>
            <span>{formatCurrency(rentalAmount)}</span>
          </div>

          {/* 3. Tiền cọc giữ xe bắt buộc */}
          <div style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            color: "#166534",
            marginBottom: "14px",
            fontSize: "14px",
            background: "#f0fdf4",
            padding: "10px 14px",
            borderRadius: "10px",
            border: "1px solid #bbf7d0",
          }}>
            <div>
              <strong style={{ display: "block" }}>Tiền cọc giữ xe bắt buộc:</strong>
              <span style={{ fontSize: "12px", color: "#15803d" }}>(Sẽ được hoàn trả lại sau khi thanh lý hợp đồng trả xe)</span>
            </div>
            <strong style={{ fontSize: "16px" }}>{formatCurrency(depositAmount)}</strong>
          </div>

          <hr style={{ border: "none", borderTop: "2px dashed #cbd5e1", margin: "14px 0" }} />

          {/* 4. TỔNG SỐ TIỀN THANH TOÁN TOÀN BỘ ĐƠN HÀNG */}
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <div>
              <strong style={{ fontSize: "17px", color: "#0f172a", display: "block" }}>
                TỔNG CỘNG THANH TOÁN (Thuê + Cọc):
              </strong>
              <span style={{ fontSize: "12px", color: "#64748b" }}>
                Thanh toán chung 1 lần qua mã VietQR bên dưới
              </span>
            </div>
            <strong style={{ color: "#16a34a", fontSize: "22px", fontWeight: "900" }}>
              {formatCurrency(totalPaymentAmount)}
            </strong>
          </div>
        </div>
      </div>

      {/* KHU VỰC QUÉT MÃ VIETQR THANH TOÁN HÓA ĐƠN CHUNG */}
      <div style={{
        background: "#ffffff",
        borderRadius: "16px",
        boxShadow: "0 6px 25px rgba(0,0,0,0.08)",
        border: "2px solid #22c55e",
        padding: "26px 20px",
        textAlign: "center",
      }}>
        <div style={{
          display: "inline-flex",
          alignItems: "center",
          gap: "8px",
          background: "#dcfce7",
          color: "#15803d",
          padding: "6px 14px",
          borderRadius: "999px",
          fontSize: "13px",
          fontWeight: "700",
          marginBottom: "14px",
        }}>
          <span style={{ fontSize: "16px" }}>⚡</span> QUÉT MÃ VIETQR QUA APP NGÂN HÀNG
        </div>

        <h3 style={{ margin: "0 0 6px 0", fontSize: "20px", color: "#0f172a", fontWeight: "800" }}>
          Chuyển khoản trực tiếp VietinBank
        </h3>
        <p style={{ margin: "0 0 18px 0", fontSize: "14px", color: "#64748b" }}>
          Mở ứng dụng ngân hàng bất kỳ để quét mã thanh toán toàn bộ đơn hàng — <strong>Hệ thống sẽ tự động chuyển trang khi nhận tiền</strong>
        </p>

        {/* Khung Chứa Ảnh Mã QR */}
        <div style={{
          background: "#f8fafc",
          padding: "14px",
          borderRadius: "16px",
          display: "inline-block",
          border: "1px solid #e2e8f0",
          boxShadow: "0 4px 15px rgba(0,0,0,0.05)",
          marginBottom: "20px",
        }}>
          <img
            src={directVietQrUrl}
            alt="Mã VietQR VietinBank HOANG TU UYEN"
            style={{ width: "250px", height: "250px", display: "block", borderRadius: "8px" }}
          />
        </div>

        {/* Trạng thái Radar Quét Tự Động */}
        <div style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          gap: "10px",
          background: "#f0fdf4",
          border: "1px solid #bbf7d0",
          padding: "12px 18px",
          borderRadius: "12px",
          maxWidth: "500px",
          margin: "0 auto 20px auto",
          fontSize: "14px",
          color: "#166534",
          fontWeight: "600",
        }}>
          <span style={{
            display: "inline-block",
            width: "12px",
            height: "12px",
            borderRadius: "50%",
            background: "#22c55e",
            boxShadow: "0 0 0 5px rgba(34,197,94,0.3)",
          }}></span>
          <span>Đang chờ chuyển khoản... Quét mã xong trang sẽ tự động nhảy!</span>
        </div>

        {/* BẢNG THÔNG TIN CHUYỂN KHOẢN CHI TIẾT */}
        <div style={{
          background: "#f8fafc",
          padding: "18px 20px",
          borderRadius: "12px",
          fontSize: "14px",
          textAlign: "left",
          border: "1px solid #e2e8f0",
          maxWidth: "480px",
          margin: "0 auto",
          display: "flex",
          flexDirection: "column",
          gap: "12px",
        }}>
          {/* Ngân hàng */}
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ color: "#64748b", fontWeight: "500" }}>Ngân hàng thụ hưởng:</span>
            <strong style={{ color: "#0f172a", fontSize: "15px" }}>VietinBank (ICB)</strong>
          </div>

          {/* Số tài khoản */}
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ color: "#64748b", fontWeight: "500" }}>Số tài khoản:</span>
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <strong style={{ color: "#1d4ed8", fontSize: "17px", letterSpacing: "0.5px" }}>0376359204</strong>
              <button
                type="button"
                onClick={() => copyToClipboard("0376359204", "stk")}
                style={{
                  fontSize: "12px",
                  padding: "4px 10px",
                  borderRadius: "6px",
                  border: "none",
                  background: copiedField === "stk" ? "#16a34a" : "#1e40af",
                  color: "#ffffff",
                  cursor: "pointer",
                  fontWeight: "700",
                  transition: "all 0.2s",
                }}
              >
                {copiedField === "stk" ? "✓ Đã chép" : "Sao chép"}
              </button>
            </div>
          </div>

          {/* Chủ tài khoản */}
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ color: "#64748b", fontWeight: "500" }}>Chủ tài khoản:</span>
            <strong style={{ color: "#0f172a", fontSize: "15px" }}>HOANG TU UYEN</strong>
          </div>

          {/* Số tiền thanh toán toàn bộ hóa đơn */}
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <div>
              <span style={{ color: "#64748b", fontWeight: "500", display: "block" }}>Số tiền thanh toán:</span>
              <span style={{ fontSize: "12px", color: "#64748b" }}>(Gồm tiền thuê + VAT + tiền cọc)</span>
            </div>
            <strong style={{ color: "#16a34a", fontSize: "18px", fontWeight: "800" }}>
              {formatCurrency(totalPaymentAmount)}
            </strong>
          </div>

          {/* Nội dung chuyển khoản */}
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", background: "#fef3c7", padding: "10px 12px", borderRadius: "8px", border: "1px solid #fde68a" }}>
            <span style={{ color: "#92400e", fontWeight: "600" }}>Nội dung chuyển khoản:</span>
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <strong style={{ color: "#78350f", fontSize: "15px", fontFamily: "Consolas, monospace" }}>
                {transferContent}
              </strong>
              <button
                type="button"
                onClick={() => copyToClipboard(transferContent, "nd")}
                style={{
                  fontSize: "12px",
                  padding: "4px 10px",
                  borderRadius: "6px",
                  border: "none",
                  background: copiedField === "nd" ? "#16a34a" : "#b45309",
                  color: "#ffffff",
                  cursor: "pointer",
                  fontWeight: "700",
                }}
              >
                {copiedField === "nd" ? "✓ Đã chép" : "Sao chép"}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
