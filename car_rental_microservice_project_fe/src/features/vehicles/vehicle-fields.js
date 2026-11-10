export const vehicleStatuses = {
  AVAILABLE: 'Sẵn sàng',
  RESERVED: 'Đã giữ chỗ',
  RENTED: 'Đang cho thuê',
  IN_MAINTENANCE: 'Đang bảo dưỡng',
  DECOMMISSIONED: 'Ngừng khai thác',
}

export const allowedTransitions = {
  AVAILABLE: ['RESERVED', 'IN_MAINTENANCE', 'DECOMMISSIONED'],
  RESERVED: ['RENTED', 'AVAILABLE'],
  RENTED: ['AVAILABLE'],
  IN_MAINTENANCE: ['AVAILABLE'],
  DECOMMISSIONED: [],
}

export function modelLabel({ brand, modelName, manufactureYear }) {
  return `${brand} ${modelName} · ${manufactureYear}`
}

const numberFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 })

export function formatVehicleNumber(value) {
  return value == null ? '—' : numberFormat.format(value)
}

export const sizeClasses = {
  COMPACT: 'Cỡ nhỏ', MIDSIZE: 'Cỡ trung', FULLSIZE: 'Cỡ lớn',
  SUV: 'SUV', LUXURY: 'Xe sang', VAN: 'Van', TRUCK: 'Xe tải', EV: 'Xe điện',
}
export const fuelTypes = {
  GASOLINE: 'Xăng', DIESEL: 'Dầu diesel', ELECTRIC: 'Điện', HYBRID: 'Hybrid', PLUGIN_HYBRID: 'Hybrid cắm sạc',
}
export const transmissions = { AUTOMATIC: 'Tự động', MANUAL: 'Số sàn', CVT: 'CVT' }

const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 })
export const formatPrice = (value) => (value == null ? 'Liên hệ' : money.format(value))
