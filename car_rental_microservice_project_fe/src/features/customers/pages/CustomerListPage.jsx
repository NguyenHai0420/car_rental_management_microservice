import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getAllCustomers, customerError } from '../services/customer'

export function CustomerListPage() {
  const [customers, setCustomers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [keyword, setKeyword] = useState('')

  useEffect(() => {
    loadCustomers()
  }, [])

  async function loadCustomers() {
    setLoading(true)
    setError('')
    try {
      const data = await getAllCustomers()
      setCustomers(data || [])
    } catch (err) {
      setError(customerError(err))
    } finally {
      setLoading(false)
    }
  }

  const filtered = customers.filter((c) => {
    const q = keyword.toLowerCase()
    return (
      c.fullName?.toLowerCase().includes(q) ||
      c.email?.toLowerCase().includes(q) ||
      c.phone?.toLowerCase().includes(q)
    )
  })

  return (
    <section className="media-section" aria-labelledby="customer-list-heading">
      <div className="section-heading">
        <h2 id="customer-list-heading">
          Danh sách khách hàng <span className="count">{customers.length}</span>
        </h2>
        <button className="secondary" onClick={loadCustomers} disabled={loading}>
          {loading ? 'Đang tải…' : 'Làm mới'}
        </button>
      </div>

      <div className="fleet-filters" style={{ gridTemplateColumns: 'minmax(250px, 2fr) auto' }}>
        <label style={{ margin: 0 }}>
          Tìm kiếm khách hàng
          <input
            type="search"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Tìm theo tên, email, số điện thoại…"
          />
        </label>
      </div>

      {error && (
        <div className="notice error">
          <p role="alert">{error}</p>
          <button className="secondary" onClick={loadCustomers}>
            Thử lại
          </button>
        </div>
      )}

      {loading && <p className="empty">Đang tải danh sách khách hàng từ Customer Service…</p>}

      {!loading && !error && filtered.length === 0 && (
        <p className="empty">Không tìm thấy khách hàng nào.</p>
      )}

      {!loading && !error && filtered.length > 0 && (
        <div className="fleet-table-scroll">
          <table className="fleet-table">
            <thead>
              <tr>
                <th>Họ và tên</th>
                <th>Email</th>
                <th>Số điện thoại</th>
                <th>Trạng thái</th>
                <th>Hạng</th>
                <th>Bằng lái (GPLX)</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((c) => {
                const licenseStatus = c.driverLicense?.verificationStatus ?? 'CHƯA NỘP'
                const statusBadgeStyle = {
                  VERIFIED: { color: 'var(--ok)', bg: 'var(--ok-soft)' },
                  PENDING: { color: 'var(--warn)', bg: 'var(--warn-soft)' },
                  REJECTED: { color: 'var(--danger, #ef4444)', bg: '#fee2e2' },
                }[licenseStatus] || { color: 'var(--muted)', bg: 'var(--surface-2)' }

                return (
                  <tr key={c.id}>
                    <th scope="row">
                      <Link to={`/vehicles/customers/${c.id}`} style={{ textDecoration: 'none' }}>
                        {c.fullName || 'Chưa đặt tên'}
                      </Link>
                    </th>
                    <td>{c.email}</td>
                    <td>{c.phone || '—'}</td>
                    <td>
                      <span className="badge" style={{ fontSize: '11px' }}>
                        {c.status}
                      </span>
                    </td>
                    <td>
                      <strong>{c.loyaltyTier}</strong>
                    </td>
                    <td>
                      <span
                        style={{
                          padding: '2px 8px',
                          borderRadius: '999px',
                          fontSize: '11px',
                          fontWeight: 700,
                          color: statusBadgeStyle.color,
                          background: statusBadgeStyle.bg,
                        }}
                      >
                        {licenseStatus}
                      </span>
                    </td>
                    <td>
                      <Link
                        to={`/vehicles/customers/${c.id}`}
                        className="fleet-button"
                        style={{ padding: '4px 10px', fontSize: '12px' }}
                      >
                        Chi tiết / Duyệt
                      </Link>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
