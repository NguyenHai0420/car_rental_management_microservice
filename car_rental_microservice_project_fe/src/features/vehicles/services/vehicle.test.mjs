import { after, before, test } from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import apiClient, { setAccessTokenProvider } from '../../../services/axios-client.js'
import { getVehicleDocumentUrl, uploadVehicleDocument, uploadVehicleImage } from './vehicle.js'

let received
const server = createServer(async (request, response) => {
  const parts = []
  for await (const chunk of request) parts.push(chunk)
  received = { headers: request.headers, path: request.url, body: parts.map((part) => part.toString()).join('') }
  response.writeHead(200, { 'Content-Type': 'application/json' })
  response.end(JSON.stringify({ data: { id: 'saved-media', url: 'https://files.example.test/signed' } }))
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
