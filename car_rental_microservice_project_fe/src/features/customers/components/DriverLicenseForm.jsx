import { useState } from 'react'
import { customerError } from '../services/customer'

export function DriverLicenseForm({ license, onSubmitLicense }) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [frontUrl, setFrontUrl] = useState(license?.frontImageUrl ?? '')
  const [backUrl, setBackUrl] = useState(license?.backImageUrl ?? '')

  async function submit(event) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const fields = {
      licenseNumber: form.get('licenseNumber')?.trim(),
      licenseClass: form.get('licenseClass'),
      issueDate: form.get('issueDate'),
      expiryDate: form.get('expiryDate'),
      issuingAuthority: form.get('issuingAuthority')?.trim() || null,
      frontImageUrl: form.get('frontImageUrl')?.trim(),
      backImageUrl: form.get('backImageUrl')?.trim(),
    }

    setSaving(true)
    setError('')
    setSuccess('')
    try {
      await onSubmitLicense(fields)
      setSuccess('Đã gửi thông tin bằng lái xe! Trạng thái đang chờ nhân viên kiểm duyệt.')
    } catch (failure) {
      setError(customerError(failure))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} className="upload-form" aria-busy={saving}>
      <legend>Nộp / Cập nhật Giấy phép lái xe (GPLX)</legend>
      <p className="hint">
        Theo quy định, bạn cần cung cấp bằng lái xe hợp lệ để hệ thống xác nhận điều kiện đặt thuê xe.
      </p>

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
          Số giấy phép lái xe
          <input
            name="licenseNumber"
            defaultValue={license?.licenseNumber ?? ''}
            required
            maxLength={50}
            placeholder="Ví dụ: B2-790123456"
          />
        </label>

        <label>
          Hạng bằng lái
          <select name="licenseClass" defaultValue={license?.licenseClass ?? 'B2'}>
            <option value="B1">Hạng B1 (Xe số tự động đến 9 chỗ)</option>
            <option value="B2">Hạng B2 (Xe số sàn & tự động đến 9 chỗ)</option>
            <option value="C">Hạng C (Xe tải & xe chở người đến 9 chỗ)</option>
            <option value="D">Hạng D (Xe chở người từ 10 - 30 chỗ)</option>
          </select>
        </label>

        <label>
          Ngày cấp
          <input
            name="issueDate"
            type="date"
            defaultValue={license?.issueDate ?? ''}
            required
          />
        </label>

        <label>
          Ngày hết hạn
          <input
            name="expiryDate"
            type="date"
            defaultValue={license?.expiryDate ?? ''}
            required
          />
        </label>

        <label style={{ gridColumn: '1 / -1' }}>
          Cơ quan cấp bằng
          <input
            name="issuingAuthority"
            defaultValue={license?.issuingAuthority ?? 'Sở Giao thông Vận tải'}
            maxLength={100}
            placeholder="Sở Giao thông Vận tải TP.HCM"
          />
        </label>

        <label>
          Đường dẫn ảnh mặt trước (Front Image URL)
          <input
            name="frontImageUrl"
            value={frontUrl}
            onChange={(e) => setFrontUrl(e.target.value)}
            required
            maxLength={500}
            placeholder="https://..."
          />
        </label>

        <label>
          Đường dẫn ảnh mặt sau (Back Image URL)
          <input
            name="backImageUrl"
            value={backUrl}
            onChange={(e) => setBackUrl(e.target.value)}
            required
            maxLength={500}
            placeholder="https://..."
          />
        </label>

        {(frontUrl || backUrl) && (
          <div style={{ gridColumn: '1 / -1', display: 'flex', gap: '16px', marginTop: '8px' }}>
            {frontUrl && (
              <div style={{ flex: 1 }}>
                <span style={{ fontSize: '12px', color: 'var(--muted)' }}>Xem trước mặt trước:</span>
                <img
                  src={frontUrl}
                  alt="Mặt trước GPLX"
                  style={{ width: '100%', height: '140px', objectFit: 'cover', borderRadius: '8px', border: '1px solid var(--line)' }}
                  onError={(e) => { e.currentTarget.style.display = 'none' }}
                />
              </div>
            )}
            {backUrl && (
              <div style={{ flex: 1 }}>
                <span style={{ fontSize: '12px', color: 'var(--muted)' }}>Xem trước mặt sau:</span>
                <img
                  src={backUrl}
                  alt="Mặt sau GPLX"
                  style={{ width: '100%', height: '140px', objectFit: 'cover', borderRadius: '8px', border: '1px solid var(--line)' }}
                  onError={(e) => { e.currentTarget.style.display = 'none' }}
                />
              </div>
            )}
          </div>
        )}

        <div className="fleet-actions">
          <button type="submit" className="fleet-button">
            {saving ? 'Đang gửi…' : license ? 'Cập nhật bằng lái' : 'Gửi duyệt bằng lái'}
          </button>
        </div>
      </fieldset>
    </form>
  )
}
