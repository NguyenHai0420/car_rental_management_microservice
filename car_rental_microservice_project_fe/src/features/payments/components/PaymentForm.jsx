import { useState, useEffect, useRef, useCallback } from "react";
import { useNavigate } from "react-router";
import { paymentService } from "../services/payment";
import { formatCurrency } from "../utils/pricing";

export const PaymentForm = ({ payment }) => {
  const navigate = useNavigate();
  const [copiedField, setCopiedField] = useState(null);
  const [autoRedirecting, setAutoRedirecting] = useState(false);
  const pollingRef = useRef(null);

  const totalAmount = Number(
    payment?.totalAmount ??
    payment?.amountDue ??
    payment?.rentalAmount ??
    (payment?.subtotal ? Math.round(Number(payment.subtotal) * 1.1) : 500000)
  );

  const subtotal = Number(
    payment?.subtotal ??
    Math.round(totalAmount / 1.1)
  );
  const taxAmount = Number(
    payment?.taxAmount ??
    (totalAmount - subtotal)
  );
  const discountAmount = Number(payment?.discountAmount || 0);
  const penaltyAmount = Number(payment?.penaltyAmount || 0);

  // TỔNG CỘNG HÓA ĐƠN = Thanh toán toàn bộ 100%
  const totalPaymentAmount = totalAmount;

  const paymentCode = payment?.paymentCode || "PAY-DEMO";
  const vehicleName = payment?.vehicleName || payment?.notes || "Xe thuê";

  const isLocalDemoOnly = !payment?.id || String(payment.id).startsWith("pay-");

  const goToSuccess = useCallback((updatedPayment) => {
    const finalStatus = updatedPayment?.status || "FULLY_PAID";
    const finalTotal = Number(updatedPayment?.amountPaid || updatedPayment?.totalAmount || totalPaymentAmount);
    const vName = encodeURIComponent(vehicleName);
    const bId = payment?.bookingId ? `&bookingId=${encodeURIComponent(payment.bookingId)}` : "";
    navigate(
      `/payment/callback?code=00&method=PAYOS&paymentCode=${encodeURIComponent(paymentCode)}&amountPaid=${finalTotal}${bId}&vehicleName=${vName}&status=${encodeURIComponent(finalStatus)}`
    );
  }, [navigate, paymentCode, totalPaymentAmount, vehicleName, payment?.bookingId]);

  // === LẮNG NGHE BACKEND (SEPAY POLLING / WEBHOOK) MỖI 2.5 GIÂY ĐỂ TỰ NHẢY TRANG KHI TIỀN VÀO ===
  useEffect(() => {
    if (!payment || isLocalDemoOnly) return undefined;

    if (payment.status === "DEPOSIT_PAID" || payment.status === "FULLY_PAID") {
      const immediate = setTimeout(() => goToSuccess(payment), 0);
      return () => clearTimeout(immediate);
    }

    let redirectTimer = null;

    pollingRef.current = setInterval(async () => {
      try {
        const updated = await paymentService.getPaymentById(payment.id);
        if (updated && (updated.status === "DEPOSIT_PAID" || updated.status === "FULLY_PAID")) {
          clearInterval(pollingRef.current);
          setAutoRedirecting(true);
          redirectTimer = setTimeout(() => {
            goToSuccess(updated);
          }, 800);
        }
      } catch {
        // Tiếp tục chờ SePay xác nhận tiền vào tài khoản
      }
    }, 2500);

    return () => {
      if (redirectTimer) clearTimeout(redirectTimer);
      if (pollingRef.current) clearInterval(pollingRef.current);
    };
  }, [payment, isLocalDemoOnly, goToSuccess]);

  if (!payment) {
    return (
      <div className="empty">
        Không tìm thấy thông tin hóa đơn thanh toán.
      </div>
    );
  }

  const copyToClipboard = (text, fieldName) => {
    navigator.clipboard.writeText(text);
    setCopiedField(fieldName);
    setTimeout(() => setCopiedField(null), 2500);
  };

  const cleanPaymentCode = payment.paymentCode ? payment.paymentCode.replace(/-/g, "") : "PAYDEMO";
  const transferContent = `SEVQR ${cleanPaymentCode}`;

  // Mã VietQR chuẩn Napas247: Quét 1 lần thanh toán trọn gói vào STK VietinBank 0376359204 - HOANG TU UYEN
  const directVietQrUrl = `https://img.vietqr.io/image/ICB-0376359204-compact2.png?amount=${totalPaymentAmount}&addInfo=${encodeURIComponent(transferContent)}&accountName=HOANG%20TU%20UYEN`;

  return (
    <>
      {autoRedirecting && (
        <div className="notice success" role="status" style={{ fontWeight: 700, textAlign: "center", fontSize: "15px" }}>
          🎉 ĐÃ NHẬN TIỀN VÀO VIETINBANK (HOANG TU UYEN)! Đang chuyển sang trang xác nhận...
        </div>
      )}

      {/* THẺ HÓA ĐƠN CHUNG (GỒM TIỀN THUÊ, VAT & TIỀN CỌC XE) */}
      <section className="pay-card" aria-label="Chi tiết hóa đơn thanh toán">
        <div className="pay-card-head">
          <div>
            <h2>Hóa đơn #{payment.paymentCode}</h2>
            {payment.vehicleName && (
              <div style={{ fontSize: "14px", color: "var(--brand)", fontWeight: 700, marginTop: "3px" }}>
                🚗 {payment.vehicleName}
              </div>
            )}
            <p className="hint">Hạn thanh toán: {payment.dueDate || "Trong 24 giờ"}</p>
          </div>
          <span className="pay-badge warn">Chờ thanh toán</span>
        </div>

        <div className="pay-card-body">
          <div className="pay-row">
            <span>Tiền thuê xe (Subtotal):</span>
            <strong style={{ color: "var(--ink)" }}>{formatCurrency(subtotal)}</strong>
          </div>

          <div className="pay-row">
            <span>Thuế giá trị gia tăng (VAT 10%):</span>
            <span>{formatCurrency(taxAmount)}</span>
          </div>

          {discountAmount > 0 && (
            <div className="pay-row" style={{ color: "var(--ok)" }}>
              <span>Giảm giá:</span>
              <strong>-{formatCurrency(discountAmount)}</strong>
            </div>
          )}

          {penaltyAmount > 0 && (
            <div className="pay-row" style={{ color: "var(--danger)" }}>
              <span>Phí phạt / phụ thu phát sinh:</span>
              <strong>+{formatCurrency(penaltyAmount)}</strong>
            </div>
          )}

          <div className="pay-row" style={{ color: "var(--brand)", fontWeight: 600 }}>
            <span>Tổng tiền thuê xe (đã gồm VAT):</span>
            <strong>{formatCurrency(totalPaymentAmount)}</strong>
          </div>

          <hr className="pay-divider" />

          <div className="pay-total-row">
            <div>
              <strong style={{ fontSize: "16px", display: "block" }}>TỔNG CỘNG THANH TOÁN (100%):</strong>
              <span className="hint">Thanh toán trọn gói qua mã VietQR bên dưới để xác nhận đặt xe</span>
            </div>
            <strong className="pay-total-amount">{formatCurrency(totalPaymentAmount)}</strong>
          </div>
        </div>
      </section>

      {/* KHU VỰC QUÉT MÃ VIETQR VIETINBANK */}
      <section className="qr-panel" aria-label="Thanh toán bằng mã VietQR">
        <span className="pay-badge ok">⚡ QUÉT MÃ VIETQR QUA APP NGÂN HÀNG</span>

        <div>
          <h3 style={{ fontSize: "20px", fontWeight: 800 }}>Chuyển khoản trực tiếp VietinBank</h3>
          <p className="hint" style={{ fontSize: "14px", marginTop: "6px" }}>
            Mở ứng dụng ngân hàng bất kỳ để quét mã thanh toán — <strong>Hệ thống tự động xác nhận đặt xe khi nhận tiền</strong>
          </p>
        </div>

        <div className="qr-frame">
          <img
            src={directVietQrUrl}
            alt="Mã VietQR VietinBank HOANG TU UYEN"
          />
        </div>

        <div className="qr-radar" role="status">
          <span className="radar-dot" aria-hidden="true" />
          <span>
            Đang kiểm tra biến động số dư VietinBank (0376359204) qua SePay... Hệ thống sẽ tự động xác nhận đơn xe ngay khi nhận tiền!
          </span>
        </div>

        <div className="bank-table">
          <div className="bank-row">
            <span className="bank-row-label">Ngân hàng thụ hưởng:</span>
            <strong>VietinBank (ICB)</strong>
          </div>

          <div className="bank-row">
            <span className="bank-row-label">Số tài khoản:</span>
            <div className="copy-inline">
              <strong style={{ color: "var(--brand)", fontSize: "16px", letterSpacing: "0.4px" }}>0376359204</strong>
              <button
                type="button"
                className={copiedField === "stk" ? "btn btn-success btn-sm copy-btn" : "btn btn-outline-secondary btn-sm copy-btn"}
                onClick={() => copyToClipboard("0376359204", "stk")}
              >
                {copiedField === "stk" ? "✓ Đã chép" : "Sao chép"}
              </button>
            </div>
          </div>

          <div className="bank-row">
            <span className="bank-row-label">Chủ tài khoản:</span>
            <strong>HOANG TU UYEN</strong>
          </div>

          <div className="bank-row">
            <div>
              <span className="bank-row-label" style={{ display: "block" }}>Số tiền thanh toán:</span>
              <span className="hint">(Thanh toán toàn bộ chi phí thuê xe)</span>
            </div>
            <strong style={{ color: "var(--ok)", fontSize: "18px", fontWeight: 800 }}>
              {formatCurrency(totalPaymentAmount)}
            </strong>
          </div>

          <div className="transfer-note-box">
            <strong>Nội dung chuyển khoản:</strong>
            <div className="copy-inline">
              <strong style={{ fontFamily: "Consolas, monospace", fontSize: "14px" }}>{transferContent}</strong>
              <button
                type="button"
                className={copiedField === "nd" ? "btn btn-success btn-sm copy-btn" : "btn btn-outline-secondary btn-sm copy-btn"}
                onClick={() => copyToClipboard(transferContent, "nd")}
              >
                {copiedField === "nd" ? "✓ Đã chép" : "Sao chép"}
              </button>
            </div>
          </div>

          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 24, flexWrap: "wrap", gap: 10 }}>
            <button
              type="button"
              className="btn btn-outline-secondary"
              onClick={() => navigate("/bookings")}
            >
              ← Quay lại đơn thuê của tôi
            </button>
            <div style={{ display: "flex", gap: 10 }}>
              <button
                type="button"
                className="btn btn-warning"
                onClick={() => goToSuccess(payment)}
                style={{ fontWeight: 600, color: "#874d00" }}
              >
                ⚡ Mô phỏng thanh toán (Test nhanh)
              </button>
              <button
                type="button"
                className="btn btn-success"
                onClick={() => goToSuccess(payment)}
                style={{ fontWeight: 700 }}
              >
                ✓ Xác nhận đã thanh toán
              </button>
            </div>
          </div>
        </div>
      </section>
    </>
  );
};
