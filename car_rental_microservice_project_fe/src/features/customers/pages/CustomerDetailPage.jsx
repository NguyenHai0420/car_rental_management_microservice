import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  getCustomerById,
  verifyDriverLicense,
  getCustomerRentalHistory,
  customerError,
} from '../services/customer'
import { DriverLicenseCard } from '../components/DriverLicenseCard'
import { RentalHistoryTable } from '../components/RentalHistoryTable'

export function CustomerDetailPage() {
  const { id } = useParams()
  const [customer, setCustomer] = useState(null)
  const [rentals, setRentals] = useState([])
  const [loading, setLoading] = useState(true)
  const [rentalsLoading, setRentalsLoading] = useState(false)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState('')
  const [actionSuccess, setActionSuccess] = useState('')

  useEffect(() => {
    if (id) {
      loadCustomer()
      loadRentals()
    }
  }, [id])

  async function loadCustomer() {
    setLoading(true)
    setError('')
    try {
      const data = await getCustomerById(id)
      setCustomer(data)
    } catch (err) {
      setError(customerError(err))
    } finally {
      setLoading(false)
    }
  }

  async function loadRentals() {
    setRentalsLoading(true)
    try {
      const data = await getCustomerRentalHistory(id)
      setRentals(data || [])
    } catch (err) {
      console.warn('Could not load rentals for customer:', err)
    } finally {
      setRentalsLoading(false)
    }
  }

  async function handleVerify(event) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const status = form.get('status')
    const rejectionReason = form.get('rejectionReason')?.trim() || null

    if (status === 'REJECTED' && !rejectionReason) {
      setActionError('Vui lòng nhập lý do từ chối bằng lái xe.')
      return
    }

    setSaving(true)
    setActionError('')
    setActionSuccess('')
    try {
      const updatedLicense = await verifyDriverLicense(id, { status, rejectionReason })
      setCustomer((prev) => ({
        ...prev,
        driverLicense: updatedLicense,
        status: status === 'VERIFIED' ? 'ACTIVE' : prev.status,
      }))
      setActionSuccess(`Đã cập nhật trạng thái bằng lái thành: ${status}`)
    } catch (err) {
      setActionError(customerError(err))
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <p className="notice">Đang tải thông tin khách hàng…</p>
  if (error) return <p className="notice error">{error}</p>
  if (!customer) return <p className="empty">Không tìm thấy thông tin khách hàng.</p>

  const license = customer.driverLicense

  return (
    <div>
      <div className="page-header" style={{ marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '24px', margin: '0 0 4px' }}>
            Hồ sơ: {customer.fullName}
          </h1>
          <p className="subtitle" style={{ margin: 0 }}>
            {customer.email} • ID: {customer.id}
          </p>
        </div>
        <Link to="/vehicles/customers" className="secondary" style={{ padding: '8px 16px', borderRadius: '8px', textDecoration: 'none' }}>
          ← Danh sách khách hàng
        </Link>
      </div>

      <div className="stats" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '16px', marginBottom: '24px' }}>
        <div className="stat">
          <span>Trạng thái hồ sơ</span>
          <strong style={{ color: customer.status === 'ACTIVE' ? 'var(--ok, #16a34a)' : 'var(--warn, #d97706)' }}>
            {customer.status}
          </strong>
        </div>
        <div className="stat">
          <span>Hạng thành viên</span>
          <strong style={{ color: '#0284c7' }}>{customer.loyaltyTier || 'BRONZE'}</strong>
        </div>
        <div className="stat">
          <span>Tổng số lượt thuê</span>
          <strong>{customer.totalRentals || 0} chuyến</strong>
        </div>
        <div className="stat">
          <span>Trạng thái GPLX</span>
          <strong style={{ color: license?.verificationStatus === 'VERIFIED' ? 'var(--ok, #16a34a)' : 'var(--warn, #d97706)' }}>
            {license ? license.verificationStatus : 'CHƯA NỘP'}
          </strong>
        </div>
      </div>

      <div className="media-section" style={{ marginBottom: '24px' }}>
        <div className="section-heading">
          <h2>Thông tin cá nhân</h2>
        </div>
        <dl className="fleet-detail-fields">
          <div>
            <dt>Họ và tên</dt>
            <dd>{customer.fullName}</dd>
          </div>
          <div>
            <dt>Email</dt>
            <dd>{customer.email}</dd>
          </div>
          <div>
            <dt>Số điện thoại</dt>
            <dd>{customer.phone || '—'}</dd>
          </div>
          <div>
            <dt>Ngày sinh</dt>
            <dd>{customer.dateOfBirth || '—'}</dd>
          </div>
          <div>
            <dt>Giới tính</dt>
            <dd>{customer.gender || '—'}</dd>
          </div>
          <div>
            <dt>Ngày tham gia</dt>
            <dd>{customer.createdAt ? new Date(customer.createdAt).toLocaleDateString('vi-VN') : '—'}</dd>
          </div>
        </dl>
      </div>

      <div className="media-section" style={{ marginBottom: '24px' }}>
        <div className="section-heading">
          <h2>Giấy phép lái xe</h2>
          {license && (
            <span className="badge">Trạng thái: {license.verificationStatus}</span>
          )}
        </div>

        {license ? (
          <>
            <DriverLicenseCard license={license} />

            <form onSubmit={handleVerify} className="upload-form" style={{ marginTop: '20px' }} aria-busy={saving}>
              <legend>Kiểm duyệt Giấy phép lái xe (Dành cho Staff)</legend>
              {actionError && <p className="notice error">{actionError}</p>}
              {actionSuccess && <p className="notice" style={{ color: '#16a34a', borderColor: '#86efac' }}>✓ {actionSuccess}</p>}

              <fieldset className="fleet-fields" disabled={saving}>
                <label>
                  Quyết định kiểm duyệt
                  <select name="status" defaultValue={license.verificationStatus === 'REJECTED' ? 'REJECTED' : 'VERIFIED'}>
                    <option value="VERIFIED">Duyệt hợp lệ (VERIFIED) - Tự động kích hoạt tài khoản ACTIVE</option>
                    <option value="REJECTED">Từ chối (REJECTED) - Yêu cầu khách gửi lại</option>
                  </select>
                </label>

                <label>
                  Lý do từ chối (bắt buộc nếu chọn Từ chối)
                  <input
                    name="rejectionReason"
                    defaultValue={license.rejectionReason ?? ''}
                    placeholder="Ví dụ: Ảnh mờ, thông tin không khớp, bằng lái hết hạn…"
                  />
                </label>

                <div className="fleet-actions" style={{ gridColumn: '1 / -1' }}>
                  <button type="submit" className="fleet-button">
                    {saving ? 'Đang lưu kết quả…' : 'Xác nhận kiểm duyệt'}
                  </button>
                </div>
              </fieldset>
            </form>
          </>
        ) : (
          <p className="empty">Khách hàng này chưa nộp giấy phép lái xe.</p>
        )}
      </div>

      <div className="media-section">
        <div className="section-heading" style={{ marginBottom: '16px' }}>
          <h2>Lịch sử các chuyến thuê xe</h2>
          <span className="badge">Tổng: {rentals.length} chuyến</span>
        </div>
        <RentalHistoryTable rentals={rentals} loading={rentalsLoading} />
      </div>
    </div>
  )
}

export default CustomerDetailPage
