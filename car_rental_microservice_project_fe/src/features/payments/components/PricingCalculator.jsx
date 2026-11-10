import React, { useState, useEffect } from "react";
import { paymentService } from "../services/payment";

const VEHICLES = [
  {
    id: "11111111-1111-1111-1111-111111111111",
    name: "Xe Sedan (4 - 5 Chỗ)",
    models: "Mazda 3 / Toyota Vios / Kia Cerato",
    capacity: "4 - 5 chỗ ngồi",
    icon: "🚗",
    baseDailyRate: 800000,
    weekendRate: 1000000,
    depositAmount: 5000000,
    features: "Tiết kiệm xăng, số tự động, điều hòa 2 vùng",
  },
  {
    id: "22222222-2222-2222-2222-222222222222",
    name: "Xe SUV (7 Chỗ)",
    models: "Toyota Fortuner / Ford Everest / Hyundai SantaFe",
    capacity: "7 chỗ ngồi rộng rãi",
    icon: "🚙",
    baseDailyRate: 1200000,
    weekendRate: 1500000,
    depositAmount: 10000000,
    features: "Gầm cao vượt địa hình, cốp hành lý lớn cho gia đình",
  },
];

export const calculateRentalPricing = (vehicle, pickupDateStr, returnDateStr) => {
  const start = new Date(pickupDateStr);
  const end = new Date(returnDateStr);

  // Tính số giờ chênh lệch thực tế
  const diffMs = Math.max(0, end - start);
  const totalHours = Math.max(1, Math.round(diffMs / (1000 * 60 * 60)));
  // Làm tròn số ngày thuê (nếu lấn qua mốc giờ thì tính thêm 1 ngày như chuẩn quốc tế)
  const rentalDays = Math.max(1, Math.ceil(totalHours / 24));

  let weekdayCount = 0;
  let weekendCount = 0;

  // Quét từng ngày thuê để phân tách chính xác ngày thường vs ngày cuối tuần (Thứ 7 & Chủ Nhật)
  for (let i = 0; i < rentalDays; i++) {
    const d = new Date(start);
    d.setDate(start.getDate() + i);
    const dayOfWeek = d.getDay(); // 0 = Chủ Nhật, 6 = Thứ Bảy
    if (dayOfWeek === 0 || dayOfWeek === 6) {
      weekendCount++;
    } else {
      weekdayCount++;
    }
  }

  const weekdayRate = vehicle.baseDailyRate;
  const weekendRate = vehicle.weekendRate || vehicle.baseDailyRate;

  const weekdayTotal = weekdayCount * weekdayRate;
  const weekendTotal = weekendCount * weekendRate;
  const subtotal = weekdayTotal + weekendTotal;
  const taxAmount = Math.round(subtotal * 0.1);
  const estimatedTotal = subtotal + taxAmount;
  const depositRequired = vehicle.depositAmount;

  const formatVnd = (num) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(num || 0);

  const breakdownItems = [];
  if (weekdayCount > 0) {
    breakdownItems.push({
      description: `Giá ngày thường (${weekdayCount} ngày x ${formatVnd(weekdayRate)})`,
      amount: weekdayTotal,
      isWeekend: false,
    });
  }
  if (weekendCount > 0) {
    breakdownItems.push({
      description: `Giá ngày cuối tuần Thứ 7 & CN (${weekendCount} ngày x ${formatVnd(weekendRate)})`,
      amount: weekendTotal,
      isWeekend: true,
    });
  }

  return {
    categoryId: vehicle.id,
    tierName: vehicle.name,
    rentalDays,
    weekdayCount,
    weekendCount,
    baseDailyRate: weekdayRate,
    weekendRate,
    subtotal,
    taxAmount,
    discountAmount: 0,
    estimatedTotal,
    depositRequired,
    currency: "VND",
    breakdownItems,
  };
};

