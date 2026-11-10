export function Pager({ paging, label }) {
  if (paging.total <= paging.size) return null
  return <nav className="fleet-pagination" aria-label={label}>
    <span>{paging.total} mục · Trang {paging.page + 1} / {paging.pages}</span>
    <div className="fleet-actions">
      <button className="secondary" disabled={paging.page === 0} onClick={() => paging.setPage(paging.page - 1)}>Trang trước</button>
      <button className="secondary" disabled={paging.page + 1 >= paging.pages} onClick={() => paging.setPage(paging.page + 1)}>Trang sau</button>
    </div>
  </nav>
}
