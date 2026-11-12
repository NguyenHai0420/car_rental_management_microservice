import { after, before, test } from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import apiClient, { setAccessTokenProvider } from '../../../services/axios-client.js'
import { notificationService } from './notification.js'

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
  setAccessTokenProvider(async () => 'notif-admin-token')
})

after(() => new Promise((resolve) => server.close(resolve)))

test('listNotifications and getSummary call new list and summary endpoints with filters', async () => {
  requests.length = 0
  handler = (req) => {
    if (req.url === '/api/v1/notifications/summary') {
      return { status: 200, body: { data: { total: 5, sent: 3, failed: 2 } } }
    }
    return {
      status: 200,
      body: {
        data: {
          content: [{ id: 'n-1', status: 'SENT', recipient: 'user@gmail.com' }],
          totalElements: 1,
          number: 0,
          size: 10,
        },
      },
    }
  }

  const page = await notificationService.listNotifications({
    recipient: 'gmail.com',
    status: 'SENT',
    sourceEvent: '',
    page: 0,
    size: 10,
  })
  const summary = await notificationService.getSummary()

  const listUrl = new URL(requests[0].url, 'http://test.local')
  assert.equal(listUrl.pathname, '/api/v1/notifications')
  assert.equal(listUrl.searchParams.get('recipient'), 'gmail.com')
  assert.equal(listUrl.searchParams.get('status'), 'SENT')
  assert.equal(listUrl.searchParams.has('sourceEvent'), false)
  assert.equal(requests[1].url, '/api/v1/notifications/summary')
  assert.equal(page.content[0].status, 'SENT')
  assert.equal(summary.sent, 3)
})

test('sendBookingConfirmation preserves HTTP 202 and unwrapped notification payload', async () => {
  requests.length = 0
  handler = () => ({
    status: 202,
    body: {
      code: 'ACCEPTED',
      message: 'Accepted',
      data: { id: 'n-2', status: 'PENDING', idempotentReplay: false },
    },
  })

  const result = await notificationService.sendBookingConfirmation({
    idempotencyKey: 'k-1',
    customerEmail: 'user@gmail.com',
  })

  assert.equal(requests[0].method, 'POST')
  assert.equal(requests[0].url, '/api/v1/notifications/booking-confirmation')
  assert.equal(result.httpStatus, 202)
  assert.equal(result.data.id, 'n-2')
  assert.equal(result.data.status, 'PENDING')
})

test('getById and getByReference fetch single and reference notification records', async () => {
  requests.length = 0
  handler = (req) => ({
    status: 200,
    body: {
      data: req.url.includes('/reference/')
        ? [{ id: 'n-3', referenceId: 'ref-1' }]
        : { id: 'n-3', status: 'FAILED', errorMessage: 'Auth failed' },
    },
  })

  const byId = await notificationService.getById('n-3')
  const byRef = await notificationService.getByReference('ref-1')

  assert.equal(requests[0].url, '/api/v1/notifications/n-3')
  assert.equal(requests[1].url, '/api/v1/notifications/reference/ref-1')
  assert.equal(byId.errorMessage, 'Auth failed')
  assert.equal(byRef.length, 1)
})

