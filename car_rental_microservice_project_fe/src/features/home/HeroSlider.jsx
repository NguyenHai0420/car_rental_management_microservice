import { useEffect, useState } from 'react'
import { formatPrice } from '../vehicles/vehicle-fields'
import { CarPhoto } from './CarPhoto'

// Deterministic "snow" so the layout is stable between renders.
const flakes = Array.from({ length: 34 }, (_, i) => ({ left: (i * 37) % 100, size: 3 + (i * 7) % 7, delay: -((i * 53) % 22), duration: 10 + (i * 11) % 14, drift: ((i * 17) % 60) - 30 }))
const reduced = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

export function HeroSlider({ cars, onOpen }) {
  const [index, setIndex] = useState(0)
  const [paused, setPaused] = useState(false)
  const count = cars.length + 1
  const active = index % count
  const go = (next) => setIndex((next + count) % count)

  useEffect(() => {
    if (paused || count < 2 || reduced()) return undefined
    const timer = setInterval(() => setIndex((i) => (i + 1) % count), 6000)
    return () => clearInterval(timer)
  }, [paused, count])

  return <section className="hero-slider" aria-roledescription="carousel" aria-label="Xe nổi bật"
    onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)} onFocus={() => setPaused(true)} onBlur={() => setPaused(false)}>
    <div className="slide" data-active={active === 0} aria-hidden={active !== 0} role="group" aria-roledescription="slide">
      <div className="container slide-text">
        <p className="kicker">Thuê xe tự lái · Giao xe nhanh</p>
        <h1>Chọn chiếc xe <span>phù hợp</span> cho hành trình của bạn</h1>
        <p className="lead">Đội xe được kiểm định, hình ảnh thật, giá theo ngày minh bạch.</p>
        <a
          className="btn btn-danger btn-lg px-4"
          href="#xe"
          tabIndex={active === 0 ? 0 : -1}
          onClick={(e) => {
            e.preventDefault()
            document.getElementById('xe')?.scrollIntoView({ behavior: 'smooth' })
          }}
        >
          Xem xe sẵn sàng <i className="bi bi-arrow-right" aria-hidden="true" />
        </a>
      </div>
    </div>
    {cars.map((car, i) => {
      const on = active === i + 1
      const name = `${car.brand} ${car.modelName}`
      return <div key={car.id} className="slide" data-active={on} aria-hidden={!on} role="group" aria-roledescription="slide" aria-label={`${i + 2} / ${count}`}>
        <div className="slide-photo"><CarPhoto src={car.imageUrl} alt="" /></div>
        <div className="container slide-text">
          <p className="kicker">{car.categoryName} · {car.manufactureYear}</p>
          <h2>{name}</h2>
          <p className="slide-price">{formatPrice(car.dailyRate)}{car.dailyRate != null && <small> /ngày</small>}</p>
          <button className="btn btn-danger btn-lg px-4" tabIndex={on ? 0 : -1} onClick={() => onOpen(car)}>Xem chi tiết <i className="bi bi-arrow-right" aria-hidden="true" /></button>
        </div>
      </div>
    })}
    <div className="flakes" aria-hidden="true">{flakes.map((f, i) => <span key={i}
      style={{ left: `${f.left}%`, width: f.size, height: f.size, animationDelay: `${f.delay}s`, animationDuration: `${f.duration}s`, '--drift': `${f.drift}px` }} />)}</div>
    {count > 1 && <>
      <button className="slide-nav prev" aria-label="Xe trước" onClick={() => go(active - 1)}><i className="bi bi-chevron-left" aria-hidden="true" /></button>
      <button className="slide-nav next" aria-label="Xe sau" onClick={() => go(active + 1)}><i className="bi bi-chevron-right" aria-hidden="true" /></button>
      <div className="slide-dots">{Array.from({ length: count }, (_, i) =>
        <button key={i} aria-label={`Chuyển đến slide ${i + 1}`} aria-current={active === i} onClick={() => go(i)} />)}</div>
    </>}
  </section>
}
