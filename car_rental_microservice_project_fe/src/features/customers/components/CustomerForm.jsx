import { useState } from 'react'
import { Link } from 'react-router-dom'
import { customerError, uploadCustomerImage } from '../services/customer'

export function CustomerForm({ initialData, onSave, cancelTo = '/' }) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [uploadingAvatar, setUploadingAvatar] = useState(false)
  const [avatarUrl, setAvatarUrl] = useState(initialData?.avatarUrl ?? '')

  async function handleAvatarChange(event) {
    const file = event.target.files?.[0]
    if (!file) return
    setError('')
    setUploadingAvatar(true)
    try {
      const res = await uploadCustomerImage(file)
      const url = res?.url || res
      setAvatarUrl(url)
    } catch (err) {
      setError(customerError(err))
    } finally {
      setUploadingAvatar(false)
    }
  }

  async function submit(event) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const fields = {
      fullName: form.get('fullName')?.trim(),
      email: form.get('email')?.trim() || null,
      phone: form.get('phone')?.trim() || null,
      dateOfBirth: form.get('dateOfBirth') || null,
      gender: form.get('gender') || null,
      avatarUrl: avatarUrl?.trim() || null,
    }

    setSaving(true)
    setError('')
    setSuccess('')
    try {
      await onSave(fields)
      setSuccess('Cập nhật thông tin hồ sơ thành công!')
    } catch (failure) {
      setError(customerError(failure))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} aria-busy={saving}>
      {error && (
        <p className="notice error" role="alert">
          {error}
        </p>
      )}
      {success && (
        <p className="notice" style={{ color: '#16a34a', borderColor: '#86efac' }} role="status">
          ✓ {success}
        </p>
      )}

      <fieldset className="fleet-fields" disabled={saving}>
        <label>
          Họ và tên
          <input
            name="fullName"
            defaultValue={initialData?.fullName ?? ''}
            required
            maxLength={100}
            placeholder="Nguyễn Văn A"
            autoComplete="name"
          />
        </label>

        <label>
          Email liên hệ
          <input
            name="email"
            type="email"
            defaultValue={initialData?.email ?? ''}
            required
            maxLength={150}
            placeholder="customer@carrental.vn"
            autoComplete="email"
          />
        </label>

        <label>
          Số điện thoại
          <input
            name="phone"
            defaultValue={initialData?.phone ?? ''}
            maxLength={20}
            placeholder="0987654321"
            autoComplete="tel"
          />
        </label>

        <label>
          Ngày sinh
          <input
            name="dateOfBirth"
            type="date"
            defaultValue={initialData?.dateOfBirth ?? ''}
          />
        </label>

        <label>
          Giới tính
          <select name="gender" defaultValue={initialData?.gender ?? 'MALE'}>
            <option value="MALE">Nam</option>
            <option value="FEMALE">Nữ</option>
            <option value="OTHER">Khác</option>
          </select>
        </label>

        <label style={{ gridColumn: '1 / -1' }}>
          Ảnh đại diện (Chọn tệp từ máy tính)
          <input
            type="file"
            accept="image/*"
            disabled={saving || uploadingAvatar}
            onChange={handleAvatarChange}
            style={{ marginBottom: 6, padding: '4px' }}
          />
          {uploadingAvatar && <span style={{ fontSize: '12px', color: '#0284c7', display: 'block', marginBottom: 4 }}>⏳ Đang tải ảnh lên MinIO...</span>}
          <input
            name="avatarUrl"
            value={avatarUrl}
            onChange={(e) => setAvatarUrl(e.target.value)}
            maxLength={500}
            placeholder="MinIO URL (tự động điền sau khi chọn tệp)"
            style={{ fontSize: '12px', color: 'var(--muted)' }}
          />
        </label>

        <div className="fleet-actions">
          <button type="submit" className="fleet-button">
            {saving ? 'Đang lưu…' : 'Lưu thay đổi'}
          </button>
          <Link to={cancelTo}>Hủy</Link>
        </div>
      </fieldset>
    </form>
  )
}
