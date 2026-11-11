import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../app/providers/auth-context'
import { useVehicleQuery } from '../vehicles/hooks/useVehicleQuery'
import { listVehicleImages } from '../vehicles/services/vehicle'
import { formatPrice, fuelTypes, sizeClasses, transmissions } from '../vehicles/vehicle-fields'
import { CarPhoto } from './CarPhoto'

export function VehicleDialog({ car, onClose }) {
  const auth = useAuth()
  const navigate = useNavigate()
  const ref = useRef(null)
  const [index, setIndex] = useState(0)
  const load = useCallback((signal) => listVehicleImages(car.id, signal), [car.id])
  const gallery = useVehicleQuery(load, () => '')
  // Cover first, then the rest of the gallery; fall back to the cover from the list.
  const urls = gallery.data?.length ? gallery.data.map((image) => image.url) : car.imageUrl ? [car.imageUrl] : []
  const name = `${car.brand} ${car.modelName}`

  useEffect(() => {
    // No close() on cleanup: it would fire onClose and unmount us again (StrictMode remount).
    if (!ref.current.open) ref.current.showModal()
  }, [])

  const facts = [['Năm sản xuất', car.manufactureYear], ['Số chỗ', `${car.seatCount} chỗ`],
    ['Hộp số', transmissions[car.transmission]], ['Nhiên liệu', fuelTypes[car.fuelType]],
    ['Phân hạng', sizeClasses[car.sizeClass]], ['Màu xe', car.color], ['Nhận xe tại', car.location]]

  return <dialog ref={ref} className="car-dialog" aria-labelledby="car-dialog-title" onClose={onClose}
    onClick={(event) => { if (event.target === ref.current) onClose() }}>
    <div className="dialog-grid">
      <div className="gallery">
        <div className="gallery-main"><CarPhoto key={urls[index]} src={urls[index]} alt={`${name} - ảnh ${index + 1}`} /></div>
        {urls.length > 1 && <div className="thumbs">{urls.map((url, i) =>
          <button key={url} className="secondary" aria-current={i === index} aria-label={`Ảnh ${i + 1}`} onClick={() => setIndex(i)}><img src={url} alt="" loading="lazy" /></button>)}</div>}
      </div>
      <div className="dialog-info">
        <button className="btn btn-light border dialog-close" onClick={onClose} aria-label="Đóng">✕</button>
        <div><p className="eyebrow">{car.categoryName}</p><h2 id="car-dialog-title">{name}</h2></div>
        <p className="price">{formatPrice(car.dailyRate)}{car.dailyRate != null && <small> /ngày</small>}</p>
        <dl className="info-list">{facts.filter(([, value]) => value).map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
        <div className="d-grid gap-2">
          <button
            type="button"
            className="btn btn-outline-danger btn-lg w-100"
            onClick={() => {
              onClose()
              navigate('/pricing', { state: { showroomCar: { ...car, imageUrl: urls[0] || car.imageUrl } } })
            }}
          >
            Tính giá thuê xe này
          </button>
          {auth.authenticated ? (
          <button
            className="btn btn-danger btn-lg w-100"
            onClick={() => {
              onClose()
              navigate(`/bookings?vehicleId=${car.id}&action=book`, {
                state: { vehicle: car },
              })
            }}
          >
            <i className="bi bi-calendar-check me-2" aria-hidden="true" />
            Đặt xe này ngay
          </button>
        ) : (
          <button
            className="btn btn-danger btn-lg w-100"
            disabled={!auth.ready || !auth.configured}
            onClick={() => auth.login()}
          >
            <i className="bi bi-box-arrow-in-right me-2" aria-hidden="true" />
            Đăng nhập để đặt xe
          </button>
          )}
        </div>
      </div>
    </div>
  </dialog>
}
