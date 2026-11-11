import { useState } from 'react'
import { vehicleError } from '../services/vehicle'

// Shared flow of the category and model pages: busy flag, notices, reload after a change.
export function useCatalogSave(reload) {
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  // Resolves true on success. `describe` turns a failure into the text shown to the user.
  async function run(action, success, describe = vehicleError) {
    setSaving(true)
    setError('')
    setMessage('')
    try {
      await action()
      reload()
      setMessage(success)
      return true
    } catch (failure) {
      setError(describe(failure))
      return false
    } finally {
      setSaving(false)
    }
  }

  async function save(event, submit, success) {
    event.preventDefault()
    const form = event.currentTarget
    const saved = await run(() => submit(Object.fromEntries(new FormData(form))), success)
    if (saved) form.reset()
    return saved
  }
  return { saving, message, error, save, run }
}
