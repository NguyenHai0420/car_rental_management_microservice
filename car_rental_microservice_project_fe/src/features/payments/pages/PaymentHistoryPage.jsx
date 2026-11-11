import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router";
import { SiteHeader } from "../../home/SiteHeader";
import { SiteFooter } from "../../home/SiteFooter";
import { paymentService } from "../services/payment";
import { formatCurrency } from "../utils/pricing";

const STATUS_META = {
  FULLY_PAID: { label: "✓ Đã thanh toán", cls: "pay-badge ok" },
  DEPOSIT_PAID: { label: "✓ Đã đặt cọc", cls: "pay-badge brand" },
  PARTIALLY_PAID: { label: "Thanh toán một phần", cls: "pay-badge brand" },
  PENDING: { label: "⏳ Chờ thanh toán", cls: "pay-badge warn" },
  REFUND_PENDING: { label: "Đang hoàn cọc", cls: "pay-badge brand" },
  REFUNDED: { label: "↩ Đã hoàn cọc", cls: "pay-badge ok" },
  FAILED: { label: "✕ Thất bại", cls: "pay-badge danger" },
  CANCELLED: { label: "✕ Đã hủy", cls: "pay-badge danger" },
};

const TXN_METHOD_LABELS = {
  BANK_TRANSFER: "Chuyển khoản SePay / VietQR",
  PAYOS: "VietQR / PayOS",
  VNPAY: "VNPay",
  MOMO: "MoMo",
  ZALOPAY: "ZaloPay",
  CASH: "Tiền mặt",
};

const FILTER_OPTIONS = [
  { value: "ALL", label: "Tất cả" },
  { value: "PAID", label: "Đã thanh toán" },
  { value: "PENDING", label: "Chờ thanh toán" },
  { value: "OTHER", label: "Đã hủy" },
];

