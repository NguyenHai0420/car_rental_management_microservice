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

export const fuelTypes = {
  GASOLINE: 'Xăng', DIESEL: 'Dầu diesel', ELECTRIC: 'Điện', HYBRID: 'Hybrid', PLUGIN_HYBRID: 'Hybrid cắm sạc',
}
export const transmissions = { AUTOMATIC: 'Tự động', MANUAL: 'Số sàn', CVT: 'CVT' }

const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 })
export const formatPrice = (value) => (value == null ? 'Liên hệ' : money.format(value))

const fold = (text) => String(text ?? '').normalize('NFD').replace(/\p{Diacritic}/gu, '').replace(/đ/gi, 'd').toLowerCase()

// Every word of the query must appear in the fields, ignoring case and Vietnamese accents.
export function matchesSearch(query, ...fields) {
  const haystack = fold(fields.join(' '))
  return fold(query).split(/\s+/).filter(Boolean).every((word) => haystack.includes(word))
}
