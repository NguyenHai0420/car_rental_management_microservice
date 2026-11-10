import { useId } from 'react'
import { modelLabel } from '../vehicle-fields'

export function VehicleCatalogSelects({ catalog, categoryId, modelId, required = false }) {
  const labelId = useId()
  const categories = catalog?.categories ?? []
  const models = catalog?.models ?? []
  return <>
    <label><span id={`${labelId}-category`}>Loại xe</span>
      <select aria-labelledby={`${labelId}-category`} name="categoryId"
        defaultValue={categoryId ?? ''} required={required} disabled={!categories.length}>
        <option value="">{required ? 'Chọn loại xe' : 'Tất cả loại xe'}</option>
        {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
      </select>
    </label>
    <label><span id={`${labelId}-model`}>Mẫu xe</span>
      <select aria-labelledby={`${labelId}-model`} name="modelId"
        defaultValue={modelId ?? ''} required={required} disabled={!models.length}>
        <option value="">{required ? 'Chọn mẫu xe' : 'Tất cả mẫu xe'}</option>
        {models.map((model) => <option key={model.id} value={model.id}>{modelLabel(model)}</option>)}
      </select>
    </label>
  </>
}
