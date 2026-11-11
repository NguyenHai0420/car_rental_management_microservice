import { vehicleStatuses } from '../vehicle-fields'

export function VehicleStatusBadge({ status }) {
  return <span className="fleet-status" data-status={status}>{vehicleStatuses[status] || status || '—'}</span>
}
