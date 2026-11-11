import { useLocation, useNavigate } from 'react-router-dom'
import { hotline, hotlineHref } from './site'

export function SiteFooter({ brands = [], locations = [], onBrand = () => {} }) {
  const location = useLocation()
  const navigate = useNavigate()
  const isHome = location.pathname === '/'

  const handleBrandClick = (event, brand) => {
    event.preventDefault()
    onBrand(brand)
    if (isHome) {
      document.getElementById('xe')?.scrollIntoView({ behavior: 'smooth' })
    } else {
      navigate('/#xe')
    }
  }

  const handleAllCarsClick = (event) => {
    event.preventDefault()
    if (isHome) {
      document.getElementById('xe')?.scrollIntoView({ behavior: 'smooth' })
    } else {
      navigate('/#xe')
    }
  }

  return <footer className="site-footer" id="lien-he">
    <div className="container py-5"><div className="row g-4">
      <section className="col-lg-4"><h2>CarRental</h2>
        <p>Dịch vụ thuê xe tự lái với đội xe được kiểm định, hình ảnh thật và giá theo ngày minh bạch.</p>
        <a className="btn btn-danger" href={hotlineHref}><i className="bi bi-telephone-fill me-2" aria-hidden="true" />{hotline ? `Hotline: ${hotline}` : 'Liên hệ nhân viên tư vấn'}</a></section>
      <section className="col-6 col-lg-2"><h2>Hãng xe</h2>
        <ul>{brands.length ? brands.slice(0, 8).map((brand) => <li key={brand}><a href="#xe" onClick={(e) => handleBrandClick(e, brand)}>{brand}</a></li>) : <li><a href="#xe" onClick={handleAllCarsClick}>Xem toàn bộ xe</a></li>}</ul></section>
      <section className="col-6 col-lg-3"><h2>Thông tin</h2>
        <ul><li>Thuê xe có hoặc không có tài xế</li><li>Cần giấy phép lái xe và CCCD khi nhận xe</li><li>Đặt cọc và thanh toán minh bạch</li><li>Hỗ trợ khách hàng 24/7</li></ul></section>
      <section className="col-lg-3"><h2>Điểm nhận xe</h2>
        <ul>{locations.length ? locations.map((place) => <li key={place}><i className="bi bi-geo-alt-fill me-2" aria-hidden="true" />{place}</li>) : <li>Chi nhánh Hà Nội, TP.HCM, Đà Nẵng</li>}</ul></section>
    </div></div>
    <div className="copyright"><div className="container py-3">© {new Date().getFullYear()} CarRental. Thuê xe tự lái an toàn, minh bạch.</div></div>
  </footer>
}
