export const DEFAULT_VEHICLES = [];

export function formatCurrency(amount) {
  return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount || 0);
}

export function mapShowroomCarToPricingVehicle(car, tierByCategory = {}) {
  const tier = car.categoryId ? tierByCategory[car.categoryId] : null;
  const rawRate = Number(car.dailyRate) > 0 ? Number(car.dailyRate) : Number(tier?.baseDailyRate) || 90000;
  const isLarge =
    Number(car.seatCount) >= 7 ||
    String(car.categoryName || "").toLowerCase().includes("suv");

  return {
    id: `showroom-${car.id}`,
    vehicleId: car.id,
    categoryId: car.categoryId,
    categoryName: car.categoryName || tier?.tierName || "Xe cho thuê",
    name: `${car.brand} ${car.modelName}`,
    models: `${car.categoryName || "Xe cho thuê"} · ${car.seatCount || 5} chỗ${car.location ? ` · ${car.location}` : ""}`,
    imageUrl: car.imageUrl || null,
    icon: isLarge ? "🚙" : "🚗",
    baseDailyRate: rawRate,
    weekendRate: Number(tier?.weekendRate) > 0 ? Number(tier.weekendRate) : Math.round(rawRate * 1.25),
    depositAmount: Number(tier?.depositAmount) > 0 ? Number(tier.depositAmount) : 200000,
  };
}

export function mapTierToPricingVehicle(tier) {
  const tierLabel = tier.tierName || "Phân khúc xe";
  const isSuv = tierLabel.toLowerCase().includes("suv") || tierLabel.toLowerCase().includes("7");
  const baseRate = Number(tier.baseDailyRate) || 80000;
  return {
    id: tier.categoryId || tier.id,
    categoryId: tier.categoryId,
    categoryName: tierLabel,
    name: tierLabel,
    models: "Bảng giá từ payment_db",
    imageUrl: null,
    icon: isSuv ? "🚙" : "🚗",
    baseDailyRate: baseRate,
    weekendRate: Number(tier.weekendRate) > 0 ? Number(tier.weekendRate) : Math.round(baseRate * 1.25),
    depositAmount: Number(tier.depositAmount) > 0 ? Number(tier.depositAmount) : 200000,
  };
}

export function calculateRentalPricing(vehicle, pickupDateStr, returnDateStr) {
  if (!vehicle) return null;

  const start = new Date(pickupDateStr);
  const end = new Date(returnDateStr);

  const diffMs = Math.max(0, end - start);
  const totalHours = Math.max(1, Math.round(diffMs / (1000 * 60 * 60)));
  const rentalDays = Math.max(1, Math.ceil(totalHours / 24));

  let weekdayCount = 0;
  let weekendCount = 0;

  for (let i = 0; i < rentalDays; i++) {
    const d = new Date(start);
    d.setDate(start.getDate() + i);
    const dayOfWeek = d.getDay(); // 0 = CN, 6 = Thứ 7
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

  const breakdownItems = [];
  if (weekdayCount > 0) {
    breakdownItems.push({
      description: `Giá ngày thường (${weekdayCount} ngày x ${formatCurrency(weekdayRate)})`,
      amount: weekdayTotal,
      isWeekend: false,
    });
  }
  if (weekendCount > 0) {
    breakdownItems.push({
      description: `Giá ngày cuối tuần Thứ 7 & CN (${weekendCount} ngày x ${formatCurrency(weekendRate)})`,
      amount: weekendTotal,
      isWeekend: true,
    });
  }

  return {
    categoryId: vehicle.categoryId || vehicle.id,
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
}
