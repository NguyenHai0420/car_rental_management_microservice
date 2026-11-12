import {
  Navigate,
  Outlet,
  Route,
  Routes,
} from 'react-router-dom'
import { Alert } from 'antd'
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
import { PricingPage } from '../features/payments/pages/PricingPage'
import { PaymentPage } from '../features/payments/pages/PaymentPage'
import { PaymentCallbackPage } from '../features/payments/pages/PaymentCallbackPage'
import { PaymentHistoryPage } from '../features/payments/pages/PaymentHistoryPage'
import { MaintenancePage } from '../features/maintenance/pages/MaintenancePage'
import { NotificationPage } from '../features/notifications/pages/NotificationPage'
import { SiteHeader } from '../features/home/SiteHeader'
import { SiteFooter } from '../features/home/SiteFooter'
import '../style/home.css'
import '../style/admin.css'
import '../style/payment.css'

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
      <Route element={<VehicleManagementLayout />}>
        <Route path="/vehicles">
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
        <Route
          path="/maintenance"
          element={
            <PrivateRoute allowedRoles={['ADMIN', 'STAFF', 'VEHICLE_OWNER']}>
              <MaintenancePage />
            </PrivateRoute>
          }
        />
        <Route
          path="/notifications"
          element={
            <PrivateRoute allowedRoles={['ADMIN', 'STAFF']}>
              <NotificationPage />
            </PrivateRoute>
          }
        />
      </Route>
      <Route path="/rent" element={<PricingPage />} />
      <Route path="/pricing" element={<PricingPage />} />
      <Route path="/payment" element={<PrivateRoute><PaymentPage /></PrivateRoute>} />
      <Route path="/payments" element={<PrivateRoute><PaymentPage /></PrivateRoute>} />
      <Route path="/payment/history" element={<PrivateRoute><PaymentHistoryPage /></PrivateRoute>} />
      <Route path="/payments/history" element={<PrivateRoute><PaymentHistoryPage /></PrivateRoute>} />
      <Route path="/payment/callback" element={<PaymentCallbackPage />} />
      <Route path="/payment/cancel" element={<PaymentCallbackPage />} />
      <Route element={<CustomerLayout />}>
        <Route path="/bookings" element={<BookingListPage />} />
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
