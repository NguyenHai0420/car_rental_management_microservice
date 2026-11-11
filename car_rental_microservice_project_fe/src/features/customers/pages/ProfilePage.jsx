import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert, Spin } from 'antd'
import { useAuth } from '../../../app/providers/auth-context'
import {
  getMyProfile,
  updateMyProfile,
  submitDriverLicense,
  getMyRentalHistory,
  customerError,
} from '../services/customer'
import { CustomerForm } from '../components/CustomerForm'
import { DriverLicenseForm } from '../components/DriverLicenseForm'
import { DriverLicenseCard } from '../components/DriverLicenseCard'
import { RentalHistoryTable } from '../components/RentalHistoryTable'

export function ProfilePage() {
  const auth = useAuth()
  const [profile, setProfile] = useState(null)
  const [rentals, setRentals] = useState([])
  const [loading, setLoading] = useState(true)
  const [rentalsLoading, setRentalsLoading] = useState(true)
  const [error, setError] = useState('')
  const [rentalsError, setRentalsError] = useState('')
  const [showLicenseEdit, setShowLicenseEdit] = useState(false)

  useEffect(() => {
    if (!auth.ready || !auth.authenticated) return
    const controller = new AbortController()
    getMyProfile(controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return
        setProfile(data)
        setError('')
      })
      .catch((err) => {
        if (!controller.signal.aborted) setError(customerError(err))
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    getMyRentalHistory(controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return
        setRentals(data || [])
        setRentalsError('')
      })
      .catch((err) => {
        if (!controller.signal.aborted) setRentalsError(customerError(err))
      })
      .finally(() => {
        if (!controller.signal.aborted) setRentalsLoading(false)
      })
    return () => controller.abort()
  }, [auth.ready, auth.authenticated])

  async function loadProfile() {
    setLoading(true)
    setError('')
    try {
      const data = await getMyProfile()
      setProfile(data)
    } catch (err) {
      setError(customerError(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ maxWidth: '960px', margin: '0 auto' }}>
      <div className="page-header" style={{ marginBottom: '24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1 style={{ fontSize: '28px', margin: '0 0 6px', letterSpacing: '-.02em' }}>
            Hồ sơ khách hàng
          </h1>
          <p className="subtitle" style={{ margin: 0, color: 'var(--muted, #64748b)' }}>
            Quản lý thông tin tài khoản, giấy phép lái xe và lịch sử chuyến đi
          </p>
        </div>
        <Link to="/" className="btn btn-outline-secondary" style={{ padding: '8px 16px', borderRadius: '8px', textDecoration: 'none' }}>
          ← Quay lại trang chủ
        </Link>
      </div>

      {loading && (
        <div style={{ textAlign: 'center', padding: '40px 0' }}>
          <Spin size="large" description="Đang tải thông tin hồ sơ từ Customer Service..." />
        </div>
      )}

      {error && (
        <Alert
          type="error"
          showIcon
          message="Lỗi tải hồ sơ"
          description={error}
          style={{ marginBottom: '20px' }}
        />
      )}

      {!profile && !loading && !error && (
        <div className="empty" style={{ textAlign: 'center', padding: '32px', background: '#fff', borderRadius: '16px', boxShadow: '0 2px 8px rgba(0,0,0,0.04)' }}>
          <p style={{ fontWeight: 600 }}>Không tìm thấy thông tin hồ sơ khách hàng.</p>
          <p className="subtitle" style={{ marginBottom: '16px', color: 'var(--muted, #64748b)' }}>
            Vui lòng bấm Thử lại để kết nối với Customer Service.
          </p>
          <button type="button" className="btn btn-primary" onClick={loadProfile}>
            Thử lại
          </button>
        </div>
      )}

      {profile && (
        <>
          <div className="stats" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px', marginBottom: '24px' }}>
            <div className="stat" style={{ background: '#fff', padding: '16px', borderRadius: '12px', border: '1px solid var(--line, #e2e8f0)' }}>
              <span style={{ fontSize: '13px', color: 'var(--muted, #64748b)', display: 'block', marginBottom: '4px' }}>Trạng thái tài khoản</span>
              <strong style={{ fontSize: '18px', color: profile.status === 'ACTIVE' ? 'var(--ok, #16a34a)' : 'var(--warn, #d97706)' }}>
                {profile.status}
              </strong>
            </div>
            <div className="stat" style={{ background: '#fff', padding: '16px', borderRadius: '12px', border: '1px solid var(--line, #e2e8f0)' }}>
              <span style={{ fontSize: '13px', color: 'var(--muted, #64748b)', display: 'block', marginBottom: '4px' }}>Hạng khách hàng</span>
              <strong style={{ fontSize: '18px', color: '#0284c7' }}>{profile.loyaltyTier || 'BRONZE'}</strong>
            </div>
            <div className="stat" style={{ background: '#fff', padding: '16px', borderRadius: '12px', border: '1px solid var(--line, #e2e8f0)' }}>
              <span style={{ fontSize: '13px', color: 'var(--muted, #64748b)', display: 'block', marginBottom: '4px' }}>Lượt thuê thành công</span>
              <strong style={{ fontSize: '18px' }}>{profile.totalRentals || 0} chuyến</strong>
            </div>
          </div>

          <div className="media-section" style={{ background: '#fff', borderRadius: '16px', padding: '24px', marginBottom: '24px', boxShadow: '0 2px 8px rgba(0,0,0,0.04)', border: '1px solid var(--line, #e2e8f0)' }}>
            <div className="section-heading" style={{ marginBottom: '20px' }}>
              <h2 style={{ fontSize: '20px', margin: 0 }}>Thông tin cá nhân</h2>
            </div>
            <CustomerForm
              initialData={profile}
              onSave={async (fields) => {
                const updated = await updateMyProfile(fields)
                setProfile(updated)
              }}
            />
          </div>

          <div className="media-section" style={{ background: '#fff', borderRadius: '16px', padding: '24px', marginBottom: '24px', boxShadow: '0 2px 8px rgba(0,0,0,0.04)', border: '1px solid var(--line, #e2e8f0)' }}>
            <div className="section-heading" style={{ marginBottom: '20px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <h2 style={{ fontSize: '20px', margin: 0 }}>Giấy phép lái xe (GPLX)</h2>
                <span style={{ fontSize: '13px', color: 'var(--muted, #64748b)' }}>
                  Bắt buộc phải có bằng lái được duyệt để thuê xe
                </span>
              </div>
              {profile.driverLicense && (
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  style={{ padding: '6px 12px', fontSize: '13px' }}
                  onClick={() => setShowLicenseEdit((v) => !v)}
                >
                  {showLicenseEdit ? 'Đóng form sửa' : 'Chỉnh sửa / Cập nhật'}
                </button>
              )}
            </div>

            {!profile.driverLicense || showLicenseEdit ? (
              <DriverLicenseForm
                license={profile.driverLicense}
                onSubmitLicense={async (fields) => {
                  const updatedLicense = await submitDriverLicense(fields)
                  setProfile((prev) => ({ ...prev, driverLicense: updatedLicense }))
                  setShowLicenseEdit(false)
                }}
              />
            ) : (
              <DriverLicenseCard
                license={profile.driverLicense}
                onEditClick={() => setShowLicenseEdit(true)}
              />
            )}
          </div>

          <div className="media-section" style={{ background: '#fff', borderRadius: '16px', padding: '24px', marginBottom: '24px', boxShadow: '0 2px 8px rgba(0,0,0,0.04)', border: '1px solid var(--line, #e2e8f0)' }}>
            <div className="section-heading" style={{ marginBottom: '20px' }}>
              <h2 style={{ fontSize: '20px', margin: '0 0 4px' }}>Lịch sử chuyến đi</h2>
              <span style={{ fontSize: '13px', color: 'var(--muted, #64748b)' }}>
                Các chuyến xe đã thuê và đánh giá dịch vụ
              </span>
            </div>
            {rentalsError
              ? <p className="notice error" role="alert">{rentalsError}</p>
              : <RentalHistoryTable rentals={rentals} loading={rentalsLoading} />}
          </div>
        </>
      )}
    </div>
  )
}

export default ProfilePage
