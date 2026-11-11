import { Link } from 'react-router'
import { Pager } from '../components/Pager'
import { usePaging } from '../hooks/usePaging'
import { useCatalogSave } from '../hooks/useCatalogSave'
import { useVehicleCatalog } from '../hooks/useVehicleQuery'
import { createVehicleModel } from '../services/vehicle'
import { fuelTypes, transmissions } from '../vehicle-fields'

function EnumSelect({ name, options }) {
  return <select name={name} defaultValue="" required>
    <option value="">Chọn một mục</option>
    {Object.entries(options).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
  </select>
}

export function VehicleModelPage() {
  const catalog = useVehicleCatalog()
  const { saving, message, error, save } = useCatalogSave(catalog.reload)
  const paging = usePaging(catalog.data?.models ?? [])
  const categories = catalog.data?.categories ?? []

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
        {catalog.data && (catalog.data.models.length
          ? <><div className="fleet-table-scroll"><table className="fleet-table">
            <thead><tr><th scope="col">Mẫu xe</th><th scope="col">Loại xe</th><th scope="col">Năm</th><th scope="col">Số chỗ</th>
              <th scope="col">Nhiên liệu</th><th scope="col">Hộp số</th></tr></thead>
            <tbody>{paging.visible.map((model) => <tr key={model.id}>
              <th scope="row">{model.brand} {model.modelName}</th>
              <td>{model.categoryName ?? <span className="hint">Chưa gán loại</span>}</td><td>{model.manufactureYear}</td><td>{model.seatCount}</td>
              <td>{fuelTypes[model.fuelType] ?? model.fuelType}</td><td>{transmissions[model.transmission] ?? model.transmission}</td>
            </tr>)}</tbody></table></div>
          <Pager paging={paging} label="Phân trang mẫu xe" />
          </>
          : <p className="empty">Chưa có mẫu xe. Hãy tạo mẫu xe đầu tiên ở khung bên cạnh.</p>)}
      </div>
      {catalog.data && (categories.length
        ? <form className="upload-form" onSubmit={addModel}>
          <fieldset disabled={saving}>
            <legend>Thêm mẫu xe</legend>
            <label>Loại xe
              <select name="categoryId" defaultValue="" required>
                <option value="">Chọn loại xe</option>
                {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
              </select>
            </label>
            <label>Hãng xe<input name="brand" required maxLength={100} /></label>
            <label>Tên mẫu xe<input name="modelName" required maxLength={100} /></label>
            <div className="form-row">
              <label>Năm sản xuất<input name="manufactureYear" type="number" min="1" max="9999" required /></label>
              <label>Số chỗ<input name="seatCount" type="number" min="1" max="2147483647" defaultValue="5" required /></label>
            </div>
            <label>Nhiên liệu<EnumSelect name="fuelType" options={fuelTypes} /></label>
            <label>Hộp số<EnumSelect name="transmission" options={transmissions} /></label>
            <label>Dung tích động cơ (cc)<input name="engineCapacityCc" type="number" min="0" max="2147483647" /></label>
            <button type="submit">{saving ? 'Đang lưu…' : 'Tạo mẫu xe'}</button>
          </fieldset>
        </form>
        : <p className="notice">Cần có ít nhất một loại xe trước. <Link to="/vehicles/categories">Tạo loại xe</Link></p>)}
    </div>
  </section>
}
