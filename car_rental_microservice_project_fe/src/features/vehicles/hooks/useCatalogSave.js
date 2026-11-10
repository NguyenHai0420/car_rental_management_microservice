import { useState } from 'react'
import { vehicleError } from '../services/vehicle'

// Shared submit flow of the category and model forms: busy flag, reset, reload, notice.
export function useCatalogSave(reload) {
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  async function save(event, submit, success) {
    event.preventDefault()
    const form = event.currentTarget
    setSaving(true)
    setError('')
    setMessage('')
    try {
      await submit(Object.fromEntries(new FormData(form)))
      form.reset()
      reload()
      setMessage(success)
    } catch (failure) {
      setError(vehicleError(failure))
    } finally {
      setSaving(false)
    }
  }
  return { saving, message, error, save }
}
