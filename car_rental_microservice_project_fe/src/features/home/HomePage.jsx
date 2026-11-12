import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  App,
  Button,
  Col,
  DatePicker,
  Input,
  Row,
  Select,
} from 'antd'
import {
  CalendarOutlined,
  CarOutlined,
  EnvironmentOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import { useAuth } from '../../app/providers/auth-context'
import { useVehicleQuery } from '../vehicles/hooks/useVehicleQuery'
import { listShowroom, vehicleError } from '../vehicles/services/vehicle'
import { matchesSearch } from '../vehicles/vehicle-fields'
import { BrandLogo } from './BrandLogo'
import { CarCard } from './CarCard'
import { HeroSlider } from './HeroSlider'
import { SiteFooter } from './SiteFooter'
import { hotline, hotlineHref } from './site'
import { SiteHeader } from './SiteHeader'
import { VehicleDialog } from './VehicleDialog'
import { BookingForm } from '../bookings/components/BookingForm'
import { bookingService } from '../bookings/services/booking'
import { mergeAvailableVehicles } from '../bookings/booking-vehicles'

const { RangePicker } = DatePicker
const PREVIEW = 8
const loadShowroom = (signal) => listShowroom({ size: 48 }, signal)
const showroomError = (error) =>
  error?.response ? vehicleError(error) : 'Không kết nối được máy chủ. Vui lòng thử lại.'
const group = (cars, key) =>
  [...Map.groupBy(cars, key)].sort((a, b) => b[1].length - a[1].length)

const getDefaultRange = () => [
  dayjs().add(1, 'day').hour(8).minute(0).second(0),
  dayjs().add(3, 'day').hour(8).minute(0).second(0),
]

export function HomePage() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const { authenticated, login } = useAuth()
  const showroom = useVehicleQuery(loadShowroom, showroomError)

  const [brand, setBrand] = useState('')
  const [category, setCategory] = useState('')
  const [term, setTerm] = useState('')
  const [dateRange, setDateRange] = useState(getDefaultRange)
  const [minSeats, setMinSeats] = useState(undefined)
  const [locationFilter, setLocationFilter] = useState('')
  const [availabilityFilters, setAvailabilityFilters] = useState(null)
  const loadAvailability = useCallback((signal) => availabilityFilters
    ? bookingService.searchAvailableVehicles(availabilityFilters, signal)
    : Promise.resolve(null), [availabilityFilters])
  const availability = useVehicleQuery(loadAvailability, showroomError)
  const availableCars = useMemo(() => mergeAvailableVehicles(
    availability.data || [], showroom.data?.content || []), [availability.data, showroom.data])
  const loading = showroom.loading || Boolean(availabilityFilters && availability.loading)
  const error = showroom.error || (availabilityFilters ? availability.error : '')
  const reload = () => {
    showroom.reload()
    if (availabilityFilters) availability.reload()
  }

  const [selected, setSelected] = useState(null)
  const [bookingVehicle, setBookingVehicle] = useState(null)
  const [submittingBooking, setSubmittingBooking] = useState(false)

  useEffect(() => {
    const hash = window.location.hash
    if (hash) {
      const id = hash.replace('#', '')
      if (id === 'top') {
        window.scrollTo({ top: 0, behavior: 'smooth' })
      } else {
        const el = document.getElementById(id)
        if (el) {
          setTimeout(() => el.scrollIntoView({ behavior: 'smooth' }), 150)
        }
      }
    }
  }, [])

  const cars = availabilityFilters
    ? (loading || error ? null : availableCars)
    : showroom.data?.content
  const brands = useMemo(() => group(cars ?? [], (car) => car.brand), [cars])
  const categories = useMemo(
    () => [...new Map((cars ?? []).map((car) => [car.categoryId, car.categoryName])).entries()],
    [cars]
  )
  const locations = useMemo(
    () => [...new Set((cars ?? []).map((car) => car.location).filter(Boolean))].slice(0, 6),
    [cars]
  )
  const slides = useMemo(() => (cars ?? []).filter((car) => car.imageUrl).slice(0, 5), [cars])

  const filteredCars = useMemo(() => {
    return (cars ?? []).filter((car) => {
      if (brand && car.brand !== brand) return false
      if (category && car.categoryId !== category) return false
      if (term.trim() && !matchesSearch(term, car.brand, car.modelName, car.categoryName)) return false
      if (minSeats && (!car.seatCount || car.seatCount < minSeats)) return false
      if (locationFilter.trim()) {
        const loc = (car.location || '').toLowerCase()
        if (!loc.includes(locationFilter.trim().toLowerCase())) return false
      }
      return true
    })
  }, [cars, brand, category, term, minSeats, locationFilter])

  const filtering = Boolean(
    availabilityFilters || brand || category || term.trim() || minSeats || locationFilter.trim()
  )
  const sections = useMemo(() => group(filteredCars, (car) => car.brand), [filteredCars])
  const total = filteredCars.length

  const pickBrand = (name) => {
    setBrand((current) => (current === name ? '' : name))
    setCategory('')
    setTerm('')
  }
  const showBrand = (name) => {
    setBrand(name)
    setCategory('')
    setTerm('')
  }
  const reset = () => {
    setBrand('')
    setCategory('')
    setTerm('')
    setMinSeats(undefined)
    setLocationFilter('')
    setAvailabilityFilters(null)
  }

  const handleSearch = () => {
    if (!dateRange?.[0] || !dateRange?.[1] || !dateRange[1].isAfter(dateRange[0])) {
      message.warning('Vui lòng chọn thời gian trả xe sau thời gian nhận xe.')
      return
    }
    setAvailabilityFilters({
      pickupDatetime: dateRange[0],
      returnDatetime: dateRange[1],
      minSeats,
      location: locationFilter,
    })
    document.getElementById('xe')?.scrollIntoView({ behavior: 'smooth' })
  }

  const handleBookVehicle = (car) => {
    if (!authenticated) {
      message.info('Vui lòng đăng nhập để đặt thuê xe.')
      login()
      return
    }
    setBookingVehicle(car)
  }

  const handleCreateBooking = async (payload) => {
    setSubmittingBooking(true)
    try {
      const created = await bookingService.createBooking(payload)
      return created
    } catch (err) {
      message.error(err.userMessage || 'Tạo đơn đặt xe thất bại.')
      throw err
    } finally {
      setSubmittingBooking(false)
    }
  }

  return (
    <div className="site" id="top">
      <SiteHeader />
      <main>
        <HeroSlider cars={slides} onOpen={setSelected} />

        {/* BỘ LỌC TÌM XE TRỐNG TÍCH HỢP TRỰC TIẾP TRÊN TRANG CHỦ */}
        <div className="container finder-wrap" id="tim-xe">
          <div className="finder">
            <Row gutter={[16, 16]} align="bottom">
              <Col xs={24} md={10} lg={9}>
                <div style={{ marginBottom: 6, fontWeight: 600, fontSize: 13, color: 'var(--title)' }}>
                  <CalendarOutlined style={{ color: '#ff6a1a', marginRight: 6 }} /> Khoảng thời gian thuê xe
                </div>
                <RangePicker
                  size="large"
                  value={dateRange}
                  onChange={(vals) => setDateRange(vals)}
                  showTime={{ format: 'HH:mm' }}
                  format="DD/MM/YYYY HH:mm"
                  style={{ width: '100%' }}
                  disabledDate={(current) => current && current < dayjs().startOf('day')}
                />
              </Col>
              <Col xs={12} sm={8} lg={4}>
                <div style={{ marginBottom: 6, fontWeight: 600, fontSize: 13, color: 'var(--title)' }}>
                  <CarOutlined style={{ color: '#ff6a1a', marginRight: 6 }} /> Số chỗ tối thiểu
                </div>
                <Select
                  size="large"
                  allowClear
                  placeholder="Tất cả số chỗ"
                  style={{ width: '100%' }}
                  value={minSeats}
                  onChange={setMinSeats}
                  options={[
                    { value: 4, label: 'Từ 4 chỗ' },
                    { value: 5, label: 'Từ 5 chỗ' },
                    { value: 7, label: 'Từ 7 chỗ' },
                    { value: 16, label: 'Từ 16 chỗ' },
                  ]}
                />
              </Col>
              <Col xs={12} sm={8} lg={5}>
                <div style={{ marginBottom: 6, fontWeight: 600, fontSize: 13, color: 'var(--title)' }}>
                  <EnvironmentOutlined style={{ color: '#ff6a1a', marginRight: 6 }} /> Khu vực / Chi nhánh
                </div>
                <Input
                  size="large"
                  allowClear
                  placeholder="VD: Quận 1, Hoàng Mai, Cầu Giấy..."
                  value={locationFilter}
                  onChange={(e) => setLocationFilter(e.target.value)}
                  onPressEnter={handleSearch}
                />
              </Col>
              <Col xs={16} sm={8} lg={4}>
                <div style={{ marginBottom: 6, fontWeight: 600, fontSize: 13, color: 'var(--title)' }}>
                  <SearchOutlined style={{ color: '#ff6a1a', marginRight: 6 }} /> Tìm theo tên
                </div>
                <Input
                  size="large"
                  allowClear
                  placeholder="Vios, Camry, VF 8..."
                  value={term}
                  onChange={(e) => {
                    setTerm(e.target.value)
                    setBrand('')
                  }}
                  onPressEnter={handleSearch}
                />
              </Col>
              <Col xs={8} sm={24} lg={2}>
                <Button
                  size="large"
                  type="primary"
                  style={{
                    width: '100%',
                    fontWeight: 700,
                    background: 'linear-gradient(135deg, #ff8a1f, #ff6a1a)',
                    borderColor: '#ff6a1a',
                  }}
                  onClick={handleSearch}
                  loading={Boolean(availabilityFilters && availability.loading)}
                >
                  <SearchOutlined /> Tìm xe
                </Button>
              </Col>
            </Row>
          </div>
        </div>

        {brands.length > 0 && (
          <section className="container brand-tiles" id="hang-xe" aria-label="Hãng xe">
            <ul className="brand-row">
              {brands.map(([name, list]) => (
                <li key={name}>
                  <button
                    className="secondary w-100 h-100"
                    aria-pressed={brand === name}
                    onClick={() => {
                      pickBrand(name)
                      document.getElementById('xe')?.scrollIntoView({ behavior: 'smooth' })
                    }}
                  >
                    <BrandLogo brand={name} className="tile-logo" />
                    <strong>{name}</strong>
                    <small>{list.length} xe</small>
                  </button>
                </li>
              ))}
            </ul>
          </section>
        )}

        <div className="container fleet" id="xe">
          {loading && (
            <ul className="row row-cols-1 row-cols-sm-2 row-cols-lg-4 g-3" aria-busy="true" aria-label="Đang tải">
              {Array.from({ length: 4 }, (_, i) => (
                <li key={i} className="col">
                  <div className="skeleton" />
                </li>
              ))}
            </ul>
          )}
          {error && (
            <div className="state" role="alert">
              <p>{error}</p>
              <button className="btn btn-primary" onClick={reload}>
                Thử lại
              </button>
            </div>
          )}
          {categories.length > 1 && (
            <div className="chips" role="group" aria-label="Lọc theo loại xe">
              <button className="chip" aria-pressed={!category} onClick={() => setCategory('')}>
                Tất cả
              </button>
              {categories.map(([id, name]) => (
                <button
                  key={id}
                  className="chip"
                  aria-pressed={category === id}
                  onClick={() => {
                    setCategory(id)
                    setBrand('')
                  }}
                >
                  {name}
                </button>
              ))}
            </div>
          )}
          {cars && filtering && (
            <p className="result-bar" role="status">
              {total} xe phù hợp{' '}
              <button className="btn btn-link btn-sm p-0 link" onClick={reset}>
                Xóa bộ lọc
              </button>
            </p>
          )}
          {cars &&
            (sections.length ? (
              sections.map(([name, list]) => {
                const shown = filtering ? list : list.slice(0, PREVIEW)
                return (
                  <section key={name} className="brand-section" aria-label={name}>
                    <header>
                      <h2>
                        <BrandLogo brand={name} />
                        {name}
                      </h2>
                      <span className="count">{list.length} xe</span>
                      {!filtering && list.length > PREVIEW && (
                        <button className="btn btn-sm btn-outline-danger see-all" onClick={() => pickBrand(name)}>
                          Xem tất cả ({list.length})
                        </button>
                      )}
                    </header>
                    <ul className="row row-cols-1 row-cols-sm-2 row-cols-lg-4 g-3 mt-0">
                      {shown.map((car) => (
                        <CarCard
                          key={car.id}
                          car={car}
                          onOpen={setSelected}
                          onBook={handleBookVehicle}
                        />
                      ))}
                    </ul>
                  </section>
                )
              })
            ) : (
              !loading && (
                <div className="state">
                  <p>{cars.length ? 'Không có xe khớp bộ lọc.' : 'Hiện chưa có xe sẵn sàng. Vui lòng quay lại sau.'}</p>
                  {cars.length > 0 && (
                    <button className="btn btn-outline-secondary" onClick={reset}>
                      Xóa bộ lọc
                    </button>
                  )}
                </div>
              )
            ))}
        </div>

        <section className="steps" aria-labelledby="steps-title">
          <div className="container">
            <h2 id="steps-title">Thuê xe chỉ với 3 bước</h2>
            <ol className="row g-3">
              {[
                ['bi-search-heart', 'Chọn xe', 'Xem hình ảnh thật, giá theo ngày và thông số từng xe.'],
                ['bi-chat-dots', 'Liên hệ & xác nhận', 'Nhân viên tư vấn xác nhận lịch nhận xe và các giấy tờ cần thiết.'],
                ['bi-key', 'Nhận xe & lên đường', 'Kiểm tra xe cùng nhân viên, ký nhận và bắt đầu hành trình.'],
              ].map(([icon, title, text], i) => (
                <li className="col-md-4" key={title}>
                  <div className="step">
                    <span className="step-no">{i + 1}</span>
                    <i className={`bi ${icon}`} aria-hidden="true" />
                    <h3>{title}</h3>
                    <p>{text}</p>
                  </div>
                </li>
              ))}
            </ol>
          </div>
        </section>
      </main>
      <SiteFooter brands={brands.map(([name]) => name)} locations={locations} onBrand={showBrand} />
      <a className="fab" href={hotlineHref} aria-label={hotline ? `Gọi ${hotline}` : 'Liên hệ'}>
        <i className="bi bi-telephone-fill" aria-hidden="true" />
      </a>
      {selected && (
        <VehicleDialog
          car={selected}
          onClose={() => setSelected(null)}
          onBook={handleBookVehicle}
        />
      )}

      {/* MODAL ĐẶT THUÊ XE TRỰC TIẾP TỪ TRANG CHỦ */}
      {bookingVehicle && (
        <BookingForm
          open={Boolean(bookingVehicle)}
          vehicle={bookingVehicle}
          defaultDateRange={dateRange}
          submitting={submittingBooking}
          onCancel={() => setBookingVehicle(null)}
          onSubmit={handleCreateBooking}
        />
      )}
    </div>
  )
}
