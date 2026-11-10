import { useEffect, useState } from "react";
import { useLocation, useSearchParams, useNavigate, Link } from "react-router";
import { SiteHeader } from "../../home/SiteHeader";
import { SiteFooter } from "../../home/SiteFooter";
import { usePayment } from "../hooks/payment";
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
        // Giữ null để hiển thị thông báo lỗi hoặc trạng thái chưa có hóa đơn
      });

    return () => {
      active = false;
    };
  }, [currentBookingId, stateOrder, fetchPaymentByBooking]);

  const activePayment =
    stateOrder ||
    (currentBookingId && fetchedBooking?.bookingId === currentBookingId ? fetchedBooking.data : null) ||
    (!currentBookingId ? savedOrder : null);

  const handleClearOrder = () => {
    sessionStorage.removeItem("current_rental_payment");
    setSavedOrder(null);
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
              <Link to="/pricing" style={{ fontSize: "13px", fontWeight: 700 }}>
                ← Đổi xe / tính lại giá
              </Link>
              {!currentBookingId && (
                <button
                  type="button"
                  className="btn btn-outline-secondary btn-sm copy-btn"
                  onClick={handleClearOrder}
                >
                  ✕ Hủy đơn này
                </button>
              )}
            </div>
          )}
        </div>

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
                  Bạn chưa chọn xe để đặt. Vui lòng chọn xe từ Trang chủ hoặc vào mục Tính giá &amp; Thuê xe để tạo hóa đơn thanh toán VietQR mới.
                </p>
              </div>
              <div className="result-actions">
                <Link to="/pricing" className="btn btn-danger btn-lg">
                  🚗 Chọn xe &amp; Tính giá ngay →
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
