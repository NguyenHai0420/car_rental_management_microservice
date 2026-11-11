import { useState } from 'react'
import { Link } from 'react-router-dom'
import { customerError } from '../services/customer'

export function CustomerForm({ initialData, onSave, cancelTo = '/' }) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  async function submit(event) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const fields = {
      fullName: form.get('fullName')?.trim(),
      email: form.get('email')?.trim() || null,
      phone: form.get('phone')?.trim() || null,
      dateOfBirth: form.get('dateOfBirth') || null,
      gender: form.get('gender') || null,
      avatarUrl: form.get('avatarUrl')?.trim() || null,
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
          Đường dẫn ảnh đại diện (Avatar URL)
          <input
            name="avatarUrl"
            defaultValue={initialData?.avatarUrl ?? ''}
            maxLength={500}
            placeholder="https://example.com/avatar.jpg"
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
