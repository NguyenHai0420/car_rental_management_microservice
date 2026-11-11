import { useCallback } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { VehicleCatalogSelects } from '../components/VehicleCatalogSelects'
import { VehicleStatusBadge } from '../components/VehicleStatusBadge'
import { useVehicleCatalog, useVehicleQuery } from '../hooks/useVehicleQuery'
import { listVehicles } from '../services/vehicle'
import { formatVehicleNumber, modelLabel, vehicleStatuses } from '../vehicle-fields'

const PAGE_SIZE = 10
const dots = { AVAILABLE: 'var(--ok)', RESERVED: 'var(--info)', RENTED: 'var(--violet)', IN_MAINTENANCE: 'var(--warn)' }

// One size=1 request per status: the page metadata carries the exact total.
async function loadCounts(signal) {
  const keys = Object.keys(vehicleStatuses)
  const pages = await Promise.all(keys.map((status) => listVehicles({ status, size: 1 }, signal)))
  return Object.fromEntries(keys.map((key, i) => [key, pages[i].page.totalElements]))
}

export function VehicleListPage() {
  const [params, setParams] = useSearchParams()
  const { state } = useLocation()
  const q = params.get('q') || ''
  const status = params.get('status') || ''
  const categoryId = params.get('categoryId') || ''
  const modelId = params.get('modelId') || ''
  const rawPage = Number(params.get('page'))
  const page = Number.isSafeInteger(rawPage) && rawPage > 0 ? rawPage : 0
  const load = useCallback((signal) => listVehicles({
    q: q || undefined, status: status || undefined, categoryId: categoryId || undefined,
    modelId: modelId || undefined, page, size: PAGE_SIZE,
  }, signal), [q, status, categoryId, modelId, page])
  const vehicles = useVehicleQuery(load)
  const catalog = useVehicleCatalog()
  const counts = useVehicleQuery(loadCounts, () => '').data

  function filter(event) {
    event.preventDefault()
    const fields = new FormData(event.currentTarget)
    const next = new URLSearchParams()
    for (const [name, value] of fields) {
      if (value.trim()) next.set(name, value.trim())
    }
    setParams(next)
  }

  function openPage(number) {
    const next = new URLSearchParams(params)
    if (number === 0) next.delete('page')
    else next.set('page', String(number))
    setParams(next)
  }

  const data = vehicles.data
  return <section className="media-section" aria-labelledby="fleet-list-heading">
    <div className="section-heading">
      <div>
        <h2 id="fleet-list-heading">Danh sách xe</h2>
        <p className="subtitle">Tìm theo tên xe, biển số hoặc VIN và lọc theo thông tin hiện tại của xe.</p>
      </div>
      <Link to="/vehicles/new" className="fleet-button">Thêm xe</Link>
    </div>
    {counts && <div className="stats">
      {Object.entries(vehicleStatuses).map(([key, label]) => <button key={key} className="stat" aria-pressed={status === key}
        onClick={() => setParams(status === key ? {} : { status: key })}>
        <span><i style={{ background: dots[key] }} />{label}</span><strong>{counts[key]}</strong></button>)}
    </div>}
    {state?.notice && <p className="notice success" role="status">{state.notice}</p>}
    <form className="fleet-filters" key={JSON.stringify([q, status, categoryId, modelId])} onSubmit={filter}>
      <label>Tên xe, biển số hoặc VIN<input name="q" type="search" defaultValue={q} placeholder="VD: Toyota Vios, 51A-12345…" /></label>
      <label><span id="fleet-filter-status">Trạng thái</span>
        <select aria-labelledby="fleet-filter-status" name="status" defaultValue={status}>
          <option value="">Tất cả trạng thái</option>
          {Object.entries(vehicleStatuses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
      <VehicleCatalogSelects key={String(catalog.loading)} catalog={catalog.data} categoryId={categoryId} modelId={modelId} />
      <div className="fleet-actions">
        <button type="submit">Áp dụng bộ lọc</button>
        <Link to="/vehicles">Xóa bộ lọc</Link>
      </div>
    </form>
    {catalog.loading && <p className="hint" role="status">Đang tải danh mục bộ lọc…</p>}
    {catalog.error && <div className="notice error">
      <p role="alert">{catalog.error}</p><button className="secondary" onClick={catalog.reload}>Tải lại danh mục</button>
    </div>}
    {vehicles.loading && <p className="empty" role="status">Đang tải danh sách xe…</p>}
    {vehicles.error && <div className="notice error">
      <p role="alert">{vehicles.error}</p><button className="secondary" onClick={vehicles.reload}>Thử lại</button>
    </div>}
    {data && <>
      {data.content.length === 0
        ? <p className="empty">Không tìm thấy xe phù hợp.</p>
        : <div className="fleet-table-scroll">
          <table className="fleet-table">
            <thead><tr>
              <th scope="col">Biển số</th><th scope="col">VIN</th><th scope="col">Mẫu xe</th>
              <th scope="col">Loại xe</th><th scope="col">Trạng thái</th><th scope="col">Số km</th>
            </tr></thead>
            <tbody>{data.content.map((vehicle) => <tr key={vehicle.id}>
              <th scope="row"><Link to={`/vehicles/${vehicle.id}`}>{vehicle.licensePlate}</Link></th>
              <td>{vehicle.vin}</td><td>{modelLabel(vehicle)}</td><td>{vehicle.categoryName}</td>
              <td><VehicleStatusBadge status={vehicle.status} /></td>
              <td>{formatVehicleNumber(vehicle.mileageKm)}</td>
            </tr>)}</tbody>
          </table>
        </div>}
      {(data.page.totalElements > 0 || page > 0) && <nav className="fleet-pagination" aria-label="Phân trang danh sách xe">
        <span>{data.page.totalElements} xe · Trang {page + 1} / {Math.max(1, data.page.totalPages)}</span>
        <div className="fleet-actions">
          {page >= data.page.totalPages && page > 0 && <button className="secondary" onClick={() => openPage(0)}>Về trang đầu</button>}
          <button className="secondary" disabled={page === 0} onClick={() => openPage(page - 1)}>Trang trước</button>
          <button className="secondary" disabled={page + 1 >= data.page.totalPages} onClick={() => openPage(page + 1)}>Trang sau</button>
        </div>
      </nav>}
    </>}
  </section>
}
