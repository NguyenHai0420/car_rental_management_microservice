import { useState } from 'react'
import { Link } from 'react-router'
import { useVehicleCatalog } from '../hooks/useVehicleQuery'
import { createVehicleCategory, createVehicleModel, vehicleError } from '../services/vehicle'
import { modelLabel } from '../vehicle-fields'

const sizes = {
  COMPACT: 'Cỡ nhỏ', MIDSIZE: 'Cỡ trung', FULLSIZE: 'Cỡ lớn',
  SUV: 'SUV', LUXURY: 'Xe sang', VAN: 'Van', TRUCK: 'Xe tải', EV: 'Xe điện',
}
const fuels = {
  GASOLINE: 'Xăng', DIESEL: 'Dầu diesel', ELECTRIC: 'Điện', HYBRID: 'Hybrid', PLUGIN_HYBRID: 'Hybrid cắm sạc',
}
const transmissions = { AUTOMATIC: 'Tự động', MANUAL: 'Số sàn', CVT: 'CVT' }

function CatalogEnum({ name, options }) {
  return <select name={name} defaultValue="" required>
    <option value="">Chọn một mục</option>
    {Object.entries(options).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
  </select>
}

export function VehicleCatalogPage() {
  const catalog = useVehicleCatalog()
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  async function save(event, submit, success) {
    event.preventDefault()
    const form = event.currentTarget
    const fields = Object.fromEntries(new FormData(form))
    setSaving(true)
    setError('')
    setMessage('')
    try {
      await submit(fields)
      form.reset()
      catalog.reload()
      setMessage(success)
    } catch (failure) {
      setError(vehicleError(failure))
    } finally {
      setSaving(false)
    }
  }

  function addCategory(event) {
    return save(event, ({ name, description, sizeClass }) =>
      createVehicleCategory({ name: name.trim(), description: description.trim() || null, sizeClass }),
    'Đã tạo loại xe.')
  }

  function addModel(event) {
    return save(event, (fields) => createVehicleModel({
      ...fields,
      brand: fields.brand.trim(),
      modelName: fields.modelName.trim(),
      manufactureYear: Number(fields.manufactureYear),
      seatCount: Number(fields.seatCount),
      engineCapacityCc: fields.engineCapacityCc ? Number(fields.engineCapacityCc) : null,
    }), 'Đã tạo mẫu xe.')
  }

  return <section className="media-section" aria-labelledby="catalog-heading">
    <div className="section-heading">
      <div><h2 id="catalog-heading">Danh mục xe</h2>
        <p className="subtitle">Thêm loại và mẫu để chọn trong biểu mẫu xe.</p></div>
      <Link to="/vehicles/new" className="fleet-button">Thêm xe</Link>
    </div>
    {(error || catalog.error) && <p className="notice error" role="alert">{error || catalog.error}</p>}
    {catalog.error && <button className="secondary" onClick={catalog.reload}>Tải lại danh mục</button>}
    {message && <p className="notice success" role="status">{message}</p>}
    {catalog.loading && <p className="notice" role="status">Đang tải danh mục xe…</p>}
    <div className="fleet-catalog">
      <section>
        <form className="upload-form" onSubmit={addCategory}>
          <fieldset disabled={saving}>
            <legend>Thêm loại xe</legend>
            <label>Tên loại xe<input name="name" required maxLength={50} /></label>
            <label>Phân hạng<CatalogEnum name="sizeClass" options={sizes} /></label>
            <label>Mô tả<input name="description" maxLength={255} /></label>
            <button type="submit">Tạo loại xe</button>
          </fieldset>
        </form>
        {catalog.data && <CatalogEntries heading="Loại xe hiện có" items={catalog.data.categories}
          empty="Chưa có loại xe." label={(category) => category.name} />}
      </section>
      <section>
        <form className="upload-form" onSubmit={addModel}>
          <fieldset disabled={saving}>
            <legend>Thêm mẫu xe</legend>
            <label>Hãng xe<input name="brand" required maxLength={100} /></label>
            <label>Tên mẫu xe<input name="modelName" required maxLength={100} /></label>
            <div className="fleet-fields">
              <label>Năm sản xuất<input name="manufactureYear" type="number" min="1" max="9999" required /></label>
              <label>Số chỗ<input name="seatCount" type="number" min="1" max="2147483647" defaultValue="5" required /></label>
            </div>
            <label>Nhiên liệu<CatalogEnum name="fuelType" options={fuels} /></label>
            <label>Hộp số<CatalogEnum name="transmission" options={transmissions} /></label>
            <label>Dung tích động cơ (cc)<input name="engineCapacityCc" type="number" min="0" max="2147483647" /></label>
            <button type="submit">Tạo mẫu xe</button>
          </fieldset>
        </form>
        {catalog.data && <CatalogEntries heading="Mẫu xe hiện có" items={catalog.data.models}
          empty="Chưa có mẫu xe." label={modelLabel} />}
      </section>
    </div>
  </section>
}

function CatalogEntries({ heading, items, empty, label }) {
  return <>
    <h3>{heading}</h3>
    {items.length
      ? <ul className="fleet-catalog-entries">{items.map((item) => <li key={item.id}>{label(item)}</li>)}</ul>
      : <p className="hint">{empty}</p>}
  </>
}
