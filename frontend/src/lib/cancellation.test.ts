import { describe, expect, it } from 'vitest'
import type { Booking } from '../api/types'
import { isCancellable, isUpcoming, splitBookings } from './cancellation'

const now = new Date(2026, 9, 10, 12, 0) // October 10th, 12:00

function booking(overrides: Partial<Booking> & Pick<Booking, 'id' | 'startTime' | 'endTime'>): Booking {
  return { facilityId: 1, facilityName: 'Court', status: 'CONFIRMED', ...overrides }
}

describe('isCancellable', () => {
  it('allows a booking that starts at least 24 hours from now (the boundary included)', () => {
    expect(isCancellable(booking({ id: 1, startTime: '2026-10-11T12:00:00', endTime: '2026-10-11T13:00:00' }), now)).toBe(true)
    expect(isCancellable(booking({ id: 1, startTime: '2026-10-11T11:59:00', endTime: '2026-10-11T13:00:00' }), now)).toBe(false)
  })

  it('never allows a cancelled booking', () => {
    const cancelled = booking({ id: 1, status: 'CANCELLED', startTime: '2026-10-20T10:00:00', endTime: '2026-10-20T11:00:00' })
    expect(isCancellable(cancelled, now)).toBe(false)
    expect(isCancellable({ ...cancelled, cancellable: true }, now)).toBe(false)
  })

  it('lets the server flag win when it is present', () => {
    const soon = booking({ id: 1, startTime: '2026-10-10T15:00:00', endTime: '2026-10-10T16:00:00' })
    expect(isCancellable({ ...soon, cancellable: true }, now)).toBe(true)
    const later = booking({ id: 2, startTime: '2026-10-20T10:00:00', endTime: '2026-10-20T11:00:00' })
    expect(isCancellable({ ...later, cancellable: false }, now)).toBe(false)
  })
})

describe('isUpcoming and splitBookings', () => {
  const finished = booking({ id: 1, startTime: '2026-10-09T10:00:00', endTime: '2026-10-09T11:00:00' })
  const cancelled = booking({ id: 2, status: 'CANCELLED', startTime: '2026-10-15T10:00:00', endTime: '2026-10-15T11:00:00' })
  const later = booking({ id: 3, startTime: '2026-10-20T10:00:00', endTime: '2026-10-20T11:00:00' })
  const sooner = booking({ id: 4, startTime: '2026-10-12T10:00:00', endTime: '2026-10-12T11:00:00' })
  const running = booking({ id: 5, startTime: '2026-10-10T11:30:00', endTime: '2026-10-10T12:30:00' })

  it('treats a booking as upcoming until it ends', () => {
    expect(isUpcoming(running, now)).toBe(true)
    expect(isUpcoming(finished, now)).toBe(false)
    expect(isUpcoming(cancelled, now)).toBe(false)
  })

  it('orders upcoming soonest first and history most recent first', () => {
    const { upcoming, history } = splitBookings([finished, cancelled, later, sooner, running], now)
    expect(upcoming.map((b) => b.id)).toEqual([5, 4, 3])
    expect(history.map((b) => b.id)).toEqual([2, 1])
  })
})
