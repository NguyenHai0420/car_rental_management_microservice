import { useMemo, useState } from 'react'
import { useVehicleQuery } from '../vehicles/hooks/useVehicleQuery'
import { listShowroom, vehicleError } from '../vehicles/services/vehicle'
import { BrandLogo } from './BrandLogo'
import { CarCard } from './CarCard'
import { HeroSlider } from './HeroSlider'
import { SiteFooter } from './SiteFooter'
import { hotline, hotlineHref } from './site'
import { SiteHeader } from './SiteHeader'
import { VehicleDialog } from './VehicleDialog'

const PREVIEW = 4
const loadShowroom = (signal) => listShowroom({ size: 48 }, signal)
const showroomError = (error) => error?.response ? vehicleError(error) : 'Không kết nối được máy chủ. Vui lòng thử lại.'
const group = (cars, key) => [...Map.groupBy(cars, key)].sort((a, b) => b[1].length - a[1].length)

export function HomePage() {
  const showroom = useVehicleQuery(loadShowroom, showroomError)
  const [brand, setBrand] = useState('')
  const [category, setCategory] = useState('')
  const [term, setTerm] = useState('')
  const [selected, setSelected] = useState(null)
  const cars = showroom.data?.content
  const brands = useMemo(() => group(cars ?? [], (car) => car.brand), [cars])
  const categories = useMemo(() => [...new Map((cars ?? []).map((car) => [car.categoryId, car.categoryName])).entries()], [cars])
  const locations = useMemo(() => [...new Set((cars ?? []).map((car) => car.location).filter(Boolean))].slice(0, 6), [cars])
  const slides = useMemo(() => (cars ?? []).filter((car) => car.imageUrl).slice(0, 5), [cars])
  const filtering = Boolean(brand || category || term.trim())
  const sections = useMemo(() => {
    const q = term.trim().toLowerCase()
    return group((cars ?? []).filter((car) => (!brand || car.brand === brand) && (!category || car.categoryId === category)
      && (!q || `${car.brand} ${car.modelName}`.toLowerCase().includes(q))), (car) => car.brand)
  }, [cars, brand, category, term])
  const total = sections.reduce((sum, [, list]) => sum + list.length, 0)
  const pickBrand = (name) => { setBrand((current) => current === name ? '' : name); setCategory(''); setTerm('') }
  const showBrand = (name) => { setBrand(name); setCategory(''); setTerm('') }
  const reset = () => { setBrand(''); setCategory(''); setTerm('') }

  return <div className="site" id="top">
    <SiteHeader />
    <main>
      <HeroSlider cars={slides} onOpen={setSelected} />
      <div className="container finder-wrap"><form className="finder" role="search" onSubmit={(event) => { event.preventDefault(); document.getElementById('xe')?.scrollIntoView({ behavior: 'smooth' }) }}>
        <div className="row g-2 align-items-center">
          <div className="col-12 col-md"><label className="input-group"><span className="input-group-text"><i className="bi bi-search" aria-hidden="true" /></span><span className="sr-only">Tìm hãng hoặc mẫu xe</span>
            <input className="form-control" type="search" placeholder="Tìm hãng hoặc mẫu xe…" value={term} onChange={(event) => { setTerm(event.target.value); setBrand('') }} /></label></div>
          <div className="col-12 col-md-4"><label><span className="sr-only">Loại xe</span>
            <select className="form-select" value={category} onChange={(event) => { setCategory(event.target.value); setBrand('') }}>
              <option value="">Tất cả loại xe</option>
              {categories.map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select></label></div>
          <div className="col-12 col-md-auto"><button className="btn btn-danger w-100 px-4" type="submit"><i className="bi bi-search me-2" aria-hidden="true" />Tìm xe</button></div>
          <p className="col-12 col-xl-auto finder-stats">{cars ? <><strong>{cars.length}</strong> xe sẵn sàng · <strong>{brands.length}</strong> hãng</> : 'Đang tải…'}</p>
        </div>
      </form></div>

      {brands.length > 0 && <section className="container brand-tiles" id="hang-xe" aria-label="Hãng xe">
        <ul className="brand-row">{brands.map(([name, list]) => <li key={name}>
          <button className="secondary w-100 h-100" aria-pressed={brand === name} onClick={() => { pickBrand(name); document.getElementById('xe')?.scrollIntoView({ behavior: 'smooth' }) }}>
            <BrandLogo brand={name} className="tile-logo" /><strong>{name}</strong><small>{list.length} xe</small></button></li>)}</ul>
      </section>}

      <div className="container fleet" id="xe">
        {showroom.loading && <ul className="row row-cols-1 row-cols-sm-2 row-cols-lg-4 g-3" aria-busy="true" aria-label="Đang tải">{Array.from({ length: 4 }, (_, i) => <li key={i} className="col"><div className="skeleton" /></li>)}</ul>}
        {showroom.error && <div className="state" role="alert"><p>{showroom.error}</p><button className="btn btn-primary" onClick={showroom.reload}>Thử lại</button></div>}
        {categories.length > 1 && <div className="chips" role="group" aria-label="Lọc theo loại xe">
          <button className="chip" aria-pressed={!category} onClick={() => setCategory('')}>Tất cả</button>
          {categories.map(([id, name]) => <button key={id} className="chip" aria-pressed={category === id} onClick={() => { setCategory(id); setBrand('') }}>{name}</button>)}
        </div>}
        {cars && filtering && <p className="result-bar" role="status">{total} xe phù hợp <button className="btn btn-link btn-sm p-0 link" onClick={reset}>Xóa bộ lọc</button></p>}
        {cars && (sections.length
          ? sections.map(([name, list]) => {
            const shown = filtering ? list : list.slice(0, PREVIEW)
            return <section key={name} className="brand-section" aria-label={name}>
              <header><h2><BrandLogo brand={name} />{name}</h2><span className="count">{list.length} xe</span>
                {!filtering && list.length > PREVIEW && <button className="btn btn-sm btn-outline-danger see-all" onClick={() => pickBrand(name)}>Xem tất cả ({list.length})</button>}</header>
              <ul className="row row-cols-1 row-cols-sm-2 row-cols-lg-4 g-3 mt-0">{shown.map((car) => <CarCard key={car.id} car={car} onOpen={setSelected} />)}</ul>
            </section>
          })
          : !showroom.loading && <div className="state"><p>{cars.length ? 'Không có xe khớp bộ lọc.' : 'Hiện chưa có xe sẵn sàng. Vui lòng quay lại sau.'}</p>
            {cars.length > 0 && <button className="btn btn-outline-secondary" onClick={reset}>Xóa bộ lọc</button>}</div>)}
      </div>

      <section className="steps" aria-labelledby="steps-title"><div className="container">
        <h2 id="steps-title">Thuê xe chỉ với 3 bước</h2>
        <ol className="row g-3">{[['bi-search-heart', 'Chọn xe', 'Xem hình ảnh thật, giá theo ngày và thông số từng xe.'], ['bi-chat-dots', 'Liên hệ & xác nhận', 'Nhân viên tư vấn xác nhận lịch nhận xe và các giấy tờ cần thiết.'], ['bi-key', 'Nhận xe & lên đường', 'Kiểm tra xe cùng nhân viên, ký nhận và bắt đầu hành trình.']].map(([icon, title, text], i) =>
          <li className="col-md-4" key={title}><div className="step"><span className="step-no">{i + 1}</span><i className={`bi ${icon}`} aria-hidden="true" /><h3>{title}</h3><p>{text}</p></div></li>)}</ol>
      </div></section>
    </main>
    <SiteFooter brands={brands.map(([name]) => name)} locations={locations} onBrand={showBrand} />
    <a className="fab" href={hotlineHref} aria-label={hotline ? `Gọi ${hotline}` : 'Liên hệ'}><i className="bi bi-telephone-fill" aria-hidden="true" /></a>
    {selected && <VehicleDialog car={selected} onClose={() => setSelected(null)} />}
  </div>
}
