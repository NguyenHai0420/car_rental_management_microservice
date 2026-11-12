import { formatPrice, fuelTypes, transmissions } from '../vehicles/vehicle-fields'
import { CarPhoto } from './CarPhoto'

const swatches = { 'trắng': '#fff', 'đen': '#111', 'bạc': '#c0c4cc', 'xám': '#6b7280', 'đỏ': '#dc2626', 'xanh': '#2563eb',
  'xanh dương': '#2563eb', 'xanh lá': '#16a34a', 'vàng': '#facc15', 'cam': '#f97316', 'nâu': '#78350f', 'be': '#e7d8b8', 'ghi': '#8b8f98' }
const swatch = (color) => swatches[color?.trim().toLowerCase()] ?? [...Object.entries(swatches)].find(([name]) => color?.toLowerCase().includes(name))?.[1]

export function CarCard({ car, onOpen, onBook }) {
  const name = `${car.brand} ${car.modelName}`
  const dot = swatch(car.color)
  const rows = [['bi-people', 'Chỗ ngồi', `${car.seatCount}`], ['bi-fuel-pump', 'Nhiên liệu', fuelTypes[car.fuelType] ?? car.fuelType],
    ['bi-gear', 'Hộp số', transmissions[car.transmission] ?? car.transmission], ['bi-geo-alt', 'Nhận xe tại', car.location]]
  return <li className="col">
    <article className="card car-card h-100 d-flex flex-column">
      <button className="car-hit" onClick={() => onOpen(car)} aria-label={`Xem chi tiết ${name}`}>
        <span className="car-photo ratio ratio-4x3"><CarPhoto src={car.imageUrl} alt={name} /></span>
        <span className="badge car-cat">{car.categoryName}</span>
        <span className="car-color">{dot && <i style={{ background: dot }} aria-hidden="true" />}{car.color}</span>
        <span className="car-title">{name}</span>
        {car.licensePlate && (
          <span className="badge bg-primary-subtle text-primary border border-primary-subtle px-2 py-1 mt-1" style={{ fontSize: 12, fontWeight: 600 }}>
            {car.licensePlate}
          </span>
        )}
        <span className="car-price">{formatPrice(car.dailyRate)}{car.dailyRate != null && <small> /ngày</small>}</span>
      </button>
      <dl className="spec-panel">{rows.filter(([, , value]) => value).map(([icon, label, value]) =>
        <div key={label}><dt><i className={`bi ${icon}`} aria-hidden="true" />{label}</dt><dd><span className="badge text-bg-light border" title={value}>{value}</span></dd></div>)}</dl>
      {onBook && (
        <div className="mt-auto pt-2">
          <button
            type="button"
            className="btn btn-danger w-100 py-2"
            style={{ fontWeight: 600, fontSize: 14 }}
            onClick={(e) => {
              e.stopPropagation()
              onBook(car)
            }}
          >
            <i className="bi bi-calendar-check me-2" aria-hidden="true" />
            Đặt xe ngay
          </button>
        </div>
      )}
    </article>
  </li>
}
