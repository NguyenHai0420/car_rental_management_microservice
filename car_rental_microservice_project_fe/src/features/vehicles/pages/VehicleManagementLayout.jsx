import { useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router'
import { useAuth } from '../../../app/providers/auth-context'

const links = [['/vehicles', 'Danh sách xe', '🚘', true], ['/vehicles/new', 'Thêm xe', '＋'], ['/vehicles/categories', 'Loại xe', '🗂️'], ['/vehicles/models', 'Mẫu xe', '🏷️']]
const titles = { '/vehicles': 'Danh sách xe', '/vehicles/new': 'Thêm xe', '/vehicles/categories': 'Loại xe', '/vehicles/models': 'Mẫu xe' }

export function VehicleManagementLayout() {
  const auth = useAuth()
  const [open, setOpen] = useState(false)
  const { pathname } = useLocation()
  let content = <Outlet />
  if (!auth.ready) content = <p className="notice" role="status">Đang kiểm tra phiên đăng nhập…</p>
  else if (!auth.configured) content = <p className="notice error" role="alert">Chưa cấu hình kết nối Keycloak.</p>
  else if (!auth.authenticated) content = <div className="gate"><h2>Đăng nhập để quản lý đội xe</h2><p className="subtitle">Khu vực dành cho nhân viên và quản trị viên.</p><button onClick={auth.login}>Đăng nhập quản trị</button></div>
  else if (!auth.canManageFleet) content = <div className="gate"><p className="notice error" role="alert">Tài khoản cần quyền ADMIN hoặc STAFF.</p><Link to="/">Về trang chủ</Link></div>

  return <div className="admin fleet-workspace">
    <aside className="admin-side" data-open={open} aria-label="Menu quản trị">
      <Link to="/" className="brand"><span className="brand-mark" aria-hidden="true">🚗</span>CarRental</Link>
      <nav className="admin-nav" onClick={() => setOpen(false)}>
        <span className="group">Đội xe</span>
        {links.map(([to, label, icon, end]) => <NavLink key={to} to={to} end={end}><span aria-hidden="true">{icon}</span>{label}</NavLink>)}
      </nav>
      <span className="spacer" />
      <nav className="admin-nav"><Link to="/">↩ Về trang chủ</Link></nav>
    </aside>
    <div className="admin-main">
      <header className="admin-top">
        <div className="fleet-actions"><button className="secondary admin-menu" aria-expanded={open} onClick={() => setOpen((v) => !v)}>☰ Menu</button>
          <span className="crumbs">Quản trị / <strong>{titles[pathname] ?? 'Chi tiết xe'}</strong></span></div>
        <div className="account">
          {auth.authenticated && <><span className="avatar" aria-hidden="true">{(auth.name || '?')[0].toUpperCase()}</span><span>{auth.name}</span>
            <button className="secondary" onClick={auth.logout}>Đăng xuất</button></>}
        </div>
      </header>
      <main className="admin-content">
        {auth.error && <p className="notice error" role="alert">{auth.error}</p>}
        {content}
      </main>
    </div>
  </div>
}
