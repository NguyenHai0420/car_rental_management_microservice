import { useEffect, useMemo, useState } from "react";
import { useLocation, useSearchParams, Link } from "react-router";
import { SiteHeader, SiteFooter } from "../../home/HomePage";
import { usePayment } from "../hooks/payment";
import { PaymentForm } from "../components/PaymentForm";

const DEMO_PAYMENT = {
  id: "e4b3c2a1-0000-0000-0000-000000000001",
  paymentCode: "PAY-20261109-001",
  bookingId: "a1b2c3d4-1111-2222-3333-444455556666",
  customerId: "c7d8e9f0-9999-8888-7777-666655554444",
  status: "PENDING",
  vehicleName: "Xe Sedan (4-5 chỗ) - Mazda CX-5",
  subtotal: 3600000,
  taxAmount: 360000,
  discountAmount: 0,
  penaltyAmount: 0,
  rentalAmount: 3960000,
  depositRequired: 5000000,
  totalAmount: 8960000,
  depositPaid: 0,
  depositRefunded: 0,
  amountPaid: 0,
  amountDue: 8960000,
  currency: "VND",
  dueDate: "Trong 24 giờ",
  notes: "Đơn thuê xe Mazda CX-5 (3 ngày)",
  paymentItems: [
    {
      id: "demo-item-1",
      itemType: "RENTAL_FEE",
      description: "Tiền thuê xe Mazda CX-5 (3 ngày)",
      quantity: 3,
      unitPrice: 1200000,
      amount: 3600000,
    },
    {
      id: "demo-item-2",
      itemType: "TAX",
      description: "Thuế VAT 10%",
      quantity: 1,
      unitPrice: 360000,
      amount: 360000,
    },
    {
      id: "demo-item-3",
      itemType: "DEPOSIT",
      description: "Tiền cọc giữ xe bắt buộc",
      quantity: 1,
      unitPrice: 5000000,
      amount: 5000000,
    },
  ],
};

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
  const [searchParams] = useSearchParams();
  const { loading, error, fetchPaymentByBooking } = usePayment();
  const [fetchedBooking, setFetchedBooking] = useState(null);

  const stateOrder = propOrder || location.state?.customOrder || null;
  const currentBookingId = propBookingId || searchParams.get("bookingId");
  const savedOrder = useMemo(() => readSavedOrder(), []);

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
        // Fallback sử dụng đơn đã lưu hoặc đơn mẫu
      });

    return () => {
      active = false;
    };
  }, [currentBookingId, stateOrder, fetchPaymentByBooking]);

  const activePayment =
    stateOrder ||
    (currentBookingId && fetchedBooking?.bookingId === currentBookingId ? fetchedBooking.data : null) ||
    savedOrder ||
    DEMO_PAYMENT;

  const isRealUserOrder = Boolean(activePayment?.isCustomOrder || (currentBookingId && fetchedBooking?.bookingId === currentBookingId));

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
          <div style={{ display: "flex", gap: "8px", alignItems: "center", flexWrap: "wrap" }}>
            {isRealUserOrder ? (
              <span className="order-pill">
                🚘 Đang thanh toán: {activePayment.vehicleName || `Đơn #${activePayment.paymentCode}`}
              </span>
            ) : (
              <span className="order-pill demo">ℹ️ Đang hiển thị đơn mẫu trải nghiệm</span>
            )}
            <Link to="/pricing" style={{ fontSize: "13px", fontWeight: 700 }}>
              ← Đổi xe / tính lại giá
            </Link>
          </div>
        </div>

        {loading && !activePayment && (
          <p className="notice" role="status">Đang tải thông tin hóa đơn thanh toán...</p>
        )}

        {error && !activePayment && (
          <p className="notice error" role="alert">{error}</p>
        )}

        <PaymentForm payment={activePayment} />
      </main>
      <SiteFooter />
    </div>
  );
};
