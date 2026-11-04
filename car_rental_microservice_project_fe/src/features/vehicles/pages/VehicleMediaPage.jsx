import { useState } from 'react'
import { useAuth } from '../../../app/providers/auth-context'
import { VehicleMediaPanel } from '../components/VehicleMediaPanel'

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function VehicleMediaPage() {
  const auth = useAuth()
  const [vehicleId, setVehicleId] = useState('')
  const [error, setError] = useState('')

  function selectVehicle(event) {
    event.preventDefault()
    const id = new FormData(event.currentTarget).get('vehicleId').trim()
    if (!uuidPattern.test(id)) {
      setError('Mã xe phải là UUID hợp lệ.')
      return
    }
    setError('')
    setVehicleId(id.toLowerCase())
  }

  return <main>
    <header className="page-header">
      <div><p className="eyebrow">CAR RENTAL / QUẢN LÝ ĐỘI XE</p>
        <h1>Hồ sơ hình ảnh & giấy tờ</h1>
        <p className="subtitle">Xem hình ảnh và quản lý hồ sơ của từng xe trong đội xe.</p></div>
      <div className="account">
        {auth.authenticated ? <><span>{auth.name}</span><button className="secondary" onClick={auth.logout}>Đăng xuất</button></>
          : auth.configured && <button disabled={!auth.ready} onClick={auth.login}>Đăng nhập quản trị</button>}
      </div>
    </header>
    {auth.error && <p role="alert" className="notice error">{auth.error}</p>}
    <form className="vehicle-picker" onSubmit={selectVehicle}>
      <label htmlFor="vehicle-id">Mã xe</label>
      <div><input id="vehicle-id" name="vehicleId" placeholder="Nhập mã UUID của xe" required spellCheck="false" />
        <button type="submit">Mở hồ sơ</button></div>
      {error && <p role="alert" className="notice error">{error}</p>}
    </form>
    {vehicleId ? <VehicleMediaPanel key={`${vehicleId}:${auth.name}:${auth.canManage}`} vehicleId={vehicleId} canManage={auth.canManage} />
      : <section className="welcome"><span className="welcome-icon" aria-hidden="true">▧</span>
        <h2>Chọn xe để xem hồ sơ</h2><p>Nhập mã của xe đã có trong hệ thống để xem ảnh và giấy tờ liên quan.</p></section>}
  </main>
}
