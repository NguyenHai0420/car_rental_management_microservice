export function DriverLicenseCard({ license, onEditClick }) {
  if (!license) {
    return (
      <div className="empty" style={{ textAlign: 'center', padding: '24px' }}>
        <p>Bạn chưa nộp Giấy phép lái xe.</p>
        <p className="subtitle">Vui lòng nộp thông tin bằng lái bên dưới để đủ điều kiện thuê xe.</p>
      </div>
    )
  }

  const statusMap = {
    PENDING: { label: 'Đang chờ duyệt', color: 'var(--warn)', bg: 'var(--warn-soft)' },
    VERIFIED: { label: 'Đã xác thực', color: 'var(--ok)', bg: 'var(--ok-soft)' },
    REJECTED: { label: 'Bị từ chối', color: 'var(--danger, #ef4444)', bg: '#fee2e2' },
  }

  const currentStatus = statusMap[license.verificationStatus] || {
    label: license.verificationStatus,
    color: 'var(--muted)',
    bg: 'var(--surface-2)',
  }

  return (
    <div style={{ background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 'var(--r-md)', padding: '20px', marginBottom: '20px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px', flexWrap: 'wrap', gap: '8px' }}>
        <div>
          <span style={{ fontSize: '13px', color: 'var(--muted)', textTransform: 'uppercase', letterSpacing: '.05em' }}>
            Hạng {license.licenseClass}
          </span>
          <h3 style={{ margin: '2px 0 0', fontSize: '20px', letterSpacing: '-.02em' }}>
            {license.licenseNumber}
          </h3>
        </div>
        <span
          style={{
            padding: '4px 12px',
            borderRadius: '999px',
            fontSize: '12px',
            fontWeight: 700,
            color: currentStatus.color,
            background: currentStatus.bg,
          }}
        >
          ● {currentStatus.label}
        </span>
      </div>

      {license.verificationStatus === 'REJECTED' && license.rejectionReason && (
        <div className="notice error" style={{ marginBottom: '14px' }}>
          <strong>Lý do từ chối:</strong> {license.rejectionReason}
          <div style={{ marginTop: '4px', fontSize: '12px' }}>
            Vui lòng kiểm tra lại thông tin và ảnh chụp để gửi lại.
          </div>
        </div>
      )}

      <div className="fleet-detail-fields" style={{ marginBottom: '14px' }}>
        <div>
          <dt>Ngày cấp</dt>
          <dd>{license.issueDate || '—'}</dd>
        </div>
        <div>
          <dt>Ngày hết hạn</dt>
          <dd>{license.expiryDate || '—'}</dd>
        </div>
        <div>
          <dt>Cơ quan cấp</dt>
          <dd>{license.issuingAuthority || '—'}</dd>
        </div>
      </div>

      <div className="image-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', marginTop: '12px' }}>
        {license.frontImageUrl && (
          <div className="image-card">
            <img src={license.frontImageUrl} alt="Mặt trước GPLX" />
            <div className="card-body">
              <span className="badge">Mặt trước</span>
            </div>
          </div>
        )}
        {license.backImageUrl && (
          <div className="image-card">
            <img src={license.backImageUrl} alt="Mặt sau GPLX" />
            <div className="card-body">
              <span className="badge">Mặt sau</span>
            </div>
          </div>
        )}
      </div>

      {onEditClick && (
        <div style={{ marginTop: '16px' }}>
          <button type="button" className="secondary" onClick={onEditClick}>
            Chỉnh sửa / Nộp lại bằng lái
          </button>
        </div>
      )}
    </div>
  )
}
