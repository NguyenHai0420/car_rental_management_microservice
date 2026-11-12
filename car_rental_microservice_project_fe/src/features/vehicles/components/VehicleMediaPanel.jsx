import { useState } from 'react'
import { mediaError, useVehicleMedia } from '../hooks/useVehicleMedia'
import {
  deleteVehicleDocument, deleteVehicleImage, getVehicleDocumentUrl,
  uploadVehicleDocument, uploadVehicleImages,
} from '../services/vehicle'

const imageTypes = {
  EXTERIOR_FRONT: 'Ngoại thất phía trước', EXTERIOR_REAR: 'Ngoại thất phía sau',
  EXTERIOR_SIDE: 'Ngoại thất bên hông', INTERIOR: 'Nội thất', DASHBOARD: 'Bảng điều khiển',
  TRUNK: 'Khoang hành lý', LICENSE_PLATE: 'Biển số', OTHER: 'Ảnh khác',
}
const documentTypes = {
  REGISTRATION: 'Đăng ký xe', INSURANCE: 'Bảo hiểm', INSPECTION_CERT: 'Đăng kiểm',
  ROAD_TAX: 'Phí đường bộ', WARRANTY: 'Bảo hành', OTHER: 'Tài liệu khác',
}
const imageAccept = 'image/jpeg,image/png,image/webp'
const documentAccept = `${imageAccept},application/pdf`
const fileSize = (bytes) => `${((bytes || 0) / 1024 / 1024).toFixed(2)} MB`

