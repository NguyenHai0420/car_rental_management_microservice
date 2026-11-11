import { useState } from 'react'
import { Link } from 'react-router'
import { Pager } from '../components/Pager'
import { usePaging } from '../hooks/usePaging'
import { useCatalogSave } from '../hooks/useCatalogSave'
import { useVehicleCatalog } from '../hooks/useVehicleQuery'
import { createVehicleModel, deleteVehicleModel, updateVehicleModel, vehicleError } from '../services/vehicle'
import { fuelTypes, matchesSearch, transmissions } from '../vehicle-fields'

function EnumSelect({ name, options, value = '' }) {
  return <select name={name} defaultValue={value} required>
    <option value="">Chọn một mục</option>
    {Object.entries(options).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
  </select>
}

export function VehicleModelPage() {
  const catalog = useVehicleCatalog()
  const { saving, message, error, save, run } = useCatalogSave(catalog.reload)
  const [editing, setEditing] = useState(null)
  const [query, setQuery] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [fuelType, setFuelType] = useState('')
  const categories = catalog.data?.categories ?? []
  const models = (catalog.data?.models ?? []).filter((model) => (!categoryId || model.categoryId === categoryId)
    && (!fuelType || model.fuelType === fuelType)
    && matchesSearch(query, model.brand, model.modelName, model.manufactureYear, model.categoryName))
  const paging = usePaging(models)
  const filtered = Boolean(query.trim() || categoryId || fuelType)
  const changeFilter = (set) => (event) => { set(event.target.value); paging.setPage(0) }

  async function submit(event) {
    const body = (fields) => ({
      ...fields,
      brand: fields.brand.trim(),
      modelName: fields.modelName.trim(),
      manufactureYear: Number(fields.manufactureYear),
      seatCount: Number(fields.seatCount),
      engineCapacityCc: fields.engineCapacityCc ? Number(fields.engineCapacityCc) : null,
    })
    const saved = await save(event, (fields) => editing ? updateVehicleModel(editing.id, body(fields)) : createVehicleModel(body(fields)),
      editing ? 'Đã cập nhật mẫu xe.' : 'Đã tạo mẫu xe.')
    if (saved) setEditing(null)
  }

  async function remove(model) {
    if (!window.confirm(`Xóa mẫu xe "${model.brand} ${model.modelName}"?`)) return
    const removed = await run(() => deleteVehicleModel(model.id), 'Đã xóa mẫu xe.',
      (failure) => failure?.response?.status === 409 ? 'Mẫu xe đang được dùng bởi xe trong đội xe nên không thể xóa.' : vehicleError(failure))
    if (removed && editing?.id === model.id) setEditing(null)
  }

  return <section className="media-section" aria-labelledby="model-heading">
    <div className="section-heading">
      <div><h2 id="model-heading">Mẫu xe</h2>
        <p className="subtitle">Thông số kỹ thuật dùng chung cho các xe cùng dòng. Chọn mẫu xe là đủ để xe thuộc đúng loại.</p></div>
      <Link to="/vehicles/new" className="fleet-button">Thêm xe</Link>
    </div>
    {(error || catalog.error) && <p className="notice error" role="alert">{error || catalog.error}</p>}
    {catalog.error && <button className="secondary" onClick={catalog.reload}>Tải lại danh sách</button>}
    {message && <p className="notice success" role="status">{message}</p>}
    <div className="section-layout">
      <div>
        {catalog.loading && <p className="notice" role="status">Đang tải mẫu xe…</p>}
        {catalog.data?.models.length > 0 && <div className="fleet-filters catalog-filters" role="search">
          <label>Tìm mẫu xe<input type="search" value={query} onChange={changeFilter(setQuery)} placeholder="Hãng, tên mẫu hoặc năm…" /></label>
          <label>Loại xe
            <select value={categoryId} onChange={changeFilter(setCategoryId)}>
              <option value="">Tất cả loại xe</option>
              {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
            </select>
          </label>
          <label>Nhiên liệu
            <select value={fuelType} onChange={changeFilter(setFuelType)}>
              <option value="">Tất cả nhiên liệu</option>
              {Object.entries(fuelTypes).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
          {filtered && <div className="fleet-actions"><span className="hint">{models.length} mẫu xe phù hợp</span>
            <button type="button" className="secondary" onClick={() => { setQuery(''); setCategoryId(''); setFuelType(''); paging.setPage(0) }}>Xóa bộ lọc</button></div>}
        </div>}
        {catalog.data && (models.length
          ? <><div className="fleet-table-scroll"><table className="fleet-table">
            <thead><tr><th scope="col">Mẫu xe</th><th scope="col">Loại xe</th><th scope="col">Năm</th><th scope="col">Số chỗ</th>
              <th scope="col">Nhiên liệu</th><th scope="col">Hộp số</th><th scope="col"><span className="sr-only">Thao tác</span></th></tr></thead>
            <tbody>{paging.visible.map((model) => <tr key={model.id}>
              <th scope="row">{model.brand} {model.modelName}</th>
              <td>{model.categoryName ?? <span className="hint">Chưa gán loại</span>}</td><td>{model.manufactureYear}</td><td>{model.seatCount}</td>
              <td>{fuelTypes[model.fuelType] ?? model.fuelType}</td><td>{transmissions[model.transmission] ?? model.transmission}</td>
              <td><div className="row-actions"><button className="secondary" disabled={saving} onClick={() => setEditing(model)} aria-label={`Sửa ${model.brand} ${model.modelName}`}>Sửa</button>
                <button className="danger" disabled={saving} onClick={() => remove(model)} aria-label={`Xóa ${model.brand} ${model.modelName}`}>Xóa</button></div></td>
            </tr>)}</tbody></table></div>
          <Pager paging={paging} label="Phân trang mẫu xe" />
          </>
          : <p className="empty">{filtered ? 'Không có mẫu xe khớp bộ lọc.' : 'Chưa có mẫu xe. Hãy tạo mẫu xe đầu tiên ở khung bên cạnh.'}</p>)}
      </div>
      {catalog.data && (categories.length
        ? <form className="upload-form" key={editing?.id ?? 'new'} onSubmit={submit}>
          <fieldset disabled={saving}>
            <legend>{editing ? 'Sửa mẫu xe' : 'Thêm mẫu xe'}</legend>
            <label>Loại xe
              <select name="categoryId" defaultValue={editing?.categoryId ?? ''} required>
                <option value="">Chọn loại xe</option>
                {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
              </select>
            </label>
            <label>Hãng xe<input name="brand" required maxLength={100} defaultValue={editing?.brand ?? ''} /></label>
            <label>Tên mẫu xe<input name="modelName" required maxLength={100} defaultValue={editing?.modelName ?? ''} /></label>
            <div className="form-row">
              <label>Năm sản xuất<input name="manufactureYear" type="number" min="1" max="9999" defaultValue={editing?.manufactureYear ?? ''} required /></label>
              <label>Số chỗ<input name="seatCount" type="number" min="1" max="2147483647" defaultValue={editing?.seatCount ?? 5} required /></label>
            </div>
            <label>Nhiên liệu<EnumSelect name="fuelType" options={fuelTypes} value={editing?.fuelType} /></label>
            <label>Hộp số<EnumSelect name="transmission" options={transmissions} value={editing?.transmission} /></label>
            <label>Dung tích động cơ (cc)<input name="engineCapacityCc" type="number" min="0" max="2147483647" defaultValue={editing?.engineCapacityCc ?? ''} /></label>
            <button type="submit">{saving ? 'Đang lưu…' : editing ? 'Lưu thay đổi' : 'Tạo mẫu xe'}</button>
            {editing && <button type="button" className="secondary" onClick={() => setEditing(null)}>Hủy sửa</button>}
          </fieldset>
        </form>
        : <p className="notice">Cần có ít nhất một loại xe trước. <Link to="/vehicles/categories">Tạo loại xe</Link></p>)}
    </div>
  </section>
}