export const PricingCalculator = ({ onProceedToPayment }) => {
  const [selectedCategory, setSelectedCategory] = useState(VEHICLES[1].id); // Mặc định SUV 7 chỗ
  const [pickupDate, setPickupDate] = useState("2026-11-14T08:00");
  const [returnDate, setReturnDate] = useState("2026-11-16T20:00");
  const [result, setResult] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const formatCurrency = (amount) => {
    return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount || 0);
  };

  const currentVehicle = VEHICLES.find((v) => v.id === selectedCategory) || VEHICLES[1];

  const calculateForCategory = async (catId, pDate, rDate) => {
    const vehicle = VEHICLES.find((v) => v.id === catId) || VEHICLES[0];
    
    // 1. Tính toán chuẩn xác theo thuật toán phân tách ngày cuối tuần & ngày thường
    const localCalculated = calculateRentalPricing(vehicle, pDate, rDate);
    setResult(localCalculated);

    // 2. Nếu backend bật, gọi đồng bộ thêm từ backend
    try {
      const data = await paymentService.calculatePrice({
        categoryId: catId,
        pickupDatetime: pDate + ":00",
        returnDatetime: rDate + ":00",
      });
      if (data && data.estimatedTotal) {
        setResult(data);
      }
    } catch (err) {
      // Dùng kết quả localCalculated đã phân tách cuối tuần chuẩn xác
    }
  };

  // Tự động tính toán lại ngay lập tức khi đổi xe hoặc đổi ngày nhận/trả
  useEffect(() => {
    calculateForCategory(selectedCategory, pickupDate, returnDate);
  }, [selectedCategory, pickupDate, returnDate]);

  const handleCreateOrderAndPay = async () => {
    if (!result) return;
    setSubmitting(true);

    const uniquePaymentCode = `PAY-${new Date().toISOString().slice(0, 10).replace(/-/g, "")}-${Math.floor(100 + Math.random() * 900)}`;
    const uniqueBookingId = `bk-${Date.now()}`;
    const vehicleName = currentVehicle.name;

    const totalInvoiceAmount = result.estimatedTotal + result.depositRequired;

    const customOrder = {
      id: `pay-${Date.now()}`,
      paymentCode: uniquePaymentCode,
      bookingId: uniqueBookingId,
      customerId: "c7d8e9f0-9999-8888-7777-666655554444",
      status: "PENDING",
      vehicleName: vehicleName,
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
      notes: `Đơn thuê ${vehicleName} (${result.rentalDays} ngày: ${result.weekdayCount || 0} ngày thường, ${result.weekendCount || 0} ngày cuối tuần)`,
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
          description: "Thuế VAT 10%",
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

    // Lưu vào sessionStorage để trang Thanh toán lấy ra hiển thị
    sessionStorage.setItem("current_rental_payment", JSON.stringify(customOrder));

    // Thử gửi lên backend nếu có kết nối
    try {
      await paymentService.createPayment({
        bookingId: "a1b2c3d4-1111-2222-3333-" + Math.floor(100000000000 + Math.random() * 900000000000),
        customerId: "c7d8e9f0-9999-8888-7777-666655554444",
        subtotal: result.subtotal,
        taxAmount: result.taxAmount,
        totalAmount: result.estimatedTotal,
        depositRequired: result.depositRequired,
        notes: customOrder.notes,
      });
    } catch (e) {
      // Backend có thể chưa chạy, client fallback mượt mà
    }

    if (onProceedToPayment) {
      onProceedToPayment(customOrder);
    } else {
      window.location.href = "/payment";
    }
  };

  return (
    <div style={{ maxWidth: "780px", margin: "0 auto", padding: "8px", fontFamily: "Segoe UI, -apple-system, sans-serif" }}>
      {/* Tiêu đề trang Thuê Xe */}
      <div style={{ textAlign: "center", marginBottom: "24px" }}>
        <h1 style={{ fontSize: "26px", fontWeight: "800", color: "#0f172a", margin: "0 0 6px 0" }}>
          🚗 Đặt & Thuê Xe Trực Tuyến
        </h1>
        <p style={{ color: "#64748b", margin: 0, fontSize: "15px" }}>
          Hệ thống tự động tính cước ngày thường vs <strong>giá cuối tuần (Thứ 7 & CN)</strong> và tiền cọc minh bạch
        </p>
      </div>

      {/* DANH SÁCH 2 XE: SEDAN & SUV */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "22px" }}>
        {VEHICLES.map((v) => {
          const isSelected = selectedCategory === v.id;
          return (
            <div
              key={v.id}
              onClick={() => setSelectedCategory(v.id)}
              style={{
                border: isSelected ? "2px solid #2563eb" : "1px solid #cbd5e1",
                background: isSelected ? "#eff6ff" : "#ffffff",
                borderRadius: "16px",
                padding: "20px",
                cursor: "pointer",
                boxShadow: isSelected ? "0 8px 24px rgba(37,99,235,0.18)" : "0 2px 8px rgba(0,0,0,0.04)",
                transition: "all 0.2s ease-in-out",
                position: "relative",
              }}
            >
              {isSelected && (
                <span style={{
                  position: "absolute",
                  top: "12px",
                  right: "12px",
                  background: "#2563eb",
                  color: "#fff",
                  fontSize: "11px",
                  fontWeight: "700",
                  padding: "3px 8px",
                  borderRadius: "999px",
                }}>
                  ✓ ĐANG CHỌN
                </span>
              )}

              <div style={{ fontSize: "36px", marginBottom: "8px" }}>{v.icon}</div>
              <h3 style={{ margin: "0 0 4px 0", fontSize: "18px", color: isSelected ? "#1e40af" : "#0f172a", fontWeight: "800" }}>
                {v.name}
              </h3>
              <p style={{ margin: "0 0 10px 0", fontSize: "13px", color: "#64748b" }}>
                {v.models}
              </p>

              <div style={{ borderTop: "1px dashed #e2e8f0", paddingTop: "10px", marginTop: "10px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: "13px", marginBottom: "4px" }}>
                  <span style={{ color: "#64748b" }}>Giá ngày thường:</span>
                  <strong style={{ color: "#0f172a" }}>{formatCurrency(v.baseDailyRate)}/ngày</strong>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: "13px", marginBottom: "6px" }}>
                  <span style={{ color: "#b45309", fontWeight: "600" }}>🔥 Giá cuối tuần (T7, CN):</span>
                  <strong style={{ color: "#b45309" }}>{formatCurrency(v.weekendRate)}/ngày</strong>
                </div>
                <div style={{
                  display: "flex",
                  justifyContent: "space-between",
                  fontSize: "13px",
                  background: isSelected ? "#dbeafe" : "#f1f5f9",
                  padding: "6px 8px",
                  borderRadius: "6px",
                  color: "#1e3a8a",
                  fontWeight: "600",
                }}>
                  <span>Tiền cọc giữ xe:</span>
                  <strong style={{ color: "#1e40af" }}>{formatCurrency(v.depositAmount)}</strong>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* FORM CHỌN THỜI GIAN THUÊ */}
      <div style={{
        background: "#ffffff",
        borderRadius: "16px",
        boxShadow: "0 4px 20px rgba(0,0,0,0.06)",
        border: "1px solid #e2e8f0",
        padding: "22px",
        marginBottom: "22px",
      }}>
        <h3 style={{ margin: "0 0 14px 0", fontSize: "16px", color: "#0f172a", fontWeight: "700" }}>
          📅 Thời Gian Thuê Xe (Tự động nhận diện Thứ Bảy & Chủ Nhật)
        </h3>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "14px" }}>
          <div>
            <label style={{ display: "block", fontSize: "13px", fontWeight: "600", color: "#334155", marginBottom: "6px" }}>
              Thời gian nhận xe:
            </label>
            <input
              type="datetime-local"
              value={pickupDate}
              onChange={(e) => setPickupDate(e.target.value)}
              style={{
                width: "100%",
                padding: "10px 12px",
                borderRadius: "8px",
                border: "1px solid #cbd5e1",
                fontSize: "14px",
                boxSizing: "border-box",
              }}
            />
          </div>
          <div>
            <label style={{ display: "block", fontSize: "13px", fontWeight: "600", color: "#334155", marginBottom: "6px" }}>
              Thời gian trả xe:
            </label>
            <input
              type="datetime-local"
              value={returnDate}
              onChange={(e) => setReturnDate(e.target.value)}
              style={{
                width: "100%",
                padding: "10px 12px",
                borderRadius: "8px",
                border: "1px solid #cbd5e1",
                fontSize: "14px",
                boxSizing: "border-box",
              }}
            />
          </div>
        </div>
      </div>

      {/* CHI TIẾT ĐƠN HÀNG VÀ TIỀN CỌC DỰ KIẾN */}
      {result && (
        <div style={{
          background: "#ffffff",
          borderRadius: "16px",
          boxShadow: "0 6px 25px rgba(0,0,0,0.06)",
          border: "2px solid #3b82f6",
          padding: "24px",
        }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "14px" }}>
            <div>
              <h3 style={{ margin: "0 0 4px 0", fontSize: "18px", color: "#0f172a", fontWeight: "800" }}>
                📋 Bảng Chi Phí Đơn Thuê: {currentVehicle.name}
              </h3>
              <span style={{ fontSize: "13px", color: "#64748b" }}>
                Thời gian thuê: <strong>{result.rentalDays} ngày</strong> (từ {pickupDate.replace("T", " ")} đến {returnDate.replace("T", " ")})
              </span>
            </div>
            <span style={{
              background: "#dcfce7",
              color: "#15803d",
              padding: "5px 12px",
              borderRadius: "999px",
              fontWeight: "700",
              fontSize: "12px",
            }}>
              Có xe sẵn sàng
            </span>
          </div>

          <div style={{
            background: "#f8fafc",
            borderRadius: "12px",
            padding: "16px 18px",
            display: "flex",
            flexDirection: "column",
            gap: "10px",
            fontSize: "14px",
            border: "1px solid #e2e8f0",
            marginBottom: "20px",
          }}>
            {/* Tổng tiền thuê trước thuế */}
            <div style={{ display: "flex", justifyContent: "space-between", color: "#0f172a", fontWeight: "700" }}>
              <span>Tiền thuê trước thuế ({result.rentalDays} ngày):</span>
              <strong style={{ fontSize: "15px" }}>{formatCurrency(result.subtotal)}</strong>
            </div>

            {/* BẢNG BÓC TÁCH CHI TIẾT NGÀY THƯỜNG VÀ CUỐI TUẦN */}
            {result.breakdownItems && result.breakdownItems.length > 0 && (
              <div style={{
                background: "#ffffff",
                border: "1px solid #e2e8f0",
                borderRadius: "8px",
                padding: "8px 12px",
                display: "flex",
                flexDirection: "column",
                gap: "6px",
              }}>
                {result.breakdownItems.map((item, idx) => (
                  <div
                    key={idx}
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      fontSize: "13px",
                      color: item.isWeekend ? "#b45309" : "#475569",
                      fontWeight: item.isWeekend ? "700" : "500",
                      background: item.isWeekend ? "#fef3c7" : "transparent",
                      padding: item.isWeekend ? "4px 8px" : "2px 4px",
                      borderRadius: "6px",
                    }}
                  >
                    <span>{item.isWeekend ? "🔥 " : "• "}{item.description}</span>
                    <span>{formatCurrency(item.amount)}</span>
                  </div>
                ))}
              </div>
            )}

            <div style={{ display: "flex", justifyContent: "space-between", color: "#475569" }}>
              <span>Thuế giá trị gia tăng (VAT 10%):</span>
              <span>{formatCurrency(result.taxAmount)}</span>
            </div>

            <hr style={{ border: "none", borderTop: "1px dashed #cbd5e1", margin: "4px 0" }} />
            
            {/* Tổng tiền thuê xe sau thuế */}
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: "14px", color: "#1e40af" }}>
              <span>Tổng tiền thuê xe ({result.rentalDays} ngày đã gồm VAT):</span>
              <strong style={{ fontSize: "15px" }}>
                {formatCurrency(result.estimatedTotal)}
              </strong>
            </div>

            {/* Tiền cọc giữ xe bắt buộc */}
            <div style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              fontSize: "14px",
              color: "#15803d",
              background: "#f0fdf4",
              padding: "10px 14px",
              borderRadius: "8px",
              border: "1px solid #bbf7d0",
              marginTop: "4px",
            }}>
              <div>
                <strong>Tiền cọc giữ xe bắt buộc:</strong>
                <span style={{ display: "block", fontSize: "12px", color: "#15803d" }}>(Sẽ hoàn trả khi trả xe)</span>
              </div>
              <strong style={{ fontSize: "16px" }}>
                {formatCurrency(result.depositRequired)}
              </strong>
            </div>

            <hr style={{ border: "none", borderTop: "2px dashed #cbd5e1", margin: "10px 0" }} />

            {/* TỔNG SỐ TIỀN THANH TOÁN TOÀN BỘ ĐƠN HÀNG */}
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div>
                <strong style={{ fontSize: "16px", color: "#0f172a", display: "block" }}>
                  TỔNG CỘNG HÓA ĐƠN (Thuê + Cọc):
                </strong>
                <span style={{ fontSize: "12px", color: "#64748b" }}>
                  Thanh toán chung 1 lần qua VietQR
                </span>
              </div>
              <strong style={{ color: "#16a34a", fontSize: "20px", fontWeight: "900" }}>
                {formatCurrency(result.estimatedTotal + result.depositRequired)}
              </strong>
            </div>
          </div>

          {/* NÚT TIẾN HÀNH THANH TOÁN ĐƠN THUÊ NÀY */}
          <button
            type="button"
            disabled={submitting}
            onClick={handleCreateOrderAndPay}
            style={{
              width: "100%",
              padding: "15px",
              background: "#16a34a",
              color: "#fff",
              border: "none",
              borderRadius: "12px",
              fontWeight: "800",
              fontSize: "16px",
              cursor: "pointer",
              boxShadow: "0 6px 18px rgba(22,163,74,0.35)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: "8px",
            }}
          >
            {submitting ? "Đang chuẩn bị đơn thanh toán..." : `👉 Đặt Xe & Thanh Toán Hóa Đơn (${formatCurrency(result.estimatedTotal + result.depositRequired)}) →`}
          </button>
        </div>
      )}
    </div>
  );
};
