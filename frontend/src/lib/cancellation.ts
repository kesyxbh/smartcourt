import type { Booking } from '../api/types'
import { parseLocalDateTime } from './datetime'

/** A booking can only be cancelled while it still starts at least this many hours from now. */
export const CANCEL_WINDOW_HOURS = 24

const HOUR_MS = 60 * 60 * 1000

/**
 * Can this booking be cancelled right now?
 * The server is the source of truth: when it sends `cancellable`, that flag wins.
 * Otherwise fall back to the 24-hour rule (a cancelled booking is never cancellable).
 */
export function isCancellable(booking: Booking, now: Date): boolean {
  if (booking.status !== 'CONFIRMED') return false
  if (typeof booking.cancellable === 'boolean') return booking.cancellable
  const msUntilStart = parseLocalDateTime(booking.startTime).getTime() - now.getTime()
  return msUntilStart >= CANCEL_WINDOW_HOURS * HOUR_MS
}

/** Upcoming = confirmed and not finished yet. */
export function isUpcoming(booking: Booking, now: Date): boolean {
  return booking.status === 'CONFIRMED' && parseLocalDateTime(booking.endTime) > now
}

/**
 * Splits bookings into the ones to act on (soonest first) and the history
 * (cancelled or finished, most recent first).
 */
export function splitBookings(bookings: readonly Booking[], now: Date): { upcoming: Booking[]; history: Booking[] } {
  const byStart = (a: Booking, b: Booking) =>
    parseLocalDateTime(a.startTime).getTime() - parseLocalDateTime(b.startTime).getTime()
  const upcoming = bookings.filter((b) => isUpcoming(b, now)).sort(byStart)
  const history = bookings.filter((b) => !isUpcoming(b, now)).sort((a, b) => byStart(b, a))
  return { upcoming, history }
}