function formatDateTime(value) {
  if (!value) return "—";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return String(value);
  return parsed.toLocaleString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function getPaidTotal(payment) {
  return Number(payment?.amountPaid || 0) + Number(payment?.depositPaid || 0);
}

function isPaidStatus(status) {
  return status === "FULLY_PAID" || status === "DEPOSIT_PAID" || status === "PARTIALLY_PAID";
}

export const PaymentHistoryPage = () => {
  const navigate = useNavigate();
  const [payments, setPayments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [keyword, setKeyword] = useState("");
  const [reloadNonce, setReloadNonce] = useState(0);
  const [cancellingId, setCancellingId] = useState(null);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    paymentService
      .getPaymentHistory({}, controller.signal)
      .then((data) => {
        if (!active) return;
        setPayments(Array.isArray(data) ? data : []);
        setError(null);
        setLoading(false);
      })
      .catch((err) => {
        if (!active || err.name === "CanceledError" || err.code === "ERR_CANCELED") return;
        setError(
          err.response?.data?.message ||
            "Không tải được lịch sử thanh toán. Hãy kiểm tra ApiGateway và PaymentService đang chạy."
        );
        setLoading(false);
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [reloadNonce]);

  const stats = useMemo(() => {
    const paidOrders = payments.filter((p) => isPaidStatus(p.status));
    const pendingOrders = payments.filter((p) => p.status === "PENDING");
    return {
      totalCount: payments.length,
      paidCount: paidOrders.length,
      pendingCount: pendingOrders.length,
    };
  }, [payments]);

  const filteredPayments = useMemo(() => {
    const q = keyword.trim().toLowerCase();
    return payments.filter((p) => {
      if (statusFilter === "PAID" && !isPaidStatus(p.status)) return false;
      if (statusFilter === "PENDING" && p.status !== "PENDING") return false;
      if (statusFilter === "OTHER" && (isPaidStatus(p.status) || p.status === "PENDING")) return false;

      if (!q) return true;
      const txnMatch = (p.paymentTransactions || []).some(
        (tx) =>
          tx.gatewayTxnId?.toLowerCase().includes(q) ||
          tx.gatewayResponse?.toLowerCase().includes(q)
      );
      return (
        p.paymentCode?.toLowerCase().includes(q) ||
        p.notes?.toLowerCase().includes(q) ||
        txnMatch
      );
    });
  }, [payments, statusFilter, keyword]);

  const handleResumePayment = (payment) => {
    const customOrder = {
      ...payment,
      rentalAmount: Number(payment.totalAmount || 0),
      vehicleName: payment.notes || `Hóa đơn #${payment.paymentCode}`,
    };
    sessionStorage.setItem("current_rental_payment", JSON.stringify(customOrder));
    navigate("/payment", { state: { customOrder } });
  };

  const handleCancelPending = async (payment) => {
    setCancellingId(payment.id);
    try {
      const updated = await paymentService.cancelPayment(payment.id);
      setPayments((prev) =>
        prev.map((item) => (item.id === payment.id ? updated || { ...item, status: "CANCELLED" } : item))
      );
      const savedRaw = sessionStorage.getItem("current_rental_payment");
      if (savedRaw) {
        try {
          const saved = JSON.parse(savedRaw);
          if (saved?.id === payment.id) {
            sessionStorage.removeItem("current_rental_payment");
          }
        } catch {
          // ignore invalid JSON
        }
      }
      setError(null);
    } catch (err) {
      setError(err.response?.data?.message || "Không hủy được hóa đơn. Vui lòng thử lại.");
    } finally {
      setCancellingId(null);
    }
  };

  return (
    <div className="site">
      <SiteHeader />
      <main className="payment-main">
        <div className="payment-page-head">
          <div>
            <p className="eyebrow">CAR RENTAL / LỊCH SỬ GIAO DỊCH</p>
            <h1>Lịch sử thanh toán &amp; chuyển khoản</h1>
            <p className="subtitle">
              Danh sách hóa đơn và các lần khách hàng thanh toán qua VietQR / SePay được lưu trong hệ thống.
            </p>
          </div>
          <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
            <button
              type="button"
              className="btn btn-outline-secondary btn-sm"
              disabled={loading}
              onClick={() => {
                setLoading(true);
                setReloadNonce((n) => n + 1);
              }}
            >
              {loading ? "Đang tải..." : "↻ Làm mới"}
            </button>
            <Link to="/pricing" className="btn btn-danger btn-sm">
              + Đặt thuê &amp; thanh toán mới
            </Link>
          </div>
        </div>

        <section className="history-summary-grid" aria-label="Thống kê thanh toán">
          <div className="history-stat-card">
            <span className="hint" style={{ marginTop: 0 }}>Tổng số hóa đơn</span>
            <strong className="history-stat-value">{stats.totalCount}</strong>
          </div>
          <div className="history-stat-card">
            <span className="hint" style={{ marginTop: 0 }}>Đã thanh toán / đặt cọc</span>
            <strong className="history-stat-value" style={{ color: "var(--ok)" }}>
              {stats.paidCount}
            </strong>
          </div>
          <div className="history-stat-card">
            <span className="hint" style={{ marginTop: 0 }}>Đơn chưa thanh toán</span>
            <strong className="history-stat-value" style={{ color: "var(--warn)" }}>
              {stats.pendingCount}
            </strong>
          </div>
        </section>

        <section className="pay-card" aria-label="Bộ lọc lịch sử thanh toán">
          <div className="pay-card-body">
            <div className="history-toolbar">
              <div className="history-filter-group" role="group" aria-label="Lọc theo trạng thái">
                {FILTER_OPTIONS.map((opt) => (
                  <button
                    key={opt.value}
                    type="button"
                    className={
                      statusFilter === opt.value
                        ? "btn btn-primary btn-sm"
                        : "btn btn-outline-secondary btn-sm"
                    }
                    onClick={() => setStatusFilter(opt.value)}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
              <div style={{ minWidth: "240px", flex: "1 1 260px", maxWidth: "360px" }}>
                <input
                  type="search"
                  className="form-control form-control-sm"
                  placeholder="Tìm mã PAY-..., tên xe, mã giao dịch SePay..."
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  aria-label="Tìm kiếm lịch sử thanh toán"
                />
              </div>
            </div>
          </div>
        </section>

        {loading && payments.length === 0 && (
          <p className="notice" role="status">Đang tải lịch sử thanh toán...</p>
        )}

        {error && (
          <p className="notice error" role="alert">{error}</p>
        )}

        {!loading && !error && filteredPayments.length === 0 && (
          <section className="result-card" aria-label="Không có lịch sử phù hợp">
            <div style={{ fontSize: "42px", lineHeight: 1 }} aria-hidden="true">🗂️</div>
            <div>
              <h2 style={{ fontSize: "20px" }}>
                {payments.length === 0
                  ? "Chưa có lịch sử thanh toán nào"
                  : "Không tìm thấy hóa đơn khớp bộ lọc"}
              </h2>
              <p className="hint" style={{ fontSize: "14px", marginTop: "6px" }}>
                {payments.length === 0
                  ? "Hãy chọn xe tại mục Tính giá & Thuê xe và hoàn tất chuyển khoản VietQR để giao dịch xuất hiện tại đây."
                  : "Thử đổi trạng thái lọc hoặc xóa từ khóa tìm kiếm."}
              </p>
            </div>
            <div className="result-actions">
              {payments.length > 0 ? (
                <button
                  type="button"
                  className="btn btn-outline-secondary"
                  onClick={() => {
                    setStatusFilter("ALL");
                    setKeyword("");
                  }}
                >
                  Xóa bộ lọc
                </button>
              ) : (
                <Link to="/pricing" className="btn btn-danger btn-lg">
                  🚗 Chọn xe &amp; Thanh toán ngay →
                </Link>
              )}
            </div>
          </section>
        )}

        {filteredPayments.map((payment) => {
          const meta = STATUS_META[payment.status] || { label: payment.status, cls: "pay-badge brand" };
          const totalInvoice = Number(payment.totalAmount || 0) + Number(payment.depositRequired || 0);
          const totalPaid = getPaidTotal(payment);
          const txns = payment.paymentTransactions || [];

          return (
            <article key={payment.id} className="pay-card" aria-label={`Hóa đơn ${payment.paymentCode}`}>
              <div className="pay-card-head">
                <div>
                  <h2 style={{ fontSize: "17px" }}>Hóa đơn #{payment.paymentCode}</h2>
                  <p className="hint" style={{ marginTop: "2px", color: "var(--ink-2)", fontWeight: 600 }}>
                    {payment.notes || "Đơn thuê xe trực tuyến"}
                  </p>
                  <p className="hint">
                    Tạo lúc: {formatDateTime(payment.createdAt)}
                    {payment.paidAt && ` · Thanh toán xong: ${formatDateTime(payment.paidAt)}`}
                  </p>
                </div>
                <span className={meta.cls}>{meta.label}</span>
              </div>

              <div className="pay-card-body">
                <div className="pay-row">
                  <span>Tiền thuê xe (gồm VAT):</span>
                  <span>{formatCurrency(payment.totalAmount)}</span>
                </div>
                <div className="pay-row">
                  <span>Tiền cọc giữ xe:</span>
                  <span>{formatCurrency(payment.depositRequired)}</span>
                </div>
                <div className="pay-row" style={{ fontWeight: 700, color: totalPaid > 0 ? "var(--ok)" : "var(--ink)" }}>
                  <span>Tổng đã thanh toán / Tổng hóa đơn:</span>
                  <span>
                    {formatCurrency(totalPaid)} / {formatCurrency(totalInvoice)}
                  </span>
                </div>

                {txns.length > 0 ? (
                  <div className="history-txn-list" aria-label="Chi tiết giao dịch chuyển khoản">
                    <span className="hint" style={{ fontWeight: 700, color: "var(--ink-2)" }}>
                      Giao dịch ghi nhận ({txns.length}):
                    </span>
                    {txns.map((tx) => (
                      <div key={tx.id} className="history-txn-item">
                        <div>
                          <div style={{ fontWeight: 700, color: "var(--ink)" }}>
                            {TXN_METHOD_LABELS[tx.txnMethod] || tx.txnMethod}
                            {" · "}
                            <span style={{ color: tx.status === "SUCCESS" ? "var(--ok)" : "var(--muted)" }}>
                              {tx.status}
                            </span>
                          </div>
                          <div className="hint" style={{ marginTop: "2px" }}>
                            Mã đối soát: <code>{tx.gatewayTxnId || "—"}</code>
                            {" · "}
                            {formatDateTime(tx.processedAt || tx.createdAt)}
                          </div>
                          {tx.gatewayResponse && (
                            <div className="hint" style={{ wordBreak: "break-word" }}>
                              {tx.gatewayResponse}
                            </div>
                          )}
                        </div>
                        <strong style={{ color: "var(--ok)", fontSize: "15px" }}>
                          +{formatCurrency(tx.amount)}
                        </strong>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="hint">Chưa có giao dịch chuyển khoản nào được ghi nhận cho hóa đơn này.</p>
                )}

                {payment.status === "PENDING" && (
                  <div style={{ display: "flex", justifyContent: "flex-end", gap: "8px", flexWrap: "wrap", paddingTop: "4px" }}>
                    <button
                      type="button"
                      className="btn btn-outline-secondary btn-sm"
                      disabled={cancellingId === payment.id}
                      onClick={() => handleCancelPending(payment)}
                    >
                      {cancellingId === payment.id ? "Đang hủy..." : "✕ Hủy đơn"}
                    </button>
                    <button
                      type="button"
                      className="btn btn-danger btn-sm"
                      onClick={() => handleResumePayment(payment)}
                    >
                      Thanh toán VietQR đơn này →
                    </button>
                  </div>
                )}
              </div>
            </article>
          );
        })}
      </main>
      <SiteFooter />
    </div>
  );
};

