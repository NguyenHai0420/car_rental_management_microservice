import { AuthProvider } from './providers/AuthProvider'
import { VehicleMediaPage } from '../features/vehicles/pages/VehicleMediaPage'
import '../style/vehicle-media.css'

export default function App() {
  return <AuthProvider><VehicleMediaPage /></AuthProvider>
}
