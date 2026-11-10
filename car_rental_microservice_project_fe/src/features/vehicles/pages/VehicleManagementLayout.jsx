import { Link, Outlet } from 'react-router'
import { useAuth } from '../../../app/providers/auth-context'

export function VehicleManagementLayout() {
  const auth = useAuth()
  let content = <Outlet />
  if (!auth.ready) content = <p className="notice" role="status">Đang kiểm tra phiên đăng nhập…</p>
  else if (!auth.configured) content = <p className="notice" role="alert">Chưa cấu hình kết nối Keycloak.</p>
  else if (!auth.authenticated) content = <p className="notice">Đăng nhập để quản lý đội xe.</p>
  else if (!auth.canManageFleet) content = <p className="notice error" role="alert">Tài khoản cần quyền ADMIN hoặc STAFF.</p>

  return <main className="fleet-workspace">
    <header className="page-header">
      <div>
        <p className="eyebrow">CAR RENTAL / QUẢN LÝ ĐỘI XE</p>
        <h1>Quản lý xe</h1>
        <nav aria-label="Điều hướng đội xe" className="fleet-actions">
          <Link to="/vehicles">Danh sách xe</Link>
          <Link to="/vehicles/catalog">Danh mục xe</Link>
          <Link to="/">Tra cứu ảnh công khai</Link>
        </nav>
      </div>
      <div className="account">
        {auth.authenticated
          ? <><span>{auth.name}</span><button className="secondary" onClick={auth.logout}>Đăng xuất</button></>
          : auth.configured && <button disabled={!auth.ready} onClick={auth.login}>Đăng nhập quản trị</button>}
      </div>
    </header>
    {auth.error && <p className="notice error" role="alert">{auth.error}</p>}
    {content}
  </main>
}
