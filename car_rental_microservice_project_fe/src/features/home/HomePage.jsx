import { useMemo, useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../../app/providers/auth-context'
import { useVehicleQuery } from '../vehicles/hooks/useVehicleQuery'
import { listShowroom, vehicleError } from '../vehicles/services/vehicle'
import { formatPrice, fuelTypes, transmissions } from '../vehicles/vehicle-fields'
import { CarPhoto } from './CarPhoto'
import { VehicleDialog } from './VehicleDialog'

const loadShowroom = (signal) => listShowroom({ size: 48 }, signal)
const showroomError = (error) => error?.response ? vehicleError(error) : 'Không kết nối được máy chủ. Vui lòng thử lại.'

export function SiteHeader() {
  const auth = useAuth()
  return <header className="site-header"><div className="wrap">
    <Link to="/" className="brand"><span className="brand-mark" aria-hidden="true">🚗</span>CarRental</Link>
    <nav className="site-nav" aria-label="Điều hướng chính">
      <a className="nav-link" href="/#xe">Xe cho thuê</a>
      <Link className="nav-link" to="/lookup">Tra cứu biển số</Link>
      {auth.canManageFleet && <Link to="/vehicles">Quản trị</Link>}
      {auth.authenticated
        ? <><span className="user-chip">{auth.name}</span><button className="secondary" onClick={auth.logout}>Đăng xuất</button></>
        : auth.configured && <button disabled={!auth.ready} onClick={auth.login}>Đăng nhập</button>}
    </nav>
  </div></header>
}

function CarCard({ car, onOpen }) {
  const name = `${car.brand} ${car.modelName}`
  return <li className="car-card">
    <div className="car-photo"><CarPhoto src={car.imageUrl} alt={name} /><span className="car-tag">{car.categoryName}</span></div>
    <div className="car-body">
      <div><h3 className="car-title">{name}</h3><p className="car-sub">{car.manufactureYear}{car.color ? ` · ${car.color}` : ''}{car.location ? ` · ${car.location}` : ''}</p></div>
      <div className="specs">
        <span className="spec">{car.seatCount} chỗ</span>
        <span className="spec">{transmissions[car.transmission] ?? car.transmission}</span>
        <span className="spec">{fuelTypes[car.fuelType] ?? car.fuelType}</span>
      </div>
      <div className="car-foot">
        <p className="price">{formatPrice(car.dailyRate)}{car.dailyRate != null && <small> /ngày</small>}</p>
        <button onClick={() => onOpen(car)} aria-label={`Xem chi tiết ${name}`}>Chi tiết</button>
      </div>
    </div>
  </li>
}

export function HomePage() {
  const showroom = useVehicleQuery(loadShowroom, showroomError)
  const [category, setCategory] = useState('')
  const [term, setTerm] = useState('')
  const [selected, setSelected] = useState(null)
  const cars = showroom.data?.content
  const categories = useMemo(() => [...new Map((cars ?? []).map((car) => [car.categoryId, car.categoryName])).entries()], [cars])
  const visible = useMemo(() => {
    const q = term.trim().toLowerCase()
    return (cars ?? []).filter((car) => (!category || car.categoryId === category)
      && (!q || `${car.brand} ${car.modelName}`.toLowerCase().includes(q)))
  }, [cars, category, term])

  return <div className="site">
    <SiteHeader />
    <main>
      <section className="hero"><div className="wrap">
        <p className="eyebrow" style={{ color: '#5eead4' }}>Thuê xe tự lái · Giao xe nhanh</p>
        <h1>Chọn chiếc xe <span>phù hợp</span> cho hành trình của bạn</h1>
        <p>Đội xe được kiểm định, giá minh bạch theo ngày. Xem hình ảnh thật và đặt xe chỉ trong vài bước.</p>
        <div className="hero-cta"><a className="primary" href="#xe">Xem xe sẵn sàng</a><Link className="ghost" to="/lookup">Tra cứu theo biển số</Link></div>
        <div className="hero-stats">
          <div><strong>{cars ? cars.length : '—'}</strong><span>xe đang sẵn sàng</span></div>
          <div><strong>{cars ? categories.length : '—'}</strong><span>dòng xe</span></div>
          <div><strong>24/7</strong><span>hỗ trợ khách hàng</span></div>
        </div>
      </div></section>
      <div className="wrap features">
        {[['🛡️', 'Xe được kiểm định', 'Bảo dưỡng định kỳ, giấy tờ đầy đủ.'], ['💳', 'Giá rõ ràng', 'Giá theo ngày, không phụ phí ẩn.'], ['⚡', 'Nhận xe nhanh', 'Thủ tục gọn, nhận xe trong ngày.']].map(([icon, title, text]) =>
          <div className="feature" key={title}><span className="icon" aria-hidden="true">{icon}</span><div><h3>{title}</h3><p>{text}</p></div></div>)}
      </div>
      <section className="wrap fleet-section" id="xe" aria-labelledby="fleet-title">
        <div className="fleet-head">
          <div><p className="eyebrow">Đội xe</p><h2 id="fleet-title">Xe sẵn sàng cho thuê</h2>
            <p>{cars ? `${visible.length} xe phù hợp` : 'Đang tải danh sách xe…'}</p></div>
          <label className="search"><span className="sr-only">Tìm theo hãng hoặc mẫu xe</span>
            <input type="search" placeholder="Tìm hãng hoặc mẫu xe…" value={term} onChange={(event) => setTerm(event.target.value)} /></label>
        </div>
        {categories.length > 1 && <div className="chips" role="group" aria-label="Lọc theo loại xe">
          <button className="chip secondary" aria-pressed={!category} onClick={() => setCategory('')}>Tất cả</button>
          {categories.map(([id, name]) => <button key={id} className="chip secondary" aria-pressed={category === id} onClick={() => setCategory(id)}>{name}</button>)}
        </div>}
        {showroom.loading && <ul className="car-grid" aria-busy="true" aria-label="Đang tải">{Array.from({ length: 6 }, (_, i) => <li key={i} className="skeleton" />)}</ul>}
        {showroom.error && <div className="state" role="alert"><p>{showroom.error}</p><button onClick={showroom.reload}>Thử lại</button></div>}
        {cars && (visible.length
          ? <ul className="car-grid">{visible.map((car) => <CarCard key={car.id} car={car} onOpen={setSelected} />)}</ul>
          : <div className="state"><p>{cars.length ? 'Không có xe khớp bộ lọc.' : 'Hiện chưa có xe sẵn sàng. Vui lòng quay lại sau.'}</p>
            {cars.length > 0 && <button className="secondary" onClick={() => { setCategory(''); setTerm('') }}>Xóa bộ lọc</button>}</div>)}
      </section>
    </main>
    <footer className="site-footer"><div className="wrap"><span>© {new Date().getFullYear()} CarRental</span><span>Thuê xe tự lái an toàn, minh bạch.</span></div></footer>
    {selected && <VehicleDialog car={selected} onClose={() => setSelected(null)} />}
  </div>
}
