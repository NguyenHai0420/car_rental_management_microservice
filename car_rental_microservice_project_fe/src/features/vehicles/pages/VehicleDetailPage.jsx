import { useCallback, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { useAuth } from '../../../app/providers/auth-context'
import { VehicleMediaPanel } from '../components/VehicleMediaPanel'
import { VehicleStatusBadge } from '../components/VehicleStatusBadge'
import { useVehicleQuery } from '../hooks/useVehicleQuery'
import { changeVehicleStatus, deleteVehicle, getVehicle, listVehicleStatusHistory, vehicleError } from '../services/vehicle'
import { allowedTransitions, formatVehicleNumber, modelLabel, vehicleStatuses } from '../vehicle-fields'

const historyTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'medium' })

export function VehicleDetailPage() {
  const { id } = useParams()
  return <VehicleDetail key={id} id={id} />
}

function VehicleDetail({ id }) {
  const auth = useAuth()
  const navigate = useNavigate()
  const { state } = useLocation()
  const loadVehicle = useCallback((signal) => getVehicle(id, signal), [id])
  const loadHistory = useCallback((signal) => listVehicleStatusHistory(id, signal), [id])
  const detail = useVehicleQuery(loadVehicle)
  const history = useVehicleQuery(loadHistory)
  const [mediaOpen, setMediaOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')
  const [notice, setNotice] = useState('')
  const vehicle = detail.data

  async function perform(action) {
    setBusy(true)
    setActionError('')
    setNotice('')
    try {
      await action()
    } catch (failure) {
      setActionError(vehicleError(failure))
      if ([404, 409].includes(failure?.response?.status)) {
        detail.reload()
        history.reload()
      }
    } finally {
      setBusy(false)
    }
  }

  function changeStatus(event) {
    event.preventDefault()
    const fields = new FormData(event.currentTarget)
    return perform(async () => {
      await changeVehicleStatus(id, { status: fields.get('status'), reason: fields.get('reason').trim() || null })
      setNotice('Đã cập nhật trạng thái.')
      detail.reload()
      history.reload()
    })
  }

  function remove() {
    if (!window.confirm(`Xóa xe ${vehicle.licensePlate} khỏi danh sách? Ảnh, giấy tờ và lịch sử sẽ được giữ.`)) return
    return perform(async () => {
      await deleteVehicle(id)
      navigate('/vehicles', { replace: true, state: { notice: 'Đã xóa xe.' } })
    })
  }

  if (detail.loading) return <p className="empty" role="status">Đang tải thông tin xe…</p>
  if (detail.error) return <div className="notice error">
    <p role="alert">{detail.error}</p><button className="secondary" onClick={detail.reload}>Thử lại</button>
  </div>

  const nextStatuses = allowedTransitions[vehicle.status] ?? []
  const canDelete = ['AVAILABLE', 'DECOMMISSIONED'].includes(vehicle.status)
  const details = [
    ['Mã xe', vehicle.id], ['VIN', vehicle.vin], ['Loại xe', vehicle.categoryName], ['Mẫu xe', modelLabel(vehicle)],
    ['Số km', formatVehicleNumber(vehicle.mileageKm)], ['Giá thuê/ngày', formatVehicleNumber(vehicle.dailyRateSnapshot)],
    ['Màu xe', vehicle.color], ['Vị trí', vehicle.location],
    ['Ngày đăng ký', vehicle.registrationDate], ['Hạn bảo hiểm', vehicle.insuranceExpiry],
  ]

  return <>
    <section className="media-section" aria-labelledby="fleet-detail-heading">
      <div className="section-heading">
        <div><p className="eyebrow">CHI TIẾT XE</p><h2 id="fleet-detail-heading">{vehicle.licensePlate}</h2></div>
        <div className="fleet-actions">
          <VehicleStatusBadge status={vehicle.status} />
          <Link to={`/vehicles/${id}/edit`}>Sửa thông tin</Link>
          <button className="danger fleet-delete" disabled={busy || !canDelete} onClick={remove}>Xóa xe</button>
        </div>
      </div>
      {(notice || (state?.notice && !actionError)) && <p className="notice success" role="status">{notice || state.notice}</p>}
      {actionError && <p className="notice error" role="alert">{actionError}</p>}
      <dl className="fleet-detail-fields">{details.map(([label, value]) =>
        <div key={label}><dt>{label}</dt><dd>{value || '—'}</dd></div>)}</dl>
      {!canDelete && <p className="hint">Chỉ xóa được xe đang sẵn sàng hoặc đã ngừng khai thác.</p>}
      <button className="secondary" aria-expanded={mediaOpen} onClick={() => setMediaOpen((open) => !open)}>
        {mediaOpen ? 'Đóng ảnh và giấy tờ' : 'Mở ảnh và giấy tờ'}
      </button>
    </section>
    {mediaOpen && <VehicleMediaPanel vehicleId={id} canManage={auth.canManage} />}
    <section className="media-section" aria-labelledby="fleet-history-heading">
      <h2 id="fleet-history-heading">Trạng thái và lịch sử</h2>
      {nextStatuses.length > 0 ? <form className="fleet-status-form" onSubmit={changeStatus} key={vehicle.status}>
        <label><span id="fleet-next-status">Trạng thái mới</span>
          <select aria-labelledby="fleet-next-status" name="status" defaultValue="" required disabled={busy}>
            <option value="">Chọn trạng thái</option>
            {nextStatuses.map((status) => <option key={status} value={status}>{vehicleStatuses[status]}</option>)}
          </select>
        </label>
        <label>Lý do<input name="reason" maxLength={500} disabled={busy} /></label>
        <button type="submit" disabled={busy}>Đổi trạng thái</button>
      </form> : <p className="hint">Xe đã ngừng khai thác.</p>}
      <VehicleHistory history={history} />
    </section>
  </>
}

function VehicleHistory({ history }) {
  if (history.loading) return <p className="empty" role="status">Đang tải lịch sử trạng thái…</p>
  if (history.error) return <div className="notice error">
    <p role="alert">{history.error}</p><button className="secondary" onClick={history.reload}>Tải lại lịch sử</button>
  </div>
  if (!history.data.length) return <p className="empty">Xe chưa đổi trạng thái.</p>

  return <div className="fleet-table-scroll"><table className="fleet-table">
    <caption>Lịch sử trạng thái</caption>
    <thead><tr>
      <th scope="col">Thời điểm</th><th scope="col">Từ</th><th scope="col">Đến</th>
      <th scope="col">Lý do</th><th scope="col">Người thực hiện</th>
    </tr></thead>
    <tbody>{history.data.map((event) => <tr key={event.id}>
      <td><time dateTime={event.changedAt}>{historyTime.format(new Date(event.changedAt))}</time></td>
      <td><VehicleStatusBadge status={event.oldStatus} /></td>
      <td><VehicleStatusBadge status={event.newStatus} /></td>
      <td>{event.reason || '—'}</td><td>{event.changedBy || '—'}</td>
    </tr>)}</tbody>
  </table></div>
}
