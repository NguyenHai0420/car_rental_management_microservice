import { useState } from 'react'
import { Link, useLocation } from 'react-router'
import { useAuth } from '../../app/providers/auth-context'
import { hotline, hotlineHref } from './site'

const perks = [['bi-tag', 'Giá minh bạch', 'Tính theo ngày, không phụ phí'], ['bi-shield-check', 'Xe được kiểm định', 'Bảo dưỡng định kỳ'], ['bi-lightning-charge', 'Nhận xe nhanh', 'Thủ tục gọn trong ngày']]

export function SiteHeader() {
  const auth = useAuth()
  const { pathname } = useLocation()
  const [open, setOpen] = useState(false)

  const isPricing = pathname === '/pricing' || pathname === '/rent'
  const isPayment = pathname.startsWith('/payment')
  const isHome = !isPricing && !isPayment

  return <header className="site-header">
    <div className="topbar"><div className="container d-flex align-items-center gap-4 py-2">
      <Link to="/" className="brand"><span className="brand-mark" aria-hidden="true"><i className="bi bi-car-front-fill" /></span><span>CarRental<small>Thuê xe tự lái</small></span></Link>
      <ul className="perks d-none d-lg-flex ms-auto">{perks.map(([icon, title, text]) =>
        <li key={title}><i className={`bi ${icon}`} aria-hidden="true" /><div><strong>{title}</strong><span>{text}</span></div></li>)}</ul>
      <a className="cta ms-auto ms-lg-0" href={hotlineHref}>Tư vấn miễn phí<strong>{hotline || 'Liên hệ ngay'}</strong></a>
    </div></div>
    <nav className="navbar navbar-expand-md mainnav" aria-label="Điều hướng chính"><div className="container">
      <button className="navbar-toggler" aria-expanded={open} aria-controls="main-menu" aria-label="Mở menu" onClick={() => setOpen(!open)}><i className="bi bi-list" aria-hidden="true" /></button>
      <div className={`collapse navbar-collapse${open ? ' show' : ''}`} id="main-menu">
        <ul className="navbar-nav me-auto">
          <li className="nav-item"><a className="nav-link" href="/#top" aria-current={isHome ? 'page' : undefined} onClick={() => setOpen(false)}>Trang chủ</a></li>
          <li className="nav-item"><a className="nav-link" href="/#hang-xe" onClick={() => setOpen(false)}>Hãng xe</a></li>
          <li className="nav-item"><a className="nav-link" href="/#xe" onClick={() => setOpen(false)}>Xe cho thuê</a></li>
          <li className="nav-item"><Link className="nav-link" to="/pricing" aria-current={isPricing ? 'page' : undefined} onClick={() => setOpen(false)}>Tính giá &amp; Thuê xe</Link></li>
          <li className="nav-item"><Link className="nav-link" to="/payment" aria-current={isPayment ? 'page' : undefined} onClick={() => setOpen(false)}>Thanh toán VietQR</Link></li>
          <li className="nav-item"><a className="nav-link" href="/#lien-he" onClick={() => setOpen(false)}>Liên hệ</a></li>
          {auth.canManageFleet && <li className="nav-item"><Link className="nav-link" to="/vehicles" onClick={() => setOpen(false)}>Quản trị</Link></li>}
        </ul>
        <div className="account d-flex align-items-center gap-2 pb-2 pb-md-0">
          {auth.authenticated
            ? <><span><i className="bi bi-person-circle me-1" aria-hidden="true" />{auth.name}</span><button className="btn btn-sm btn-outline-dark" onClick={auth.logout}>Đăng xuất</button></>
            : auth.configured && <button className="btn btn-sm btn-outline-dark" disabled={!auth.ready} onClick={auth.login}>Đăng nhập</button>}
        </div>
      </div>
    </div></nav>
  </header>
}
