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
