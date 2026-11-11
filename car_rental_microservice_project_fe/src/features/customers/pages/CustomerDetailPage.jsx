import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getCustomerById, verifyDriverLicense, customerError } from '../services/customer'
import { DriverLicenseCard } from '../components/DriverLicenseCard'

export function CustomerDetailPage() {
  const { id } = useParams()
  const [customer, setCustomer] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState('')
  const [actionSuccess, setActionSuccess] = useState('')

  useEffect(() => {
    if (id) loadCustomer()
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

  if (loading) return <p className="empty">Đang tải thông tin khách hàng…</p>
  if (error) {
    return (
      <div className="notice error">
        <p>{error}</p>
        <Link to="/vehicles/customers">← Quay lại danh sách</Link>
      </div>
    )
  }
  if (!customer) return null

  const license = customer.driverLicense

  return (
    <div style={{ maxWidth: '960px' }}>
      <div className="page-header">
        <div>
          <h1>Hồ sơ khách hàng: {customer.fullName}</h1>
          <p className="subtitle">ID: {customer.id} | Email: {customer.email}</p>
        </div>
        <Link to="/vehicles/customers" className="secondary" style={{ padding: '8px 16px', borderRadius: '8px', textDecoration: 'none' }}>
          ← Danh sách khách hàng
        </Link>
      </div>

      <div className="media-section">
        <div className="section-heading">
          <h2>Thông tin cá nhân</h2>
          <span className="badge">Tài khoản: {customer.status}</span>
        </div>
        <dl className="fleet-detail-fields">
          <div>
            <dt>Họ và tên</dt>
            <dd>{customer.fullName || '—'}</dd>
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
            <dt>Hạng thành viên</dt>
            <dd>{customer.loyaltyTier}</dd>
          </div>
          <div>
            <dt>Tổng số lần thuê</dt>
            <dd>{customer.totalRentals} lượt</dd>
          </div>
          <div>
            <dt>Ngày tham gia</dt>
            <dd>{customer.createdAt ? new Date(customer.createdAt).toLocaleDateString('vi-VN') : '—'}</dd>
          </div>
        </dl>
      </div>

      <div className="media-section">
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
    </div>
  )
}

