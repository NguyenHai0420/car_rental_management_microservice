import { Link } from 'react-router'
import { Pager } from '../components/Pager'
import { usePaging } from '../hooks/usePaging'
import { useCatalogSave } from '../hooks/useCatalogSave'
import { useVehicleCatalog } from '../hooks/useVehicleQuery'
import { createVehicleCategory } from '../services/vehicle'
import { sizeClasses } from '../vehicle-fields'

export function VehicleCategoryPage() {
  const catalog = useVehicleCatalog()
  const { saving, message, error, save } = useCatalogSave(catalog.reload)
  const paging = usePaging(catalog.data?.categories ?? [])
  const modelCount = (id) => catalog.data.models.filter((model) => model.categoryId === id).length

  function addCategory(event) {
    return save(event, ({ name, description, sizeClass }) =>
      createVehicleCategory({ name: name.trim(), description: description.trim() || null, sizeClass }),
    'Đã tạo loại xe.')
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
        {catalog.data && (catalog.data.categories.length
          ? <><div className="fleet-table-scroll"><table className="fleet-table">
            <thead><tr><th scope="col">Tên loại xe</th><th scope="col">Phân hạng</th><th scope="col">Mô tả</th><th scope="col">Số mẫu xe</th></tr></thead>
            <tbody>{paging.visible.map((category) => <tr key={category.id}>
              <th scope="row">{category.name}</th><td>{sizeClasses[category.sizeClass] ?? category.sizeClass}</td>
              <td>{category.description || '—'}</td><td>{modelCount(category.id)}</td>
            </tr>)}</tbody></table></div>
          <Pager paging={paging} label="Phân trang loại xe" />
          </>
          : <p className="empty">Chưa có loại xe. Hãy tạo loại xe đầu tiên ở khung bên cạnh.</p>)}
      </div>
      <form className="upload-form" onSubmit={addCategory}>
        <fieldset disabled={saving}>
          <legend>Thêm loại xe</legend>
          <label>Tên loại xe<input name="name" required maxLength={50} /></label>
          <label>Phân hạng
            <select name="sizeClass" defaultValue="" required>
              <option value="">Chọn một mục</option>
              {Object.entries(sizeClasses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
          <label>Mô tả<input name="description" maxLength={255} /></label>
          <button type="submit">{saving ? 'Đang lưu…' : 'Tạo loại xe'}</button>
        </fieldset>
      </form>
    </div>
  </section>
}
