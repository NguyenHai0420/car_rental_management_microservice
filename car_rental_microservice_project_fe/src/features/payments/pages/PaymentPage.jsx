import { useEffect, useState } from "react";
import { useLocation, useSearchParams, useNavigate, Link } from "react-router";
import { SiteHeader } from "../../home/SiteHeader";
import { SiteFooter } from "../../home/SiteFooter";
import { usePayment } from "../hooks/payment";
import { paymentService } from "../services/payment";
import { bookingService } from "../../bookings/services/booking";
import { PaymentForm } from "../components/PaymentForm";

function readSavedOrder() {
  const saved = sessionStorage.getItem("current_rental_payment");
  if (!saved) return null;
  try {
    return JSON.parse(saved);
  } catch {
    return null;
  }
}

export const PaymentPage = ({ bookingId: propBookingId, customOrder: propOrder }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { loading, error, fetchPaymentByBooking } = usePayment();
  const [fetchedBooking, setFetchedBooking] = useState(null);
  const [savedOrder, setSavedOrder] = useState(() => readSavedOrder());
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState(null);

  const stateOrder = propOrder || location.state?.customOrder || null;
  const currentBookingId = propBookingId || searchParams.get("bookingId");

  useEffect(() => {
    if (stateOrder || !currentBookingId) return undefined;

    let active = true;
    fetchPaymentByBooking(currentBookingId)
      .then((data) => {
        if (active && data) {
          setFetchedBooking({ bookingId: currentBookingId, data });
        }
      })
      .catch(() => {
        // Nếu chưa có hóa đơn trên payment-service, tự động lấy thông tin từ booking-service để tạo
        bookingService
          .getBookingById(currentBookingId)
          .then(async (booking) => {
            if (!active || !booking) return;
            const total = Number(booking.estimatedTotal || 0);
            const subtotal = Math.round(total / 1.1);
            const tax = total - subtotal;
            try {
              const createdPayment = await paymentService.createPayment({
                bookingId: booking.id,
                customerId: booking.customerId || "00000000-0000-0000-0000-000000000001",
                subtotal,
                taxAmount: tax,
                totalAmount: total,
                depositRequired: 0,
                currency: booking.currency || "VND",
                notes: booking.vehicleInfo || `Đơn thuê xe ${booking.bookingCode}`,
                items: [
                  {
                    itemType: "RENTAL",
                    description: `Chi phí thuê xe trọn gói: ${booking.vehicleInfo || booking.bookingCode}`,
                    quantity: 1,
                    unitPrice: total,
                    amount: total,
                  },
                ],
              });
              if (active && createdPayment) {
                setFetchedBooking({ bookingId: currentBookingId, data: createdPayment });
              }
            } catch {
              if (active) {
                setFetchedBooking({
                  bookingId: currentBookingId,
                  data: {
                    id: `pay-${booking.id.slice(0, 8)}`,
                    bookingId: booking.id,
                    paymentCode: `PAY-${booking.bookingCode.replace(/[^a-zA-Z0-9]/g, "")}`,
                    vehicleName: booking.vehicleInfo,
                    totalAmount: total,
                    rentalAmount: total,
                    subtotal,
                    taxAmount: tax,
                    depositRequired: 0,
                    status: "PENDING",
                  },
                });
              }
            }
          })
          .catch(() => {});
      });

    return () => {
      active = false;
    };
  }, [currentBookingId, stateOrder, fetchPaymentByBooking]);

  const activePayment =
    stateOrder ||
    (currentBookingId && fetchedBooking?.bookingId === currentBookingId ? fetchedBooking.data : null) ||
    (!currentBookingId ? savedOrder : null);

  const handleClearOrder = async () => {
    setCancelError(null);
    const paymentId = activePayment?.id;
    const isRealInvoice = paymentId && !String(paymentId).startsWith("pay-");

    if (isRealInvoice) {
      setCancelling(true);
      try {
        await paymentService.cancelPayment(paymentId);
      } catch (err) {
        setCancelError(
          err.response?.data?.message ||
            "Không hủy được hóa đơn trên máy chủ. Hãy kiểm tra ApiGateway và PaymentService đang chạy."
        );
        setCancelling(false);
        return;
      }
      setCancelling(false);
    }

    sessionStorage.removeItem("current_rental_payment");
    setSavedOrder(null);
    setFetchedBooking(null);
    navigate("/payment", { replace: true, state: null });
  };

  return (
    <div className="site">
      <SiteHeader />
      <main className="payment-main">
        <div className="payment-page-head">
          <div>
            <p className="eyebrow">CAR RENTAL / THANH TOÁN VIETQR</p>
            <h1>Cổng thanh toán thuê xe trực tuyến</h1>
            <p className="subtitle">
              Thanh toán an toàn qua VietinBank Napas247 — Tài khoản <strong>HOANG TU UYEN</strong>
            </p>
          </div>
          {activePayment && (
            <div style={{ display: "flex", gap: "10px", alignItems: "center", flexWrap: "wrap" }}>
              <span className="order-pill">
                🚘 Đang thanh toán: {activePayment.vehicleName || `Đơn #${activePayment.paymentCode}`}
              </span>
              <Link to="/bookings" style={{ fontSize: "13px", fontWeight: 700 }}>
                ← Quay lại đơn thuê của tôi
              </Link>
              {activePayment.status !== "FULLY_PAID" && activePayment.status !== "DEPOSIT_PAID" && (
                <button
                  type="button"
                  className="btn btn-outline-secondary btn-sm copy-btn"
                  disabled={cancelling}
                  onClick={handleClearOrder}
                >
                  {cancelling ? "Đang hủy..." : "✕ Hủy đơn này"}
                </button>
              )}
            </div>
          )}
        </div>

        {cancelError && (
          <p className="notice error" role="alert">{cancelError}</p>
        )}

        {loading && !activePayment && (
          <p className="notice" role="status">Đang tải thông tin hóa đơn thanh toán...</p>
        )}

        {error && !activePayment && (
          <p className="notice error" role="alert">{error}</p>
        )}

        {activePayment ? (
          <PaymentForm payment={activePayment} />
        ) : (
          !loading && (
            <section className="result-card" aria-label="Chưa có hóa đơn thanh toán">
              <div style={{ fontSize: "48px", lineHeight: 1 }} aria-hidden="true">🧾</div>
              <div>
                <p className="eyebrow">CHƯA CÓ ĐƠN ĐẶT XE</p>
                <h2 style={{ fontSize: "22px", marginTop: "6px" }}>Chưa có hóa đơn cần thanh toán</h2>
                <p className="hint" style={{ fontSize: "14px", marginTop: "8px" }}>
                  Bạn chưa có đơn đặt xe cần thanh toán. Vui lòng chọn xe từ Trang chủ để tạo đơn đặt xe mới.
                </p>
              </div>
              <div className="result-actions">
                <Link to="/" className="btn btn-danger btn-lg">
                  🚗 Khám phá xe tại Trang chủ →
                </Link>
                <Link to="/payment/history" className="btn btn-primary btn-lg">
                  📜 Xem lịch sử thanh toán
                </Link>
                <Link to="/" className="btn btn-outline-secondary btn-lg">
                  ← Xem xe tại Trang chủ
                </Link>
              </div>
            </section>
          )
        )}
      </main>
      <SiteFooter />
    </div>
  );
};
