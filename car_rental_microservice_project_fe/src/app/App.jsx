import {
  Navigate,
  Outlet,
  Route,
  Routes,
  useSearchParams,
} from 'react-router-dom'
import {
  Alert,
  Card,
  Descriptions,
  Result,
  Typography,
} from 'antd'
import { useAuth } from './providers/auth-context'
import { PrivateRoute } from './routes/PrivateRoute'
import { BookingListPage } from '../features/bookings/pages/BookingListPage'
import { AdminBookingManagementPage } from '../features/bookings/pages/AdminBookingManagementPage'
import { VehicleManagementLayout } from '../features/vehicles/pages/VehicleManagementLayout'
import { VehicleCategoryPage } from '../features/vehicles/pages/VehicleCategoryPage'
import { VehicleModelPage } from '../features/vehicles/pages/VehicleModelPage'
import { VehicleDetailPage } from '../features/vehicles/pages/VehicleDetailPage'
import { VehicleFormPage } from '../features/vehicles/pages/VehicleFormPage'
import { VehicleListPage } from '../features/vehicles/pages/VehicleListPage'
import { ProfilePage } from '../features/customers/pages/ProfilePage'
import { CustomerListPage } from '../features/customers/pages/CustomerListPage'
import { CustomerDetailPage } from '../features/customers/pages/CustomerDetailPage'
import { HomePage } from '../features/home/HomePage'
import { PaymentPage } from '../features/payments/pages/PaymentPage'
import { PaymentCallbackPage } from '../features/payments/pages/PaymentCallbackPage'
import { PaymentHistoryPage } from '../features/payments/pages/PaymentHistoryPage'
import { SiteHeader } from '../features/home/SiteHeader'
import { SiteFooter } from '../features/home/SiteFooter'
import '../style/home.css'
import '../style/admin.css'
import '../style/payment.css'

const { Text } = Typography

const ModulePlaceholder = ({ title, owner, serviceName }) => {
  const [searchParams] = useSearchParams()
  const bookingId = searchParams.get('bookingId')
  const bookingCode = searchParams.get('code')
  const deposit = searchParams.get('deposit')

  return (
    <Card>
      <Result
        status="info"
        title={title}
        subTitle={`Phân hệ giao diện ${serviceName} (${owner} phụ trách) đã được định tuyến sẵn qua API Gateway (:8080) và bảo vệ bởi Keycloak JWT.`}
        extra={
          bookingId && (
            <Descriptions
              bordered
              size="small"
              column={1}
              style={{ maxWidth: 520, margin: '0 auto', textAlign: 'left' }}
              items={[
                {
                  key: 'code',
                  label: 'Mã đơn chuyển sang từ Booking',
                  children: <Text strong>{bookingCode || bookingId}</Text>,
                },
                {
                  key: 'bookingId',
                  label: 'Booking UUID',
                  children: <Text code>{bookingId}</Text>,
                },
                {
                  key: 'deposit',
                  label: 'Số tiền cần thanh toán',
                  children: (
                    <Text type="danger" strong>
                      {Number(deposit || 0).toLocaleString('vi-VN')} ₫
                    </Text>
                  ),
                },
              ]}
            />
          )
        }
      />
    </Card>
  )
}

function CustomerLayout() {
  const { authError } = useAuth()

  return (
    <div className="site" style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: 'var(--bg, #fffaf5)' }}>
      <SiteHeader />
      <main style={{ flex: 1, padding: '28px 0 56px', width: '100%' }}>
        <div className="container" style={{ maxWidth: 1320 }}>
          {authError && (
            <Alert
              type="info"
              showIcon
              closable
              style={{ marginBottom: 16 }}
              message={authError}
            />
          )}
          <Outlet />
        </div>
      </main>
      <SiteFooter />
    </div>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/vehicles" element={<VehicleManagementLayout />}>
        <Route index element={<VehicleListPage />} />
        <Route path="categories" element={<VehicleCategoryPage />} />
        <Route path="models" element={<VehicleModelPage />} />
        <Route path="new" element={<VehicleFormPage />} />
        <Route path=":id" element={<VehicleDetailPage />} />
        <Route path=":id/edit" element={<VehicleFormPage />} />
        <Route path="bookings" element={<AdminBookingManagementPage />} />
        <Route path="customers" element={<CustomerListPage />} />
        <Route path="customers/:id" element={<CustomerDetailPage />} />
      </Route>
      <Route path="/payment" element={<PrivateRoute><PaymentPage /></PrivateRoute>} />
      <Route path="/payments" element={<PrivateRoute><PaymentPage /></PrivateRoute>} />
      <Route path="/payment/history" element={<PrivateRoute><PaymentHistoryPage /></PrivateRoute>} />
      <Route path="/payments/history" element={<PrivateRoute><PaymentHistoryPage /></PrivateRoute>} />
      <Route path="/payment/callback" element={<PaymentCallbackPage />} />
      <Route path="/payment/cancel" element={<PaymentCallbackPage />} />
      <Route element={<CustomerLayout />}>
        <Route path="/bookings" element={<BookingListPage />} />
        <Route
          path="/maintenance"
          element={
            <PrivateRoute allowedRoles={['ADMIN', 'VEHICLE_OWNER']}>
              <ModulePlaceholder
                title="Quản lý Bảo Dưỡng & Đăng Kiểm (Maintenance Service)"
                owner="Phong"
                serviceName="maintenance-service (:8085)"
              />
            </PrivateRoute>
          }
        />
        <Route
          path="/notifications"
          element={
            <PrivateRoute>
              <ModulePlaceholder
                title="Trung Tâm Thông Báo (Notification Service)"
                owner="Phong"
                serviceName="notification-service (:8086)"
              />
            </PrivateRoute>
          }
        />
        <Route
          path="/profile"
          element={
            <PrivateRoute>
              <ProfilePage />
            </PrivateRoute>
          }
        />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
