import { brandLogo } from './brands'

// Logo is a CSS mask so it follows the text colour (works in light and dark mode).
export function BrandLogo({ brand, className = '' }) {
  const url = brandLogo(brand)
  return url
    ? <span className={`brand-logo ${className}`} style={{ '--logo': `url(${url})` }} aria-hidden="true" />
    : <span className={`brand-logo initial ${className}`} aria-hidden="true">{brand.slice(0, 1)}</span>
}
