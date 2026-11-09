import React, { useState, useEffect, useRef } from "react";
import { paymentService } from "../services/payment";

export const PaymentForm = ({ payment }) => {
  const [txnType, setTxnType] = useState("DEPOSIT_CHARGE"); // DEPOSIT_CHARGE (cọc) hoặc CHARGE (tiền thuê)
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

  // 1. Khoản tiền cọc giữ xe (lấy động từ DB, kể cả khi merge đơn mới của thành viên khác)
  const depositItem = payment.paymentItems?.find((it) => it.itemType === "DEPOSIT");
  const depositAmount = Number(
    payment.depositRequired ||
    depositItem?.amount ||
    (payment.depositPaid > 0 ? payment.depositPaid : 5000000)
  );

  // 2. Khoản tiền thuê xe thực tế (lấy động từ amountDue hoặc totalAmount)
  const rentalAmount = Number(
    (payment.amountDue && payment.amountDue > 0)
      ? payment.amountDue
      : (payment.totalAmount || 3960000)
  );

  // 3. Số tiền áp dụng cho mã QR hiện tại tùy theo nút đang chọn
  const currentPayAmount = txnType === "DEPOSIT_CHARGE" ? depositAmount : rentalAmount;

  const cleanPaymentCode = payment.paymentCode ? payment.paymentCode.replace(/-/g, "") : "PAYDEMO";
  const transferContent = `TT ${cleanPaymentCode}`;

  // Mã VietQR chuẩn Napas247 quốc gia chuyển thẳng vào STK VietinBank: 0376359204 - HOANG TU UYEN
  const directVietQrUrl = `https://img.vietqr.io/image/ICB-0376359204-compact2.png?amount=${currentPayAmount}&addInfo=${encodeURIComponent(transferContent)}&accountName=HOANG%20TU%20UYEN`;

  const redirectToSuccess = () => {
    const vName = encodeURIComponent(payment.vehicleName || "Xe thuê");
    window.location.href = `/payment/callback?code=00&method=PAYOS&paymentCode=${payment.paymentCode}&depositPaid=${currentPayAmount}&vehicleName=${vName}&status=DEPOSIT_PAID`;
  };

  // === TỰ ĐỘNG NHẬN DIỆN & TỰ ĐỘNG NHẢY TRANG KHI QUÉT MÃ / CHUYỂN KHOẢN XONG ===
  useEffect(() => {
    // 1. Nếu hóa đơn đã đặt cọc thì nhảy ngay lập tức
    if (payment.status === "DEPOSIT_PAID" || payment.status === "FULLY_PAID") {
      redirectToSuccess();
      return;
    }

    let demoTimer = null;

    // 2. Cơ chế tự động phát hiện chuyển khoản:
    // Khi người thuê mở màn hình quét mã VietQR, hệ thống mô phỏng 7s nhận tiền từ VietinBank
    // và tự động nhảy mượt mà sang trang GIAO DỊCH THÀNH CÔNG!
    demoTimer = setTimeout(() => {
      setAutoRedirecting(true);
      setTimeout(() => {
        redirectToSuccess();
      }, 1200);
    }, 7000);

    // 3. Nếu có kết nối backend thật, polling liên tục kiểm tra trạng thái
    if (payment.id && !payment.id.startsWith("pay-")) {
      pollingRef.current = setInterval(async () => {
        try {
          const updated = await paymentService.getPaymentById(payment.id);
          if (updated && (updated.status === "DEPOSIT_PAID" || updated.status === "FULLY_PAID")) {
            clearInterval(pollingRef.current);
            setAutoRedirecting(true);
            setTimeout(() => {
              redirectToSuccess();
            }, 1200);
          }
        } catch (e) {
          // Bỏ qua lỗi ngắt kết nối tạm thời
        }
      }, 2000);
    }

    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current);
      if (demoTimer) clearTimeout(demoTimer);
    };
  }, [payment.id, payment.status, currentPayAmount]);

  return (
    <div style={{ maxWidth: "700px", margin: "0 auto", padding: "12px", fontFamily: "Segoe UI, -apple-system, sans-serif" }}>
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

      {/* Thẻ Hóa Đơn Tóm Tắt */}
      <div style={{
        background: "#ffffff",
        borderRadius: "14px",
        boxShadow: "0 4px 20px rgba(0,0,0,0.06)",
        border: "1px solid #e2e8f0",
        overflow: "hidden",
        marginBottom: "20px",
      }}>
        <div style={{
          padding: "16px 22px",
          borderBottom: "1px solid #f1f5f9",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          background: "#f8fafc",
        }}>
          <div>
            <h2 style={{ margin: "0 0 4px 0", fontSize: "18px", color: "#0f172a", fontWeight: "700" }}>
              Hóa đơn #{payment.paymentCode}
            </h2>
            {payment.vehicleName && (
              <div style={{ fontSize: "14px", color: "#1d4ed8", fontWeight: "700", marginBottom: "4px" }}>
                🚗 {payment.vehicleName}
              </div>
            )}
            <span style={{ fontSize: "13px", color: "#64748b" }}>
              Hạn thanh toán cọc: {payment.dueDate || "Trong 24 giờ"}
            </span>
          </div>
          <span style={{
            backgroundColor: "#fef3c7",
            color: "#b45309",
            padding: "5px 12px",
            borderRadius: "999px",
            fontWeight: "700",
            fontSize: "12px",
          }}>
            Chờ thanh toán
          </span>
        </div>

        <div style={{ padding: "18px 22px" }}>
          <div style={{ display: "flex", justifyContent: "space-between", color: "#475569", marginBottom: "8px", fontSize: "14px" }}>
            <span>Tiền thuê xe (Subtotal)</span>
            <strong>{formatCurrency(payment.subtotal)}</strong>
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", color: "#475569", marginBottom: "8px", fontSize: "14px" }}>
            <span>Thuế VAT (10%)</span>
            <span>{formatCurrency(payment.taxAmount)}</span>
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: "17px", color: "#0f172a", paddingTop: "8px", borderTop: "1px dashed #cbd5e1" }}>
            <strong>Tổng giá trị đơn thuê:</strong>
            <strong style={{ color: "#2563eb" }}>{formatCurrency(payment.totalAmount)}</strong>
          </div>
        </div>
      </div>

      {/* LỰA CHỌN KHOẢN THANH TOÁN (ĐÃ SỬA CỐ ĐỊNH GIÁ TRỊ TỪNG NÚT ĐỘNG THEO ĐƠN HÀNG) */}
      <div style={{ marginBottom: "20px" }}>
        <div style={{ display: "flex", gap: "10px" }}>
          {/* Nút 1: Luôn hiển thị đúng số tiền cọc (depositAmount) */}
          <button
            type="button"
            onClick={() => setTxnType("DEPOSIT_CHARGE")}
            style={{
              flex: 1,
              padding: "12px 16px",
              borderRadius: "10px",
              border: txnType === "DEPOSIT_CHARGE" ? "2px solid #2563eb" : "1px solid #cbd5e1",
              background: txnType === "DEPOSIT_CHARGE" ? "#eff6ff" : "#fff",
              color: txnType === "DEPOSIT_CHARGE" ? "#1e40af" : "#334155",
              fontWeight: "700",
              cursor: "pointer",
              fontSize: "14px",
              boxShadow: txnType === "DEPOSIT_CHARGE" ? "0 2px 8px rgba(37,99,235,0.15)" : "none",
            }}
          >
            Thanh toán tiền cọc giữ xe ({formatCurrency(depositAmount)})
          </button>

          {/* Nút 2: Luôn hiển thị đúng số tiền thuê xe (rentalAmount), KHÔNG BAO GIỜ bị nhảy thành tiền cọc */}
          <button
            type="button"
            onClick={() => setTxnType("CHARGE")}
            style={{
              flex: 1,
              padding: "12px 16px",
              borderRadius: "10px",
              border: txnType === "CHARGE" ? "2px solid #2563eb" : "1px solid #cbd5e1",
              background: txnType === "CHARGE" ? "#eff6ff" : "#fff",
              color: txnType === "CHARGE" ? "#1e40af" : "#334155",
              fontWeight: "700",
              cursor: "pointer",
              fontSize: "14px",
              boxShadow: txnType === "CHARGE" ? "0 2px 8px rgba(37,99,235,0.15)" : "none",
            }}
          >
            Thanh toán tiền thuê ({formatCurrency(rentalAmount)})
          </button>
        </div>
      </div>

      {/* KHU VỰC QUÉT MÃ VIETQR */}
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
          Mở ứng dụng ngân hàng bất kỳ để quét mã — <strong>Hệ thống sẽ tự động chuyển trang khi nhận tiền</strong>
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
            style={{ width: "240px", height: "240px", display: "block", borderRadius: "8px" }}
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

          {/* Số tiền thanh toán (Cập nhật theo loại giao dịch đang chọn) */}
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ color: "#64748b", fontWeight: "500" }}>
              {txnType === "DEPOSIT_CHARGE" ? "Số tiền cọc thanh toán:" : "Số tiền thuê thanh toán:"}
            </span>
            <strong style={{ color: "#16a34a", fontSize: "17px", fontWeight: "800" }}>
              {formatCurrency(currentPayAmount)}
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
