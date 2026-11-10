export const DEFAULT_VEHICLES = [
  {
    id: "11111111-1111-1111-1111-111111111111",
    name: "Xe Sedan (4 - 5 Chỗ)",
    models: "Mazda 3 / Toyota Vios / Kia Cerato",
    capacity: "4 - 5 chỗ",
    icon: "🚗",
    baseDailyRate: 80000,
    weekendRate: 100000,
    depositAmount: 200000,
    isDefaultTier: true,
  },
  {
    id: "22222222-2222-2222-2222-222222222222",
    name: "Xe SUV (7 Chỗ)",
    models: "Toyota Fortuner / Ford Everest / Hyundai SantaFe",
    capacity: "7 chỗ rộng rãi",
    icon: "🚙",
    baseDailyRate: 90000,
    weekendRate: 110000,
    depositAmount: 200000,
    isDefaultTier: true,
  },
];

export function formatCurrency(amount) {
  return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount || 0);
}

export function calculateRentalPricing(vehicle, pickupDateStr, returnDateStr) {
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
