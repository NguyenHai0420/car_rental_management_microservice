// Shared by header, footer and floating button; set VITE_HOTLINE in .env to show a phone number.
export const hotline = import.meta.env.VITE_HOTLINE || ''
export const hotlineHref = hotline ? `tel:${hotline.replace(/[^\d+]/g, '')}` : '#lien-he'
