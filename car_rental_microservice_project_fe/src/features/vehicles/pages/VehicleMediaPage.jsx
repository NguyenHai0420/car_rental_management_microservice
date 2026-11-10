import { useCallback, useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../../../app/providers/auth-context'
import { VehicleMediaPanel } from '../components/VehicleMediaPanel'
import { useVehicleQuery } from '../hooks/useVehicleQuery'
import { lookupVehicleByLicensePlate, vehicleError } from '../services/vehicle'

function lookupError(error) {
  if (error?.response?.status === 404) return 'Không tìm thấy xe theo biển số đã nhập.'
  if (error?.response?.status === 400) return 'Vui lòng nhập biển số xe hợp lệ.'
  return vehicleError(error)
}

export function VehicleMediaPage() {
  const auth = useAuth()
  const [search, setSearch] = useState(null)
  const [inputError, setInputError] = useState('')
  const load = useCallback((signal) => search
    ? lookupVehicleByLicensePlate(search.plate, signal)
    : Promise.resolve(null), [search])
  const lookup = useVehicleQuery(load, lookupError)
  const loading = Boolean(search) && lookup.loading
  const vehicleId = lookup.data?.id
  const error = inputError || lookup.error

  function selectVehicle(event) {
    event.preventDefault()
    const plate = new FormData(event.currentTarget).get('licensePlate').trim().toUpperCase()
    setInputError(plate ? '' : 'Vui lòng nhập biển số xe.')
    setSearch(plate ? { plate } : null)
  }

  return <main>
    <header className="page-header">
      <div><p className="eyebrow">CAR RENTAL / QUẢN LÝ ĐỘI XE</p>
        <h1>Hồ sơ hình ảnh & giấy tờ</h1>
        <p className="subtitle">Xem hình ảnh và quản lý hồ sơ của từng xe trong đội xe.</p>
        <Link className="fleet-link" to="/vehicles">Quản lý xe</Link>
      </div>
      <div className="account">
        {auth.authenticated ? <><span>{auth.name}</span><button className="secondary" onClick={auth.logout}>Đăng xuất</button></>
          : auth.configured && <button disabled={!auth.ready} onClick={auth.login}>Đăng nhập quản trị</button>}
      </div>
    </header>
    {auth.error && <p role="alert" className="notice error">{auth.error}</p>}
    <form className="vehicle-picker" onSubmit={selectVehicle} aria-busy={loading}>
      <label htmlFor="license-plate">Biển số xe</label>
      <div><input id="license-plate" name="licensePlate" type="search" placeholder="Ví dụ 51H-780.01"
        maxLength={20} required autoComplete="off" autoCapitalize="characters" spellCheck="false" />
        <button type="submit">Tra cứu xe</button></div>
      {loading && <p role="status" className="notice">Đang tra cứu biển số…</p>}
      {error && <p role="alert" className="notice error">{error}</p>}
    </form>
    {vehicleId && <>
      <p role="status" className="notice">Hồ sơ xe <strong>{search.plate}</strong></p>
      <VehicleMediaPanel key={`${vehicleId}:${auth.name}:${auth.canManage}`} vehicleId={vehicleId} canManage={auth.canManage} />
    </>}
    {!search && !error && <section className="welcome"><span className="welcome-icon" aria-hidden="true">▧</span>
      <h2>Tra cứu xe theo biển số</h2><p>Nhập đầy đủ biển số để xem ảnh công khai của xe.</p></section>}
  </main>
}
