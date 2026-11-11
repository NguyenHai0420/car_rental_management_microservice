import { after, before, test } from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import apiClient, { setAccessTokenProvider } from '../../../services/axios-client.js'
import { formatVehicleDisplay, maintenanceService } from './maintenance.js'

const requests = []
let handler = () => ({ status: 200, body: { data: {} } })

const server = createServer(async (request, response) => {
  const chunks = []
  for await (const chunk of request) chunks.push(chunk)
  const rawBody = Buffer.concat(chunks).toString()
  requests.push({
    method: request.method,
    url: request.url,
    headers: request.headers,
    body: rawBody ? JSON.parse(rawBody) : null,
  })
  const res = handler(requests[requests.length - 1])
  response.writeHead(res.status ?? 200, { 'Content-Type': 'application/json' })
  response.end(JSON.stringify(res.body))
})

before(async () => {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  apiClient.defaults.baseURL = `http://127.0.0.1:${server.address().port}`
  setAccessTokenProvider(async () => 'maint-admin-token')
})

after(() => new Promise((resolve) => server.close(resolve)))

test('listSchedules strips empty filters and returns paged maintenance data', async () => {
  requests.length = 0
  handler = () => ({
    status: 200,
    body: {
      data: {
        content: [{ id: 'sch-1', status: 'SCHEDULED' }],
        totalElements: 1,
        number: 0,
        size: 10,
      },
    },
  })

  const page = await maintenanceService.listSchedules({
    vehicleId: '',
    status: 'SCHEDULED',
    page: 0,
    size: 10,
  })

  const url = new URL(requests[0].url, 'http://test.local')
  assert.equal(requests[0].method, 'GET')
  assert.equal(url.pathname, '/api/v1/maintenances')
  assert.equal(url.searchParams.get('status'), 'SCHEDULED')
  assert.equal(url.searchParams.has('vehicleId'), false)
  assert.equal(requests[0].headers.authorization, 'Bearer maint-admin-token')
  assert.equal(page.content[0].id, 'sch-1')
})

test('checkConflicts queries conflict endpoint with interval params', async () => {
  requests.length = 0
  handler = () => ({
    status: 200,
    body: { vehicleId: 'veh-1', hasConflict: false, conflictingSchedules: [] },
  })

  const res = await maintenanceService.checkConflicts('veh-1', {
    startAt: '2026-11-15T08:00:00.000Z',
    endAt: '2026-11-15T17:00:00.000Z',
  })

  const url = new URL(requests[0].url, 'http://test.local')
  assert.equal(url.pathname, '/api/v1/maintenances/vehicle/veh-1/conflicts')
  assert.equal(url.searchParams.get('startAt'), '2026-11-15T08:00:00.000Z')
  assert.equal(res.hasConflict, false)
})

test('records, checklists and lifecycle mutations call expected maintenance routes', async () => {
  requests.length = 0
  handler = () => ({ status: 200, body: { data: { id: 'ok' } } })

  await maintenanceService.addServiceRecord('sch-1', { serviceType: 'OIL_CHANGE', description: 'Thay nhớt' })
  await maintenanceService.addChecklistItem('sch-1', { checkItem: 'Phanh trước', result: 'PASS' })
  await maintenanceService.updateChecklistItem('sch-1', 'chk-1', { result: 'WARNING' })
  await maintenanceService.cancelSchedule('sch-1', 'Hoãn lịch')

  assert.deepEqual(
    requests.map((r) => `${r.method} ${r.url}`),
    [
      'POST /api/v1/maintenances/sch-1/records',
      'POST /api/v1/maintenances/sch-1/checklists',
      'PATCH /api/v1/maintenances/sch-1/checklists/chk-1',
      'PATCH /api/v1/maintenances/sch-1/cancel',
    ]
  )
  assert.deepEqual(requests[3].body, { cancellationReason: 'Hoãn lịch' })
})

test('formatVehicleDisplay formats plate, model and Vietnamese vehicle status', () => {
  const formatted = formatVehicleDisplay({
    id: 'veh-1',
    licensePlate: '30A-999.88',
    brand: 'Toyota',
    modelName: 'Camry',
    status: 'AVAILABLE',
  })
  assert.equal(formatted, '30A-999.88 — Toyota Camry — (Sẵn sàng)')
})

