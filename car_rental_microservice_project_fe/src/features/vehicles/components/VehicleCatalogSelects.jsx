import { useId } from 'react'
import { modelLabel } from '../vehicle-fields'

export function VehicleCatalogSelects({ catalog, categoryId, modelId }) {
  const labelId = useId()
  const categories = catalog?.categories ?? []
  const models = catalog?.models ?? []
  return <>
    <label><span id={`${labelId}-category`}>Loại xe</span>
      <select aria-labelledby={`${labelId}-category`} name="categoryId"
        defaultValue={categoryId ?? ''} disabled={!categories.length}>
        <option value="">Tất cả loại xe</option>
        {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
      </select>
    </label>
    <label><span id={`${labelId}-model`}>Mẫu xe</span>
      <select aria-labelledby={`${labelId}-model`} name="modelId"
        defaultValue={modelId ?? ''} disabled={!models.length}>
        <option value="">Tất cả mẫu xe</option>
        {models.map((model) => <option key={model.id} value={model.id}>{modelLabel(model)}</option>)}
      </select>
    </label>
  </>
}

// Vehicle form: the model is the only choice, its category follows automatically.
export function ModelSelect({ catalog, modelId }) {
  const labelId = useId()
  const groups = catalog.categories.map((category) => [category, catalog.models.filter((model) => model.categoryId === category.id)])
    .filter(([, models]) => models.length)
  return <label><span id={labelId}>Mẫu xe</span>
    <select aria-labelledby={labelId} name="modelId" defaultValue={modelId ?? ''} required>
      <option value="">Chọn mẫu xe</option>
      {groups.map(([category, models]) => <optgroup key={category.id} label={category.name}>
        {models.map((model) => <option key={model.id} value={model.id}>{modelLabel(model)}</option>)}
      </optgroup>)}
    </select>
    <span className="hint">Loại xe được lấy theo mẫu xe đã chọn.</span>
  </label>
}
