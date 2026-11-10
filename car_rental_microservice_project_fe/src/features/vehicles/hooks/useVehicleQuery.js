import { useEffect, useState } from 'react'
import { listVehicleCategories, listVehicleModels, vehicleError } from '../services/vehicle'

export function useVehicleQuery(load, formatError = vehicleError) {
  const [revision, setRevision] = useState(0)
  const [result, setResult] = useState(null)

  useEffect(() => {
    const controller = new AbortController()
    function finish(data, error = '') {
      if (!controller.signal.aborted) setResult({ load, revision, data, error })
    }
    load(controller.signal).then((data) => finish(data), (error) => finish(null, formatError(error)))
    return () => controller.abort()
  }, [load, revision, formatError])

  const current = result?.revision === revision && result.load === load
  return {
    data: current ? result.data : null,
    error: current ? result.error : '',
    loading: !current,
    reload: () => setRevision((value) => value + 1),
  }
}

async function loadCatalog(signal) {
  const [categories, models] = await Promise.all([listVehicleCategories(signal), listVehicleModels(signal)])
  return { categories, models }
}

export function useVehicleCatalog() {
  return useVehicleQuery(loadCatalog)
}
