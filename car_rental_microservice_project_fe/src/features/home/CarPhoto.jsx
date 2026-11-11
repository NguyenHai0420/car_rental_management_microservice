import { useState } from 'react'

export function CarPhoto({ src, alt }) {
  const [failed, setFailed] = useState(false)
  if (!src || failed) return <span className="fallback" role="img" aria-label="Chưa có ảnh xe">🚘</span>
  return <img src={src} alt={alt} loading="lazy" onError={() => setFailed(true)} />
}
