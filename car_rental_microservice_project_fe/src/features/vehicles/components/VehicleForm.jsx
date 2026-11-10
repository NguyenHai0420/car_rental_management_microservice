import { useState } from 'react'
import { Link } from 'react-router'
import { VehicleCatalogSelects } from './VehicleCatalogSelects'
import { vehicleError } from '../services/vehicle'

export function VehicleForm({ vehicle, catalog, cancelTo, onSave }) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  async function submit(event) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const fields = {
      categoryId: form.get('categoryId'),
      modelId: form.get('modelId'),
      vin: form.get('vin').trim(),
      licensePlate: form.get('licensePlate').trim(),
      mileageKm: form.get('mileageKm'),
      color: form.get('color').trim() || null,
      location: form.get('location').trim() || null,
      registrationDate: form.get('registrationDate') || null,
      insuranceExpiry: form.get('insuranceExpiry') || null,
      dailyRateSnapshot: form.get('dailyRateSnapshot') || null,
    }
    setSaving(true)
    setError('')
    try {
      await onSave(fields)
    } catch (failure) {
      setError(vehicleError(failure))
    } finally {
      setSaving(false)
    }
  }

  return <form onSubmit={submit} aria-busy={saving}>
    {error && <p className="notice error" role="alert">{error}</p>}
    <fieldset className="fleet-fields" disabled={saving}>
      <label>Biển số
        <input name="licensePlate" defaultValue={vehicle?.licensePlate} required maxLength={20} autoComplete="off" />
      </label>
      <label>VIN
        <input name="vin" defaultValue={vehicle?.vin} required minLength={17} maxLength={17}
          pattern="[A-HJ-NPR-Za-hj-npr-z0-9]{17}" title="17 chữ cái hoặc chữ số, trừ I, O và Q" autoComplete="off" />
      </label>
      <VehicleCatalogSelects catalog={catalog} categoryId={vehicle?.categoryId} modelId={vehicle?.modelId} required />
      <label>Số km
        <input name="mileageKm" type="number" defaultValue={vehicle?.mileageKm ?? 0} min="0" max="999999999.9" step="0.1" required />
      </label>
      <label>Giá thuê/ngày
        <input name="dailyRateSnapshot" type="number" defaultValue={vehicle?.dailyRateSnapshot ?? ''} min="0" max="9999999999.99" step="0.01" />
      </label>
      <label>Màu xe<input name="color" defaultValue={vehicle?.color ?? ''} maxLength={100} /></label>
      <label>Vị trí<input name="location" defaultValue={vehicle?.location ?? ''} maxLength={500} /></label>
      <label>Ngày đăng ký<input name="registrationDate" type="date" defaultValue={vehicle?.registrationDate ?? ''} /></label>
      <label>Hạn bảo hiểm<input name="insuranceExpiry" type="date" defaultValue={vehicle?.insuranceExpiry ?? ''} /></label>
      <div className="fleet-actions">
        <button type="submit">{saving ? 'Đang lưu…' : vehicle ? 'Lưu thay đổi' : 'Tạo xe'}</button>
        <Link to={cancelTo}>Hủy</Link>
      </div>
    </fieldset>
  </form>
}
