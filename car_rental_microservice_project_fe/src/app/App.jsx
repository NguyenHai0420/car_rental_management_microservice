import { BrowserRouter, Route, Routes } from 'react-router'
import { AuthProvider } from './providers/AuthProvider'
import { VehicleManagementLayout } from '../features/vehicles/pages/VehicleManagementLayout'
import { VehicleCatalogPage } from '../features/vehicles/pages/VehicleCatalogPage'
import { VehicleDetailPage } from '../features/vehicles/pages/VehicleDetailPage'
import { VehicleFormPage } from '../features/vehicles/pages/VehicleFormPage'
import { VehicleListPage } from '../features/vehicles/pages/VehicleListPage'
import { VehicleMediaPage } from '../features/vehicles/pages/VehicleMediaPage'
import { HomePage } from '../features/home/HomePage'
import { PricingPage } from '../features/payments/pages/PricingPage'
import { PaymentPage } from '../features/payments/pages/PaymentPage'
import { PaymentCallbackPage } from '../features/payments/pages/PaymentCallbackPage'
import '../style/home.css'
import '../style/admin.css'
import '../style/payment.css'

export default function App() {
  return <AuthProvider><BrowserRouter>
    <Routes>
      <Route path="/vehicles" element={<VehicleManagementLayout />}>
        <Route index element={<VehicleListPage />} />
        <Route path="catalog" element={<VehicleCatalogPage />} />
        <Route path="new" element={<VehicleFormPage />} />
        <Route path=":id" element={<VehicleDetailPage />} />
        <Route path=":id/edit" element={<VehicleFormPage />} />
      </Route>
      <Route path="/lookup" element={<VehicleMediaPage />} />
      <Route path="/pricing" element={<PricingPage />} />
      <Route path="/rent" element={<PricingPage />} />
      <Route path="/payment" element={<PaymentPage />} />
      <Route path="/payment/callback" element={<PaymentCallbackPage />} />
      <Route path="*" element={<HomePage />} />
    </Routes>
  </BrowserRouter></AuthProvider>
}
