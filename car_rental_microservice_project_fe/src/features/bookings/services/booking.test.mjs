import { after, before, test } from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import apiClient, { setAccessTokenProvider } from '../../../services/axios-client.js'
import { bookingService } from './booking.js'
import { mergeAvailableVehicles } from '../booking-vehicles.js'

let received
const server = createServer(async (request, response) => {
  const parts = []
  for await (const chunk of request) parts.push(chunk)
  received = { method: request.method, url: new URL(request.url, 'http://test.local'),
    body: Buffer.concat(parts).toString(), authorization: request.headers.authorization }
  response.writeHead(200, { 'Content-Type': 'application/json' })
  response.end(JSON.stringify({ data: request.method === 'GET'
    ? [{ vehicleId: 'available-car', licensePlate: '51A-12345', dailyRate: 700000 }]
    : { id: 'booking-id', bookingCode: 'BK-TEST' } }))
})

before(async () => {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  apiClient.defaults.baseURL = `http://127.0.0.1:${server.address().port}`
  setAccessTokenProvider(async () => 'test-session-token')
})
after(() => new Promise((resolve) => server.close(resolve)))

test('availability search sends rental dates, seats and location to booking-service', async () => {
  const result = await bookingService.searchAvailableVehicles({
    pickupDatetime: '2026-11-13T08:00:00', returnDatetime: '2026-11-15T08:00:00',
    categoryId: 'category-id', minSeats: 7, location: '  Quận 1  ',
  })
  assert.equal(received.url.pathname, '/api/v1/bookings/available-vehicles')
  assert.deepEqual(Object.fromEntries(received.url.searchParams), {
    pickupDatetime: '2026-11-13T08:00:00', returnDatetime: '2026-11-15T08:00:00',
    categoryId: 'category-id', minSeats: '7', location: 'Quận 1',
  })
  assert.equal(result[0].licensePlate, '51A-12345')
})

test('cancelled availability searches do not send a stale request', async () => {
  const previousRequest = received
  const controller = new AbortController()
  controller.abort()
  await assert.rejects(bookingService.searchAvailableVehicles({}, controller.signal),
    (error) => error.code === 'ERR_CANCELED')
  assert.equal(received, previousRequest)
})

test('available cards retain their actual vehicle ID, cover and technical details', () => {
  const cards = mergeAvailableVehicles([
    { vehicleId: 'available-car', licensePlate: '51A-12345', dailyRate: 700000 },
  ], [
    { id: 'busy-car', brand: 'Toyota', modelName: 'Camry' },
    { id: 'available-car', brand: 'Toyota', modelName: 'Vios', transmission: 'AUTOMATIC',
      imageUrl: 'https://files.example.test/vios.jpg', dailyRate: 600000 },
  ])
  assert.equal(cards.length, 1)
  assert.equal(cards[0].id, 'available-car')
  assert.equal(cards[0].vehicleId, 'available-car')
  assert.equal(cards[0].licensePlate, '51A-12345')
  assert.equal(cards[0].transmission, 'AUTOMATIC')
  assert.equal(cards[0].imageUrl, 'https://files.example.test/vios.jpg')
  assert.equal(cards[0].dailyRate, 700000)
})

test('availability covers and cars outside the showroom preview remain bookable', () => {
  const cards = mergeAvailableVehicles([
    { vehicleId: 'car-id', primaryImageUrl: 'https://files.example.test/cover.jpg' },
    { id: 'other-id', brand: 'Ford', modelName: 'Everest' },
    { brand: 'missing-identity' },
  ])
  assert.deepEqual(cards.map((car) => car.id), ['car-id', 'other-id'])
  assert.equal(cards[0].imageUrl, 'https://files.example.test/cover.jpg')
  assert.equal(cards[1].vehicleId, 'other-id')
  assert.deepEqual(mergeAvailableVehicles([], [{ id: 'busy-car' }]), [])
})

test('booking creation preserves the selected vehicle and pickup/return details', async () => {
  await bookingService.createBooking({ vehicleId: 'available-car',
    pickupDatetime: '2026-11-13T08:00:00', returnDatetime: '2026-11-15T08:00:00',
    pickupLocation: '  Quận 1  ', returnLocation: '', notes: '  Ghế trẻ em  ' })
  assert.equal(received.method, 'POST')
  assert.equal(received.url.pathname, '/api/v1/bookings')
  assert.equal(received.authorization, 'Bearer test-session-token')
  assert.deepEqual(JSON.parse(received.body), { vehicleId: 'available-car',
    pickupDatetime: '2026-11-13T08:00:00', returnDatetime: '2026-11-15T08:00:00',
    pickupLocation: 'Quận 1', returnLocation: 'Quận 1', notes: 'Ghế trẻ em' })
})
