import type { Slot } from '../api/types'
import { parseLocalDateTime, toLocalDateTime } from './datetime'

// The backend has no notion of opening hours yet, so the frontend fixes a bookable range.
// It only affects the drop-down options.
export const OPEN_HOUR = 8
export const CLOSE_HOUR = 22

interface TimeRange {
  start: Date
  end: Date
}

/**
 * Two half-open ranges [a.start, a.end) and [b.start, b.end) overlap
 * if and only if a.start < b.end and a.end > b.start.
 * The backend uses the same rule, so 10:00-11:00 and 11:00-12:00 do not conflict.
 */
export function overlaps(a: TimeRange, b: TimeRange): boolean {
  return a.start < b.end && a.end > b.start
}

export interface Selection {
  date: string // "2026-10-04"
  startHour: number
  endHour: number
}

/**
 * Instant feedback before submitting. It only mirrors the server's rules: the server is
 * the real referee (if someone else books first, it answers 409).
 * Returns a message, or null when the selection looks bookable.
 */
export function validateSelection(selection: Selection, occupied: readonly Slot[], now: Date): string | null {
  const { date, startHour, endHour } = selection
  if (!date) return 'Please choose a date'
  if (endHour <= startHour) return 'The end time must be after the start time'

  const wanted: TimeRange = {
    start: parseLocalDateTime(toLocalDateTime(date, startHour)),
    end: parseLocalDateTime(toLocalDateTime(date, endHour)),
  }
  if (wanted.start < now) return 'The start time must not be in the past'

  const clash = occupied.some((slot) =>
    overlaps(wanted, { start: parseLocalDateTime(slot.startTime), end: parseLocalDateTime(slot.endTime) }),
  )
  return clash ? 'This time range overlaps an existing booking' : null
}
