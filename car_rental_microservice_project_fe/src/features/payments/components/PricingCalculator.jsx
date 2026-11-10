import { useState, useEffect, useMemo } from "react";
import { useNavigate, Link } from "react-router";
import { listShowroom } from "../../vehicles/services/vehicle";
import { paymentService } from "../services/payment";
import {
  DEFAULT_VEHICLES,
  calculateRentalPricing,
  formatCurrency,
  mapShowroomCarToPricingVehicle,
  mapTierToPricingVehicle,
} from "../utils/pricing";

export const PricingCalculator = ({ showroomCar, onProceedToPayment }) => {
  const navigate = useNavigate();

  const [showroomList, setShowroomList] = useState([]);
  const [tiers, setTiers] = useState([]);
  const [loadingVehicles, setLoadingVehicles] = useState(true);
  const [reloadNonce, setReloadNonce] = useState(0);

  const [selectedVehicleId, setSelectedVehicleId] = useState(
    showroomCar ? `showroom-${showroomCar.id}` : null
  );
  const [pickupDate, setPickupDate] = useState("2026-11-14T08:00");
  const [returnDate, setReturnDate] = useState("2026-11-16T20:00");
  const [backendResult, setBackendResult] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  // Tải danh sách xe thật từ vehicle-service và bảng giá đang hiệu lực từ payment-service
  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    Promise.allSettled([
      listShowroom({ size: 48 }, controller.signal),
      paymentService.listActiveTiers(controller.signal),
    ]).then(([carsRes, tiersRes]) => {
      if (!active) return;
      if (carsRes.status === "fulfilled" && Array.isArray(carsRes.value?.content)) {
        setShowroomList(carsRes.value.content);
      } else {
        setShowroomList([]);
      }
      if (tiersRes.status === "fulfilled" && Array.isArray(tiersRes.value)) {
        setTiers(tiersRes.value);
      } else {
        setTiers([]);
      }
      setLoadingVehicles(false);
    });

    return () => {
      active = false;
      controller.abort();
    };
  }, [reloadNonce]);

  const tierByCategory = useMemo(() => {
    const map = {};
    for (const tier of tiers) {
      if (tier?.categoryId && !map[tier.categoryId]) {
        map[tier.categoryId] = tier;
      }
    }
    return map;
  }, [tiers]);

  const vehicles = useMemo(() => {
    const list = [];
    const seenIds = new Set();

    if (showroomCar) {
      const picked = mapShowroomCarToPricingVehicle(showroomCar, tierByCategory);
      list.push(picked);
      seenIds.add(picked.id);
    }

    for (const car of showroomList) {
      const mapped = mapShowroomCarToPricingVehicle(car, tierByCategory);
      if (!seenIds.has(mapped.id)) {
        list.push(mapped);
        seenIds.add(mapped.id);
      }
    }

    if (list.length === 0 && tiers.length > 0) {
      return tiers.map(mapTierToPricingVehicle);
    }

    return list.length > 0 ? list : DEFAULT_VEHICLES;
  }, [showroomCar, showroomList, tiers, tierByCategory]);

  const currentVehicle = vehicles.find((v) => v.id === selectedVehicleId) || vehicles[0] || null;
  const calcKey = currentVehicle ? `${currentVehicle.id}|${pickupDate}|${returnDate}` : null;

  const localResult = useMemo(
    () => (currentVehicle ? calculateRentalPricing(currentVehicle, pickupDate, returnDate) : null),
    [currentVehicle, pickupDate, returnDate]
  );

  useEffect(() => {
    if (!currentVehicle || !calcKey) return undefined;

    let active = true;
    const categoryId = currentVehicle.categoryId || currentVehicle.id;

    paymentService
      .calculatePrice({
        categoryId,
        pickupDatetime: pickupDate + ":00",
        returnDatetime: returnDate + ":00",
        dailyRate: currentVehicle.baseDailyRate,
        categoryName: currentVehicle.categoryName || currentVehicle.name,
      })
      .then((data) => {
        if (active && data && data.estimatedTotal != null) {
          setBackendResult({ key: calcKey, data });
        }
      })
      .catch(() => {
        // Giữ kết quả localResult tính ngày thường/cuối tuần chuẩn xác
      });

    return () => {
      active = false;
    };
  }, [calcKey, currentVehicle, pickupDate, returnDate]);

  const result = calcKey && backendResult?.key === calcKey ? backendResult.data : localResult;

  const handleCreateOrderAndPay = async () => {
    if (!result || !currentVehicle) return;
    setSubmitting(true);

    const uniquePaymentCode = `PAY-${new Date().toISOString().slice(0, 10).replace(/-/g, "")}-${Math.floor(100 + Math.random() * 900)}`;
    const randomHex = Math.floor(100000000000 + Math.random() * 899999999999).toString();
    const generatedBookingId = `a1b2c3d4-1111-2222-3333-${randomHex}`;
    const vehicleName = currentVehicle.name;
    const totalInvoiceAmount = Number(result.estimatedTotal) + Number(result.depositRequired);

    let customOrder = {
      id: `pay-${Date.now()}`,
      paymentCode: uniquePaymentCode,
      bookingId: generatedBookingId,
      customerId: "c7d8e9f0-9999-8888-7777-666655554444",
      status: "PENDING",
      vehicleName,
      vehicleId: currentVehicle.vehicleId || null,
      isCustomOrder: true,
      subtotal: result.subtotal,
      taxAmount: result.taxAmount,
      discountAmount: result.discountAmount || 0,
      penaltyAmount: 0,
      rentalAmount: result.estimatedTotal,
      depositRequired: result.depositRequired,
      totalAmount: totalInvoiceAmount,
      depositPaid: 0,
      depositRefunded: 0,
      amountPaid: 0,
      amountDue: totalInvoiceAmount,
      currency: "VND",
      dueDate: "Trong 24 giờ",
      notes: `Đơn thuê ${vehicleName} (${result.rentalDays} ngày)`,
      paymentItems: [
        ...(result.breakdownItems || []).map((b, idx) => ({
          id: `item-rent-${idx}`,
          itemType: "RENTAL_FEE",
          description: b.description,
          quantity: 1,
          unitPrice: b.amount,
          amount: b.amount,
        })),
        {
          id: "item-tax",
          itemType: "TAX",
          description: "Thuế VAT (10%)",
          quantity: 1,
          unitPrice: result.taxAmount,
          amount: result.taxAmount,
        },
        {
          id: "item-deposit",
          itemType: "DEPOSIT",
          description: "Tiền cọc giữ xe bắt buộc",
          quantity: 1,
          unitPrice: result.depositRequired,
          amount: result.depositRequired,
        },
      ],
    };

    try {
      const created = await paymentService.createPayment({
        bookingId: generatedBookingId,
        customerId: "c7d8e9f0-9999-8888-7777-666655554444",
        subtotal: result.subtotal,
        taxAmount: result.taxAmount,
        totalAmount: result.estimatedTotal,
        depositRequired: result.depositRequired,
        notes: customOrder.notes,
      });
      if (created?.id) {
        customOrder = {
          ...customOrder,
          id: created.id,
          paymentCode: created.paymentCode || customOrder.paymentCode,
          bookingId: created.bookingId || customOrder.bookingId,
        };
      }
    } catch {
      // Backend có thể chưa bật, vẫn tiếp tục sang thanh toán VietQR mượt mà
    }

    sessionStorage.setItem("current_rental_payment", JSON.stringify(customOrder));

    if (onProceedToPayment) {
      onProceedToPayment(customOrder);
    } else {
      navigate("/payment", { state: { customOrder } });
    }
    setSubmitting(false);
  };

  return (
    <>
      <div className="payment-page-head">
        <div>
          <p className="eyebrow">CAR RENTAL / BÁO GIÁ &amp; ĐẶT XE</p>
          <h1>Thuê xe &amp; Tính cước dự kiến</h1>
          <p className="subtitle">
            Tự động phân tách ngày thường vs <strong>giá cuối tuần (Thứ 7 &amp; CN)</strong>, thuế VAT và tiền cọc minh bạch.
          </p>
        </div>
      </div>

      {loadingVehicles && vehicles.length === 0 && (
        <p className="notice" role="status">Đang tải danh sách xe từ hệ thống...</p>
      )}

      {!loadingVehicles && vehicles.length === 0 && (
        <section className="result-card" aria-label="Chưa có dữ liệu xe">
          <div style={{ fontSize: "48px", lineHeight: 1 }} aria-hidden="true">🚘</div>
          <div>
            <p className="eyebrow">CHƯA TẢI ĐƯỢC DANH SÁCH XE</p>
            <h2 style={{ fontSize: "22px", marginTop: "6px" }}>Chưa có xe sẵn sàng từ máy chủ</h2>
            <p className="hint" style={{ fontSize: "14px", marginTop: "8px" }}>
              Hãy bật <code>vehicle-service</code> hoặc <code>payment-service</code> (hoặc chọn một xe có sẵn tại Showroom trang chủ) để tính giá thuê.
            </p>
          </div>
          <div className="result-actions">
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => {
                setLoadingVehicles(true);
                setReloadNonce((n) => n + 1);
              }}
            >
              ↻ Thử tải lại
            </button>
            <Link to="/" className="btn btn-outline-secondary">
              ← Về Showroom trang chủ
            </Link>
          </div>
        </section>
      )}

      {/* DANH SÁCH XE TỪ DATABASE */}
      {vehicles.length > 0 && (
        <div className="vehicle-pick-grid" role="radiogroup" aria-label="Chọn dòng xe">
          {vehicles.map((v) => {
            const isSelected = currentVehicle?.id === v.id;
            return (
              <div
                key={v.id}
                role="radio"
                aria-checked={isSelected}
                tabIndex={0}
                className="vehicle-pick-card"
                data-selected={isSelected}
                onClick={() => setSelectedVehicleId(v.id)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    setSelectedVehicleId(v.id);
                  }
                }}
              >
                {isSelected && <span className="pick-badge">✓ ĐANG CHỌN</span>}

                {v.imageUrl ? (
                  <div className="pick-photo">
                    <img src={v.imageUrl} alt={v.name} />
                  </div>
                ) : (
                  <div style={{ fontSize: "34px", lineHeight: 1 }}>{v.icon}</div>
                )}

                <div>
                  <h3 style={{ fontSize: "17px", fontWeight: 800, color: isSelected ? "var(--brand)" : "var(--ink)" }}>
                    {v.name}
                  </h3>
                  <p className="hint" style={{ marginTop: "3px" }}>{v.models}</p>
                </div>

                <div className="pick-meta">
                  <div className="pick-meta-row">
                    <span>Giá ngày thường:</span>
                    <strong>{formatCurrency(v.baseDailyRate)}/ngày</strong>
                  </div>
                  <div className="pick-meta-row weekend">
                    <span>🔥 Cuối tuần (T7, CN):</span>
                    <strong>{formatCurrency(v.weekendRate)}/ngày</strong>
                  </div>
                  <div className="pick-deposit-chip">
                    <span>Tiền cọc giữ xe:</span>
                    <strong>{formatCurrency(v.depositAmount)}</strong>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* FORM CHỌN THỜI GIAN */}
      {currentVehicle && (
        <section className="pay-card" aria-label="Chọn thời gian thuê">
          <div className="pay-card-head">
            <h3>📅 Thời gian nhận &amp; trả xe</h3>
            <span className="pay-badge brand">Tự động nhận diện Thứ 7 &amp; CN</span>
          </div>
          <div className="pay-card-body">
            <div className="pay-date-grid">
              <label>
                Thời gian nhận xe
                <input
                  type="datetime-local"
                  value={pickupDate}
                  onChange={(e) => setPickupDate(e.target.value)}
                />
              </label>
              <label>
                Thời gian trả xe
                <input
                  type="datetime-local"
                  value={returnDate}
                  onChange={(e) => setReturnDate(e.target.value)}
                />
              </label>
            </div>
          </div>
        </section>
      )}

      {/* CHI TIẾT CHI PHÍ & TIỀN CỌC */}
      {currentVehicle && result && (
        <section className="pay-card accent" aria-label="Bảng chi phí đơn thuê">
          <div className="pay-card-head">
            <div>
              <h3>📋 Bảng chi phí: {currentVehicle.name}</h3>
              <p className="hint">
                Thời gian thuê: <strong>{result.rentalDays} ngày</strong> ({pickupDate.replace("T", " ")} → {returnDate.replace("T", " ")})
              </p>
            </div>
            <span className="pay-badge ok">Xe sẵn sàng</span>
          </div>

          <div className="pay-card-body">
            <div className="pay-row">
              <strong>Tiền thuê trước thuế ({result.rentalDays} ngày):</strong>
              <strong>{formatCurrency(result.subtotal)}</strong>
            </div>

            {result.breakdownItems && result.breakdownItems.length > 0 && (
              <div className="pay-breakdown">
                {result.breakdownItems.map((item, idx) => {
                  const isWeekend = item.isWeekend || item.description?.toLowerCase().includes("cuối tuần");
                  return (
                    <div key={idx} className={`pay-breakdown-row${isWeekend ? " weekend" : ""}`}>
                      <span>{isWeekend ? "🔥 " : "• "}{item.description}</span>
                      <strong>{formatCurrency(item.amount)}</strong>
                    </div>
                  );
                })}
              </div>
            )}

            <div className="pay-row">
              <span>Thuế giá trị gia tăng (VAT 10%):</span>
              <span>{formatCurrency(result.taxAmount)}</span>
            </div>

            <div className="pay-row" style={{ color: "var(--brand)", fontWeight: 700 }}>
              <span>Tổng tiền thuê xe ({result.rentalDays} ngày đã gồm VAT):</span>
              <strong>{formatCurrency(result.estimatedTotal)}</strong>
            </div>

            <div className="pay-deposit-box">
              <div>
                <strong style={{ display: "block" }}>Tiền cọc giữ xe bắt buộc</strong>
                <span style={{ fontSize: "12px" }}>(Sẽ được hoàn trả lại sau khi thanh lý hợp đồng trả xe)</span>
              </div>
              <strong style={{ fontSize: "17px" }}>{formatCurrency(result.depositRequired)}</strong>
            </div>

            <hr className="pay-divider" />

            <div className="pay-total-row">
              <div>
                <strong style={{ fontSize: "16px", display: "block" }}>TỔNG CỘNG HÓA ĐƠN (Thuê + Cọc):</strong>
                <span className="hint">Thanh toán chung 1 lần qua VietQR VietinBank</span>
              </div>
              <strong className="pay-total-amount">
                {formatCurrency(Number(result.estimatedTotal) + Number(result.depositRequired))}
              </strong>
            </div>

            <button
              type="button"
              className="btn btn-danger btn-lg pay-cta-btn"
              disabled={submitting}
              onClick={handleCreateOrderAndPay}
            >
              {submitting
                ? "Đang chuẩn bị đơn thanh toán..."
                : `Đặt xe & Thanh toán hóa đơn (${formatCurrency(Number(result.estimatedTotal) + Number(result.depositRequired))}) →`}
            </button>
          </div>
        </section>
      )}
    </>
  );
};
