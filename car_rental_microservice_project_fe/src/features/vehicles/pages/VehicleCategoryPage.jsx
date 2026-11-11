import { useState } from 'react'
import { Link } from 'react-router'
import { Pager } from '../components/Pager'
import { usePaging } from '../hooks/usePaging'
import { useCatalogSave } from '../hooks/useCatalogSave'
import { useVehicleCatalog } from '../hooks/useVehicleQuery'
import { createVehicleCategory, deleteVehicleCategory, updateVehicleCategory, vehicleError } from '../services/vehicle'
import { matchesSearch } from '../vehicle-fields'

export function VehicleCategoryPage() {
  const catalog = useVehicleCatalog()
  const { saving, message, error, save, run } = useCatalogSave(catalog.reload)
  const [editing, setEditing] = useState(null)
  const [query, setQuery] = useState('')
  const categories = (catalog.data?.categories ?? []).filter((category) => matchesSearch(query, category.name, category.description))
  const paging = usePaging(categories)
  const filtered = Boolean(query.trim())
  const modelCount = (id) => catalog.data.models.filter((model) => model.categoryId === id).length

  async function submit(event) {
    const body = ({ name, description }) => ({ name: name.trim(), description: description.trim() || null })
    const saved = await save(event, (fields) => editing ? updateVehicleCategory(editing.id, body(fields)) : createVehicleCategory(body(fields)),
      editing ? 'Đã cập nhật loại xe.' : 'Đã tạo loại xe.')
    if (saved) setEditing(null)
  }

  async function remove(category) {
    if (!window.confirm(`Xóa loại xe "${category.name}"?`)) return
    const removed = await run(() => deleteVehicleCategory(category.id), 'Đã xóa loại xe.',
      (failure) => failure?.response?.status === 409 ? 'Loại xe còn mẫu xe. Hãy chuyển hoặc xóa các mẫu xe trước.' : vehicleError(failure))
    if (removed && editing?.id === category.id) setEditing(null)
  }

  return <section className="media-section" aria-labelledby="category-heading">
    <div className="section-heading">
      <div><h2 id="category-heading">Loại xe</h2>
        <p className="subtitle">Nhóm xe theo kiểu dáng, ví dụ SUV, Sedan, Xe điện. Mỗi mẫu xe thuộc về một loại.</p></div>
      <Link to="/vehicles/models" className="fleet-button">Quản lý mẫu xe</Link>
    </div>
    {(error || catalog.error) && <p className="notice error" role="alert">{error || catalog.error}</p>}
    {catalog.error && <button className="secondary" onClick={catalog.reload}>Tải lại danh sách</button>}
    {message && <p className="notice success" role="status">{message}</p>}
    <div className="section-layout">
      <div>
        {catalog.loading && <p className="notice" role="status">Đang tải loại xe…</p>}
        {catalog.data?.categories.length > 0 && <div className="fleet-filters catalog-filters" role="search">
          <label>Tìm loại xe<input type="search" value={query} onChange={(event) => { setQuery(event.target.value); paging.setPage(0) }} placeholder="Tên hoặc mô tả…" /></label>
          {filtered && <div className="fleet-actions"><span className="hint">{categories.length} loại xe phù hợp</span>
            <button type="button" className="secondary" onClick={() => { setQuery(''); paging.setPage(0) }}>Xóa bộ lọc</button></div>}
        </div>}
        {catalog.data && (categories.length
          ? <><div className="fleet-table-scroll"><table className="fleet-table">
            <thead><tr><th scope="col">Tên loại xe</th><th scope="col">Mô tả</th><th scope="col">Số mẫu xe</th><th scope="col"><span className="sr-only">Thao tác</span></th></tr></thead>
            <tbody>{paging.visible.map((category) => <tr key={category.id}>
              <th scope="row">{category.name}</th>
              <td>{category.description || '—'}</td><td>{modelCount(category.id)}</td>
              <td><div className="row-actions"><button className="secondary" disabled={saving} onClick={() => setEditing(category)} aria-label={`Sửa ${category.name}`}>Sửa</button>
                <button className="danger" disabled={saving} onClick={() => remove(category)} aria-label={`Xóa ${category.name}`}>Xóa</button></div></td>
            </tr>)}</tbody></table></div>
          <Pager paging={paging} label="Phân trang loại xe" />
          </>
          : <p className="empty">{filtered ? 'Không có loại xe khớp bộ lọc.' : 'Chưa có loại xe. Hãy tạo loại xe đầu tiên ở khung bên cạnh.'}</p>)}
      </div>
      <form className="upload-form" key={editing?.id ?? 'new'} onSubmit={submit}>
        <fieldset disabled={saving}>
          <legend>{editing ? 'Sửa loại xe' : 'Thêm loại xe'}</legend>
          <label>Tên loại xe<input name="name" required maxLength={50} defaultValue={editing?.name ?? ''} /></label>
          <label>Mô tả<input name="description" maxLength={255} defaultValue={editing?.description ?? ''} /></label>
          <button type="submit">{saving ? 'Đang lưu…' : editing ? 'Lưu thay đổi' : 'Tạo loại xe'}</button>
          {editing && <button type="button" className="secondary" onClick={() => setEditing(null)}>Hủy sửa</button>}
        </fieldset>
      </form>
    </div>
  </section>
}
