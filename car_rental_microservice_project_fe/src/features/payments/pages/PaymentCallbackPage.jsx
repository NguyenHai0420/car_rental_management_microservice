import { useEffect, useMemo, useState } from "react";
import { useSearchParams, Link } from "react-router";
import { SiteHeader } from "../../home/SiteHeader";
import { SiteFooter } from "../../home/SiteFooter";
import { usePayment } from "../hooks/payment";
import { formatCurrency } from "../utils/pricing";
import { bookingService } from "../../bookings/services/booking";

export const PaymentCallbackPage = () => {
  const [searchParams] = useSearchParams();
  const { verifyCallback, loading } = usePayment();
  const [verifiedData, setVerifiedData] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);

  const instantResult = useMemo(() => {
    const code = searchParams.get("code");
    const status = searchParams.get("status");
    if (code !== "00" && status !== "DEPOSIT_PAID" && status !== "FULLY_PAID") {
      return null;
    }
    const depositParam = searchParams.get("depositPaid");
    const rentalParam = searchParams.get("rentalPaid");
    const amountParam = searchParams.get("amountPaid");
    const vehicleParam = searchParams.get("vehicleName");

    const dep = depositParam ? Number(depositParam) : 200000;
    const rent = rentalParam ? Number(rentalParam) : 308000;
    const amt = amountParam ? Number(amountParam) : dep + rent;

    return {
      paymentCode: searchParams.get("paymentCode") || "PAY-20261109-001",
      depositPaid: dep,
      rentalPaid: rent,
      amountPaid: amt,
      vehicleName: vehicleParam ? decodeURIComponent(vehicleParam) : "Xe Thuê Trực Tuyến",
      status: "FULLY_PAID",
    };
  }, [searchParams]);

  useEffect(() => {
    if (instantResult) return undefined;

    let active = true;
    const params = Object.fromEntries(searchParams.entries());
    const method = params.method || "PAYOS";

    verifyCallback(params, method)
      .then((data) => {
        if (active) setVerifiedData(data);
      })
      .catch((err) => {
        if (active) {
          setErrorMessage(err.response?.data?.message || err.message || "Xác thực giao dịch thất bại");
        }
      });

    return () => {
      active = false;
    };
  }, [instantResult, searchParams, verifyCallback]);

  const result = instantResult || verifiedData;

  useEffect(() => {
    if (result) {
      sessionStorage.removeItem("current_rental_payment");
      const bookingId = searchParams.get("bookingId");
      const paymentCode = searchParams.get("paymentCode") || result?.paymentCode;
      if (bookingId) {
        bookingService.confirmBooking(bookingId, paymentCode).catch(() => {});
      }
    }
  }, [result, searchParams]);

  return (
    <div className="site">
      <SiteHeader />
      <main className="payment-main">
        <section className="result-card" aria-live="polite">
          {loading && !result && (
            <>
              <div style={{ fontSize: "44px" }}>⏳</div>
              <h2>Đang xác thực biến động số dư...</h2>
              <p className="hint" style={{ fontSize: "14px" }}>
                Hệ thống đang đối soát dữ liệu với VietinBank, vui lòng chờ trong giây lát.
              </p>
            </>
          )}

          {result && (
            <>
              <div className="result-icon ok" aria-hidden="true">✓</div>
              <div>
                <p className="eyebrow">CAR RENTAL / XÁC NHẬN THANH TOÁN</p>
                <h2 style={{ color: "var(--ok)", fontSize: "24px", fontWeight: 800, marginTop: "6px" }}>
                  XÁC NHẬN ĐẶT XE THÀNH CÔNG!
                </h2>
                <p className="hint" style={{ fontSize: "14px", marginTop: "8px" }}>
                  Đã nhận thành công toàn bộ chi phí thuê xe vào tài khoản <strong>VietinBank (0376359204 - HOANG TU UYEN)</strong>.
                  Đơn đặt xe của bạn đã hoàn tất thanh toán và được xác nhận chính thức!
                </p>
              </div>

              <div className="bank-table" style={{ width: "100%" }}>
                <div className="bank-row">
                  <span className="bank-row-label">Mã hóa đơn:</span>
                  <strong>#{result.paymentCode}</strong>
                </div>
                {result.vehicleName && (
                  <div className="bank-row">
                    <span className="bank-row-label">Dòng xe đã xác nhận:</span>
                    <strong style={{ color: "var(--brand)" }}>{result.vehicleName}</strong>
                  </div>
                )}
                <div className="bank-row">
                  <span className="bank-row-label">Chi phí thuê xe (gồm VAT):</span>
                  <strong>{formatCurrency(result.amountPaid || result.rentalPaid)}</strong>
                </div>
                <hr className="pay-divider" />
                <div className="bank-row">
                  <strong style={{ color: "var(--ink)" }}>Tổng tiền đã thanh toán:</strong>
                  <strong style={{ color: "var(--ok)", fontSize: "18px", fontWeight: 800 }}>
                    {formatCurrency(result.amountPaid || result.rentalPaid)}
                  </strong>
                </div>
                <div className="bank-row">
                  <span className="bank-row-label">Phương thức:</span>
                  <strong style={{ color: "var(--brand)" }}>Mã VietQR (VietinBank)</strong>
                </div>
                <div className="bank-row">
                  <span className="bank-row-label">Trạng thái đơn:</span>
                  <span className="pay-badge ok">ĐÃ THANH TOÁN ĐỦ — ĐÃ XÁC NHẬN (CONFIRMED)</span>
                </div>
              </div>

              <div className="result-actions">
                <Link to="/payment/history" className="btn btn-primary btn-lg">
                  📜 Xem lịch sử thanh toán
                </Link>
                <Link to="/" className="btn btn-outline-secondary btn-lg">
                  ← Về trang chủ CarRental
                </Link>
                <Link to="/bookings" className="btn btn-danger btn-lg">
                  Xem đơn thuê của tôi →
                </Link>
              </div>
            </>
          )}

          {!loading && !result && errorMessage && (
            <>
              <div className="result-icon error" aria-hidden="true">✕</div>
              <div>
                <h2 style={{ color: "var(--danger)", fontSize: "22px" }}>Xác thực thất bại</h2>
                <p className="hint" style={{ fontSize: "14px", marginTop: "8px" }}>{errorMessage}</p>
              </div>
              <div className="result-actions">
                <Link to="/payment" className="btn btn-danger btn-lg">
                  Quay lại trang thanh toán
                </Link>
              </div>
            </>
          )}
        </section>
      </main>
      <SiteFooter />
    </div>
  );
};
