import { useState, useEffect, useMemo } from "react";
import { useNavigate } from "react-router";
import { paymentService } from "../services/payment";
import { DEFAULT_VEHICLES, calculateRentalPricing, formatCurrency } from "../utils/pricing";

export const PricingCalculator = ({ showroomCar, onProceedToPayment }) => {
  const navigate = useNavigate();

  const vehicles = useMemo(() => {
    if (!showroomCar) return DEFAULT_VEHICLES;
    const rate = Number(showroomCar.dailyRate) > 0 ? Number(showroomCar.dailyRate) : 90000;
    const isLarge = Number(showroomCar.seatCount) >= 7 || String(showroomCar.categoryName || "").toLowerCase().includes("suv");
    const customCar = {
      id: `showroom-${showroomCar.id}`,
      categoryId: showroomCar.categoryId || (isLarge ? DEFAULT_VEHICLES[1].id : DEFAULT_VEHICLES[0].id),
      vehicleId: showroomCar.id,
      name: `${showroomCar.brand} ${showroomCar.modelName}`,
      models: `${showroomCar.categoryName || "Xe cho thuê"} · ${showroomCar.seatCount || 5} chỗ${showroomCar.location ? ` · ${showroomCar.location}` : ""}`,
      imageUrl: showroomCar.imageUrl,
      icon: isLarge ? "🚙" : "🚗",
      baseDailyRate: rate,
      weekendRate: Math.round(rate * 1.25),
      depositAmount: 200000,
      isDefaultTier: false,
    };
    return [customCar, ...DEFAULT_VEHICLES];
  }, [showroomCar]);

  const [selectedVehicleId, setSelectedVehicleId] = useState(vehicles[0].id);
  const [pickupDate, setPickupDate] = useState("2026-11-14T08:00");
  const [returnDate, setReturnDate] = useState("2026-11-16T20:00");
  const [backendResult, setBackendResult] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const currentVehicle = vehicles.find((v) => v.id === selectedVehicleId) || vehicles[0];
  const calcKey = `${currentVehicle.id}|${pickupDate}|${returnDate}`;

  const localResult = useMemo(
    () => calculateRentalPricing(currentVehicle, pickupDate, returnDate),
    [currentVehicle, pickupDate, returnDate]
  );

  useEffect(() => {
    let active = true;
    const categoryId = currentVehicle.categoryId || currentVehicle.id;

    paymentService
      .calculatePrice({
        categoryId,
        pickupDatetime: pickupDate + ":00",
        returnDatetime: returnDate + ":00",
      })
      .then((data) => {
        if (active && data && data.estimatedTotal) {
          setBackendResult({ key: calcKey, data });
        }
      })
      .catch(() => {
        // Fallback sử dụng localResult tính ngày thường/cuối tuần chuẩn xác
      });

    return () => {
      active = false;
    };
  }, [calcKey, currentVehicle, pickupDate, returnDate]);

  const result = backendResult?.key === calcKey ? backendResult.data : localResult;

  const handleCreateOrderAndPay = async () => {
    if (!result) return;
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
          <p className="eyebrow">CAR RENTAL / BÁO GIÁ & ĐẶT XE</p>
          <h1>Thuê xe & Tính cước dự kiến</h1>
          <p className="subtitle">
            Tự động phân tách ngày thường vs <strong>giá cuối tuần (Thứ 7 & CN)</strong>, thuế VAT và tiền cọc minh bạch.
          </p>
        </div>
      </div>

      {/* DANH SÁCH XE LỰA CHỌN */}
      <div className="vehicle-pick-grid" role="radiogroup" aria-label="Chọn dòng xe">
        {vehicles.map((v) => {
          const isSelected = currentVehicle.id === v.id;
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

      {/* FORM CHỌN THỜI GIAN */}
      <section className="pay-card" aria-label="Chọn thời gian thuê">
        <div className="pay-card-head">
          <h3>📅 Thời gian nhận & trả xe</h3>
          <span className="pay-badge brand">Tự động nhận diện Thứ 7 & CN</span>
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

      {/* CHI TIẾT CHI PHÍ & TIỀN CỌC */}
      {result && (
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
              className="pay-cta-btn"
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
