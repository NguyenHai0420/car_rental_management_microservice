// Single-colour logos in public/brands (from the Simple Icons set). Unknown brands fall back to an initial.
const available = new Set(['toyota', 'honda', 'hyundai', 'kia', 'mazda', 'nissan', 'ford', 'mitsubishi', 'suzuki', 'mercedes', 'bmw', 'audi', 'volkswagen',
  'peugeot', 'mg', 'tesla', 'porsche', 'subaru', 'chevrolet', 'volvo', 'landrover', 'renault', 'skoda', 'citroen', 'jeep', 'fiat', 'mini'])
const aliases = { mercedesbenz: 'mercedes', vw: 'volkswagen', rangerover: 'landrover' }

export function brandLogo(brand) {
  const key = brand.toLowerCase().normalize('NFD').replace(/[^a-z0-9]/g, '')
  const slug = aliases[key] ?? key
  return available.has(slug) ? `${import.meta.env.BASE_URL}brands/${slug}.svg` : null
}
