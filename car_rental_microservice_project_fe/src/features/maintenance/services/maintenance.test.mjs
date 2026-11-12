import { after, before, test } from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import apiClient, { setAccessTokenProvider } from '../../../services/axios-client.js'
import { formatVehicleDisplay, maintenanceService } from './maintenance.js'
import { formatMaintenanceError, isConflictError } from './maintenanceErrors.js'

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

test('checkAllConflicts merges maintenance schedules and vehicle holds without duplicate maintenance holds', async () => {
  requests.length = 0
  handler = (req) => {
    if (req.url.startsWith('/api/v1/maintenances/vehicle/')) {
      return {
        status: 200,
        body: {
          vehicleId: 'veh-1',
          requestedStartAt: '2026-11-20T01:00:00Z',
          requestedEndAt: '2026-11-20T10:00:00Z',
          hasConflict: true,
          conflictingSchedules: [
            {
              id: 'sch-1',
              vehicleId: 'veh-1',
              status: 'SCHEDULED',
              scheduledStartAt: '2026-11-20T01:00:00Z',
              scheduledEndAt: '2026-11-20T10:00:00Z',
            },
          ],
        },
      }
    }
    return {
      status: 200,
      body: {
        data: {
          vehicleId: 'veh-1',
          vehicleStatus: 'AVAILABLE',
          from: '2026-11-20T01:00:00Z',
          to: '2026-11-20T10:00:00Z',
          hasConflict: true,
          conflictingHolds: [
            {
              holdId: 'hold-maint-dup',
              ownerType: 'MAINTENANCE',
              ownerId: 'sch-1',
              startAt: '2026-11-20T01:00:00Z',
              endAt: '2026-11-20T10:00:00Z',
              status: 'CONFIRMED',
            },
            {
              holdId: 'hold-bkg-1',
              ownerType: 'BOOKING',
              ownerId: 'bkg-1',
              startAt: '2026-11-20T02:00:00Z',
              endAt: '2026-11-20T09:00:00Z',
              status: 'CONFIRMED',
              reason: 'Khách đặt xe cuối tuần',
            },
          ],
        },
      },
    }
  }

  const combined = await maintenanceService.checkAllConflicts('veh-1', {
    startAt: '2026-11-20T01:00:00Z',
    endAt: '2026-11-20T10:00:00Z',
    excludeScheduleId: 'sch-2',
  })

  assert.equal(combined.hasConflict, true)
  assert.equal(combined.conflictingSchedules.length, 1)
  assert.equal(combined.conflictingHolds.length, 1)
  assert.equal(combined.conflictingHolds[0].holdId, 'hold-bkg-1')
  assert.equal(combined.conflictingHolds[0].ownerType, 'BOOKING')
})

test('formatMaintenanceError produces readable Vietnamese errors for conflicts and checklists', () => {
  const maintConflictErr = formatMaintenanceError({
    response: {
      status: 409,
      data: {
        message:
          'Vehicle veh-1 already has a conflicting maintenance schedule (sch-1) in [2026-11-14T01:00:00Z, 2026-11-14T10:00:00Z)',
      },
    },
  })
  assert.equal(maintConflictErr.title, 'Xe bị trùng lịch bảo dưỡng')
  assert.equal(maintConflictErr.isConflict, true)
  assert.equal(maintConflictErr.description.includes('14/11/2026'), true)

  const bookingHoldErr = formatMaintenanceError({
    response: {
      status: 409,
      data: {
        message:
          'Vehicle veh-1 has conflicting BOOKING hold (h-1) in [2026-11-20T01:00:00Z, 2026-11-20T10:00:00Z)',
      },
    },
  })
  assert.equal(bookingHoldErr.title, 'Xe bị trùng lịch đặt thuê (Booking)')
  assert.equal(isConflictError({ response: { status: 409, data: { message: 'conflicting' } } }), true)

  const missingChecklistErr = formatMaintenanceError({
    response: {
      status: 400,
      data: {
        message: 'Cannot complete maintenance schedule sch-1 without at least one inspection checklist item',
      },
    },
  })
  assert.equal(missingChecklistErr.title, 'Chưa thể hoàn tất: thiếu biên bản kiểm định')

  const failedChecklistErr = formatMaintenanceError({
    response: {
      status: 400,
      data: {
        message:
          'Cannot complete maintenance schedule sch-1: unresolved or failed inspection checklist items: Phanh trước (FAIL), Áp suất lốp (NOT_CHECKED)',
      },
    },
  })
  assert.equal(failedChecklistErr.title, 'Chưa thể hoàn tất: kiểm định an toàn chưa đạt')
  assert.equal(failedChecklistErr.description.includes('Phanh trước (Không đạt)'), true)
  assert.equal(failedChecklistErr.description.includes('Áp suất lốp (Chưa kiểm tra)'), true)
})

