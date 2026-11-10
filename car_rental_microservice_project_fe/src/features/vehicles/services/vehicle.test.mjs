import { after, before, test } from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import apiClient, { setAccessTokenProvider } from '../../../services/axios-client.js'
import {
  changeVehicleStatus, createVehicle, createVehicleCategory, createVehicleModel, deleteVehicle, getVehicle,
  getVehicleDocumentUrl, listVehicleCategories, listVehicleModels, listVehicles, listVehicleStatusHistory,
  lookupVehicleByLicensePlate, updateVehicle, uploadVehicleDocument, uploadVehicleImage, vehicleError,
} from './vehicle.js'

const fleet = '/api/v1/vehicles'
let received
const server = createServer(async (request, response) => {
  const parts = []
  for await (const chunk of request) parts.push(chunk)
  const body = Buffer.concat(parts).toString()
  const path = new URL(request.url, 'http://test.local').pathname
  received = { method: request.method, headers: request.headers, path: request.url, body }
  let data = { id: 'saved-media', url: 'https://files.example.test/signed' }
  if (path === fleet && request.method === 'GET') {
    data = { content: [{ id: 'vehicle-id' }], page: { number: 1, size: 10, totalElements: 11, totalPages: 2 } }
  }
  if (path === `${fleet}/lookup`) data = { id: 'vehicle-id' }
  const conflict = request.method === 'PATCH' && JSON.parse(body).status === 'RENTED'
  response.writeHead(conflict ? 409 : 200, { 'Content-Type': 'application/json' })
  response.end(JSON.stringify(conflict ? { message: 'Invalid transition', data: null } : { data }))
})

before(async () => {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  apiClient.defaults.baseURL = `http://127.0.0.1:${server.address().port}`
  setAccessTokenProvider(async () => 'test-session-token')
})
after(() => new Promise((resolve) => server.close(resolve)))

test('image upload sends multipart boundary, metadata and bearer token through the gateway', async () => {
  const file = new File([new Uint8Array([137, 80, 78, 71])], 'car.png', { type: 'image/png' })
  const result = await uploadVehicleImage('vehicle-id', file, 'INTERIOR', { isPrimary: true, sortOrder: 3 })
  assert.equal(result.id, 'saved-media')
  assert.equal(received.path, '/api/v1/vehicles/vehicle-id/images')
  assert.equal(received.headers.authorization, 'Bearer test-session-token')
  const boundary = /boundary=(.+)$/.exec(received.headers['content-type'])?.[1]
  assert.ok(boundary, 'multipart boundary is supplied by Axios')
  assert.ok(received.body.includes(`--${boundary}`))
  assert.match(received.body, /name="file"; filename="car.png"/)
  assert.match(received.body, /name="imageType"\r\n\r\nINTERIOR/)
  assert.match(received.body, /name="isPrimary"\r\n\r\ntrue/)
  assert.match(received.body, /name="sortOrder"\r\n\r\n3/)
})

test('document upload sends optional metadata and requests a fresh URL separately', async () => {
  const file = new File(['%PDF-1.7'], 'insurance.pdf', { type: 'application/pdf' })
  await uploadVehicleDocument('vehicle-id', file, {
    docType: 'INSURANCE', docNumber: 'INS-001', issueDate: '2026-01-01', expiryDate: '2027-01-01',
  })
  assert.equal(received.path, '/api/v1/vehicles/vehicle-id/documents')
  assert.match(received.body, /name="docType"\r\n\r\nINSURANCE/)
  assert.match(received.body, /name="expiryDate"\r\n\r\n2027-01-01/)
  const result = await getVehicleDocumentUrl('vehicle-id', 'document-id')
  assert.equal(received.path, '/api/v1/vehicles/vehicle-id/documents/document-id/url')
  assert.equal(result.url, 'https://files.example.test/signed')
})

test('fleet list sends pagination and every filter through the authenticated Axios client', async () => {
  const filters = { q: '51H-123.45', page: 1, size: 10, status: 'AVAILABLE', categoryId: 'category-id', modelId: 'model-id' }
  const result = await listVehicles(filters)
  const url = new URL(received.path, 'http://test.local')
  assert.equal(url.pathname, fleet)
  assert.deepEqual(Object.fromEntries(url.searchParams), { ...filters, page: '1', size: '10' })
  assert.equal(received.headers.authorization, 'Bearer test-session-token')
  assert.equal(result.content[0].id, 'vehicle-id')
  assert.equal(result.page.totalPages, 2)
})

test('plate lookup sends the complete plate in a query parameter', async () => {
  const result = await lookupVehicleByLicensePlate('51H 780.01')
  const url = new URL(received.path, 'http://test.local')
  assert.equal(received.method, 'GET')
  assert.equal(url.pathname, `${fleet}/lookup`)
  assert.equal(url.searchParams.get('licensePlate'), '51H 780.01')
  assert.equal(result.id, 'vehicle-id')
})

test('vehicle create, edit and delete send their own verbs and vehicle ID', async () => {
  const fields = { vin: '1HGCM82633A004352', licensePlate: '51H-123.45', mileageKm: '100.5', categoryId: 'cat', modelId: 'model' }
  const id = 'vehicle-id'
  await createVehicle(fields)
  assert.equal(received.method, 'POST')
  assert.equal(received.path, fleet)
  assert.deepEqual(JSON.parse(received.body), fields)
  await getVehicle(id)
  assert.equal(received.method, 'GET')
  assert.equal(received.path, `${fleet}/${id}`)
  await updateVehicle(id, { ...fields, color: 'Xanh' })
  assert.equal(received.method, 'PUT')
  assert.equal(received.path, `${fleet}/${id}`)
  assert.equal(JSON.parse(received.body).color, 'Xanh')
  assert.equal(JSON.parse(received.body).status, undefined)
  await deleteVehicle(id)
  assert.equal(received.method, 'DELETE')
  assert.equal(received.path, `${fleet}/${id}`)
})

test('status uses PATCH and preserves conflict messages from the gateway', async () => {
  const fields = { status: 'RESERVED', reason: 'Giữ chỗ' }
  await changeVehicleStatus('vehicle-id', fields)
  assert.equal(received.method, 'PATCH')
  assert.equal(received.path, `${fleet}/vehicle-id/status`)
  assert.deepEqual(JSON.parse(received.body), fields)
  await assert.rejects(() => changeVehicleStatus('vehicle-id', { status: 'RENTED' }), (error) =>
    error.response.status === 409 && vehicleError(error) === 'Invalid transition')
  await listVehicleStatusHistory('vehicle-id')
  assert.equal(received.method, 'GET')
  assert.equal(received.path, `${fleet}/vehicle-id/status-history`)
})

test('catalog creates and lists categories and models via vehicle routes', async () => {
  const category = { name: 'SUV', sizeClass: 'SUV' }
  const model = { brand: 'Toyota', modelName: 'RAV4', manufactureYear: 2025, seatCount: 5, fuelType: 'HYBRID', transmission: 'AUTOMATIC' }
  for (const [segment, read, write, fields] of [
    ['categories', listVehicleCategories, createVehicleCategory, category],
    ['models', listVehicleModels, createVehicleModel, model],
  ]) {
    await read()
    assert.equal(received.method, 'GET')
    assert.equal(received.path, `${fleet}/${segment}`)
    await write(fields)
    assert.equal(received.method, 'POST')
    assert.equal(received.path, `${fleet}/${segment}`)
    assert.deepEqual(JSON.parse(received.body), fields)
  }
})
