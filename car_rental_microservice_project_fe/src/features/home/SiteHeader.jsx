import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../app/providers/auth-context'
import { hotline, hotlineHref } from './site'

const perks = [['bi-tag', 'Giá minh bạch', 'Tính theo ngày, không phụ phí'], ['bi-shield-check', 'Xe được kiểm định', 'Bảo dưỡng định kỳ'], ['bi-lightning-charge', 'Nhận xe nhanh', 'Thủ tục gọn trong ngày']]
const links = [['Trang chủ', '/#top'], ['Hãng xe', '/#hang-xe'], ['Xe cho thuê', '/#xe'], ['Liên hệ', '/#lien-he']]

export function SiteHeader() {
  const auth = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const isBookingActive = location.pathname.startsWith('/bookings')
  const isProfileActive = location.pathname.startsWith('/profile')
  const isHome = location.pathname === '/'
  const isHistory = location.pathname.startsWith('/payment/history') || location.pathname.startsWith('/payments/history')

  const handleNavClick = (event, href) => {
    event.preventDefault()
    setOpen(false)
    const hash = href.includes('#') ? href.split('#')[1] : ''

    if (isHome) {
      if (!hash || hash === 'top') {
        window.scrollTo({ top: 0, behavior: 'smooth' })
      } else {
        const el = document.getElementById(hash)
        if (el) {
          el.scrollIntoView({ behavior: 'smooth' })
        }
      }
    } else {
      navigate(href)
    }
  }

  return <header className="site-header">
    <div className="topbar"><div className="container d-flex align-items-center gap-4 py-2">
      <Link
        to="/"
        className="brand"
        onClick={(e) => {
          if (isHome) {
            e.preventDefault()
            window.scrollTo({ top: 0, behavior: 'smooth' })
          }
        }}
      >
        <span className="brand-mark" aria-hidden="true"><i className="bi bi-car-front-fill" /></span>
        <span>CarRental<small>Thuê xe tự lái</small></span>
      </Link>
      <ul className="perks d-none d-lg-flex ms-auto">{perks.map(([icon, title, text]) =>
        <li key={title}><i className={`bi ${icon}`} aria-hidden="true" /><div><strong>{title}</strong><span>{text}</span></div></li>)}</ul>
      <a className="cta ms-auto ms-lg-0" href={hotlineHref}>Tư vấn miễn phí<strong>{hotline || 'Liên hệ ngay'}</strong></a>
    </div></div>
    <nav className="navbar navbar-expand-md mainnav" aria-label="Điều hướng chính"><div className="container">
      <button className="navbar-toggler" aria-expanded={open} aria-controls="main-menu" aria-label="Mở menu" onClick={() => setOpen(!open)}><i className="bi bi-list" aria-hidden="true" /></button>
      <div className={`collapse navbar-collapse${open ? ' show' : ''}`} id="main-menu">
        <ul className="navbar-nav me-auto">
          {links.map(([label, href], i) => (
            <li className="nav-item" key={label}>
              <a
                className="nav-link"
                href={href}
                aria-current={i === 0 && isHome ? 'page' : undefined}
                onClick={(e) => handleNavClick(e, href)}
              >
                {label}
              </a>
            </li>
          ))}
          <li className="nav-item"><Link className="nav-link text-nowrap" to="/payment/history" aria-current={isHistory ? 'page' : undefined} onClick={() => setOpen(false)}>Lịch sử thanh toán</Link></li>
          <li className="nav-item">
            <Link
              className="nav-link text-nowrap"
              to="/bookings"
              aria-current={isBookingActive ? 'page' : undefined}
              onClick={() => setOpen(false)}
            >
              <i className="bi bi-clock-history me-1" aria-hidden="true" />
              Đơn thuê của tôi
            </Link>
          </li>
          {auth.authenticated && (
            <li className="nav-item">
              <Link className="nav-link text-nowrap" to="/profile" aria-current={isProfileActive ? 'page' : undefined} onClick={() => setOpen(false)}>
                <i className="bi bi-person-circle me-1" aria-hidden="true" />
                Hồ sơ của tôi
              </Link>
            </li>
          )}
          {auth.canManageFleet && (
            <li className="nav-item">
              <Link className="nav-link text-nowrap" to="/vehicles" onClick={() => setOpen(false)}>
                Quản trị
              </Link>
            </li>
          )}
        </ul>
        <div className="account d-flex align-items-center gap-2 pb-2 pb-md-0 ms-auto text-nowrap">
          {auth.authenticated
            ? <>
              <Link to="/profile" className="d-flex align-items-center gap-1 text-decoration-none text-dark bg-white px-2 py-1 rounded border" title="Xem hồ sơ cá nhân">
                <i className="bi bi-person-circle me-1" aria-hidden="true" />
                <strong>{auth.name}</strong>
                {auth.roles?.filter((r) => ['ADMIN', 'STAFF'].includes(r)).map((r) => (
                  <span key={r} className="badge bg-danger ms-1" style={{ fontSize: '10.5px' }}>{r}</span>
                ))}
              </Link>
              <button className="btn btn-sm btn-outline-dark" onClick={() => auth.logout()}>Đăng xuất</button>
            </>
            : auth.configured && <button className="btn btn-sm btn-outline-dark" disabled={!auth.ready} onClick={() => auth.login()}>Đăng nhập</button>}
        </div>
      </div>
    </div></nav>
  </header>
}