function UploadForm({ document = false, disabled, onUpload }) {
  const [error, setError] = useState('')
  async function submit(event) {
    event.preventDefault()
    const form = event.currentTarget
    const fields = new FormData(form)
    const files = fields.getAll('file')
    const accepted = (document ? documentAccept : imageAccept).split(',')
    const wrongType = files.find((file) => !file?.size || !accepted.includes(file.type))
    if (!files.length || wrongType) {
      setError(wrongType?.name ? `Tệp "${wrongType.name}" không đúng định dạng được hỗ trợ.` : 'Vui lòng chọn tệp đúng định dạng được hỗ trợ.')
      return
    }
    const tooLarge = files.find((file) => file.size > 10 * 1024 * 1024)
    if (tooLarge) {
      setError(`Tệp "${tooLarge.name}" vượt quá 10 MB. Dung lượng tối đa cho mỗi tệp là 10 MB.`)
      return
    }
    setError('')
    fields.delete('file')
    if (await onUpload(document ? files[0] : files, Object.fromEntries(fields))) form.reset()
  }

  return <form className="upload-form" onSubmit={submit}>
    <fieldset disabled={disabled}>
      <legend>{document ? 'Thêm giấy tờ' : 'Thêm ảnh xe'}</legend>
      <label>Tệp {document ? 'giấy tờ' : 'ảnh'}
        <input name="file" type="file" accept={document ? documentAccept : imageAccept} multiple={!document} required />
      </label>
      <p className="hint">{document ? 'PDF, JPEG, PNG hoặc WebP · Tối đa 10 MB'
        : 'Chọn hoặc kéo thả nhiều ảnh cùng lúc · JPEG, PNG hoặc WebP · Tối đa 10 MB mỗi ảnh'}</p>
      <label>Phân loại
        <select name={document ? 'docType' : 'imageType'}>
          {Object.entries(document ? documentTypes : imageTypes).map(([value, label]) =>
            <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
      {document ? <>
        <label>Số giấy tờ<input name="docNumber" maxLength={100} /></label>
        <div className="form-row">
          <label>Ngày cấp<input name="issueDate" type="date" /></label>
          <label>Ngày hết hạn<input name="expiryDate" type="date" /></label>
        </div>
      </> : <>
        <label>Thứ tự hiển thị (ảnh sau tự tăng dần)<input name="sortOrder" type="number" min="0" step="1" defaultValue="0" required /></label>
        <label className="checkbox"><input name="isPrimary" type="checkbox" /> Đặt ảnh đầu tiên làm ảnh đại diện</label>
      </>}
      {error && <p className="notice error" role="alert">{error}</p>}
      <button type="submit">{disabled ? 'Đang xử lý…' : document ? 'Tải giấy tờ lên' : 'Tải ảnh lên'}</button>
    </fieldset>
  </form>
}

export function VehicleMediaPanel({ vehicleId, canManage }) {
  const { images, documents, loading, error, refresh } = useVehicleMedia(vehicleId, canManage)
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')
  const [message, setMessage] = useState('')
  const [uploads, setUploads] = useState([])

  async function uploadImages(files, fields) {
    setBusy(true)
    setActionError('')
    setMessage('')
    setUploads(files.map((file) => ({ name: file.name, status: 'Đang chờ…' })))
    try {
      const results = await uploadVehicleImages(vehicleId, files, fields.imageType, {
        isPrimary: fields.isPrimary === 'on', sortOrder: Number(fields.sortOrder),
      }, (result, index) => setUploads((list) => list.map((item, i) => i !== index ? item
        : { ...item, failed: Boolean(result.error), status: result.error ? mediaError(result.error) : 'Đã tải lên' })))
      const failed = results.filter((result) => result.error).length
      if (failed < results.length) await refresh()
      if (failed) setActionError(`${failed}/${results.length} ảnh tải lên thất bại. Xem chi tiết bên dưới.`)
      else setMessage(`Đã tải lên ${results.length} ảnh.`)
      return !failed
    } catch (failure) {
      setActionError(mediaError(failure))
      return false
    } finally {
      setBusy(false)
    }
  }

  async function perform(action, success) {
    setBusy(true)
    setActionError('')
    setMessage('')
    try {
      await action()
      await refresh()
      setMessage(success)
      return true
    } catch (failure) {
      setActionError(mediaError(failure))
      return false
    } finally {
      setBusy(false)
    }
  }

  async function openDocument(documentId) {
    // Open synchronously from the click so browsers do not block the new tab.
    const preview = window.open('about:blank', '_blank')
    if (preview) preview.opener = null
    setActionError('')
    try {
      const { url } = await getVehicleDocumentUrl(vehicleId, documentId)
      if (preview) preview.location.replace(url)
      else window.location.assign(url)
    } catch (failure) {
      preview?.close()
      setActionError(mediaError(failure))
    }
  }

  return <div className="media-workspace" aria-busy={loading || busy}>
    {(actionError || error) && <p role="alert" className="notice error">{actionError || error}</p>}
    {message && <p role="status" className="notice success">{message}</p>}
    {loading ? <p role="status" className="empty">Đang tải hồ sơ xe…</p> : <>
      <section className="media-section" aria-labelledby="images-heading">
        <div className="section-heading"><div><p className="eyebrow">THƯ VIỆN CÔNG KHAI</p>
          <h2 id="images-heading">Hình ảnh xe <span className="count">{images.length}</span></h2></div>
          <button className="secondary" disabled={busy} onClick={() => perform(() => Promise.resolve(), 'Đã làm mới hồ sơ.')}>Làm mới</button>
        </div>
        <div className={canManage ? 'section-layout' : ''}>
          <div>{images.length ? <div className="image-grid">
            {images.map((image) => <article className="image-card" key={image.id}>
              <a href={image.url} target="_blank" rel="noreferrer">
                <img src={image.url} alt={imageTypes[image.imageType] || image.fileName} loading="lazy" />
              </a>
              <div className="card-body">
                {image.isPrimary && <span className="badge">Ảnh đại diện</span>}
                <h3>{imageTypes[image.imageType] || image.imageType}</h3>
                <p className="filename" title={image.fileName}>{image.fileName}</p>
                <p className="hint">{fileSize(image.fileSizeBytes)}</p>
                {canManage && <button className="danger" disabled={busy} onClick={() =>
                  perform(() => deleteVehicleImage(vehicleId, image.id), 'Đã xóa ảnh.')}>Xóa ảnh</button>}
              </div>
            </article>)}
          </div> : <p className="empty">Xe chưa có hình ảnh.</p>}</div>
          {canManage && <div>
            <UploadForm disabled={busy} onUpload={uploadImages} />
            {uploads.length > 0 && <ul className="upload-progress" aria-live="polite">
              {uploads.map((item, index) => <li key={index} className={item.failed ? 'notice error' : 'hint'}>
                <span className="filename" title={item.name}>{item.name}</span>: {item.status}
              </li>)}
            </ul>}
          </div>}
        </div>
      </section>
      {canManage ? <section className="media-section" aria-labelledby="documents-heading">
        <div className="section-heading"><div><p className="eyebrow">HỒ SƠ NỘI BỘ</p>
          <h2 id="documents-heading">Giấy tờ xe <span className="count">{documents.length}</span></h2></div>
          <span className="badge private">Chỉ người có quyền quản lý</span></div>
        <div className="section-layout">
          <div>{documents.length ? <ul className="document-list">
            {documents.map((document) => <li key={document.id} className="document-card">
              <div><h3>{documentTypes[document.docType] || document.docType}</h3>
                <p className="filename">{document.fileName}</p>
                <p className="hint">{document.docNumber || 'Chưa có số giấy tờ'} · {fileSize(document.fileSizeBytes)}</p>
                <p className="hint">Ngày cấp: {document.issueDate || '—'} · Hết hạn: {document.expiryDate || '—'}</p>
                <span className="badge private">{document.isVerified ? 'Đã xác minh' : 'Chưa xác minh'}</span>
              </div>
              <div className="document-actions">
                <button className="secondary" disabled={busy} onClick={() => openDocument(document.id)}>Mở tệp</button>
                <button className="danger" disabled={busy} onClick={() => perform(() =>
                  deleteVehicleDocument(vehicleId, document.id), 'Đã xóa giấy tờ.')}>Xóa</button>
              </div>
            </li>)}
          </ul> : <p className="empty">Xe chưa có giấy tờ.</p>}</div>
          <UploadForm document disabled={busy} onUpload={(file, fields) => perform(() =>
            uploadVehicleDocument(vehicleId, file, fields), 'Đã tải giấy tờ lên.')} />
        </div>
      </section> : <p className="notice">Đăng nhập bằng tài khoản quản lý để thêm ảnh và truy cập giấy tờ xe.</p>}
    </>}
  </div>
}
