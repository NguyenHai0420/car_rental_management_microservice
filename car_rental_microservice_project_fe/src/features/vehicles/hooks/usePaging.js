import { useState } from 'react'

// Client-side paging: the catalog endpoints return the whole list (the selects need all of it).
export function usePaging(items, size = 10) {
  const [page, setPage] = useState(0)
  const pages = Math.max(1, Math.ceil(items.length / size))
  const current = Math.min(page, pages - 1)
  return { page: current, pages, total: items.length, setPage, visible: items.slice(current * size, (current + 1) * size), size }
}
