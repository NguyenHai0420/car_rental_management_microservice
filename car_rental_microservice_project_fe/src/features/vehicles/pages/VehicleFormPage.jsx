import { useCallback } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { VehicleForm } from '../components/VehicleForm'
import { useVehicleCatalog, useVehicleQuery } from '../hooks/useVehicleQuery'
import { createVehicle, getVehicle, updateVehicle } from '../services/vehicle'

export function VehicleFormPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const load = useCallback((signal) => id ? getVehicle(id, signal) : Promise.resolve(null), [id])
  const vehicle = useVehicleQuery(load)
  const catalog = useVehicleCatalog()

  async function save(fields) {
    const result = await (id ? updateVehicle(id, fields) : createVehicle(fields))
    navigate(`/vehicles/${result.id}`, {
      replace: true,
      state: { notice: id ? 'Đã lưu thay đổi.' : 'Đã thêm xe.' },
    })
  }

  return <section className="media-section" aria-labelledby="fleet-form-heading">
    <div className="section-heading">
      <h2 id="fleet-form-heading">{id ? 'Sửa thông tin xe' : 'Thêm xe'}</h2>
      <Link to="/vehicles/catalog">Danh mục xe</Link>
    </div>
    {(vehicle.loading || catalog.loading) && <p className="empty" role="status">Đang tải dữ liệu biểu mẫu…</p>}
    {vehicle.error && <div className="notice error">
      <p role="alert">{vehicle.error}</p><button className="secondary" onClick={vehicle.reload}>Tải lại xe</button>
    </div>}
    {catalog.error && <div className="notice error">
      <p role="alert">{catalog.error}</p><button className="secondary" onClick={catalog.reload}>Tải lại danh mục</button>
    </div>}
    {catalog.data && !vehicle.loading && !vehicle.error && (catalog.data.categories.length && catalog.data.models.length
      ? <VehicleForm key={id ?? 'new'} vehicle={vehicle.data} catalog={catalog.data}
        cancelTo={id ? `/vehicles/${id}` : '/vehicles'} onSave={save} />
      : <p className="notice">Cần ít nhất một loại xe và một mẫu xe. <Link to="/vehicles/catalog">Tạo danh mục xe</Link></p>)}
  </section>
}
