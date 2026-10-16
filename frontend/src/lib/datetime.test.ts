import { describe, expect, it } from 'vitest'
import { formatDate, parseLocalDateTime, toDateString, toLocalDateTime } from './datetime'

describe('toDateString', () => {
  it('returns the local date, not the UTC date', () => {
    // With toISOString() this would be 2026-09-25 in UTC+8 (00:30 local is 16:30 UTC the day before)
    expect(toDateString(new Date(2026, 8, 26, 0, 30))).toBe('2026-09-26')
    expect(toDateString(new Date(2026, 8, 26, 23, 30))).toBe('2026-09-26')
  })

  it('zero-pads month and day', () => {
    expect(toDateString(new Date(2026, 0, 5))).toBe('2026-01-05')
  })
})

describe('toLocalDateTime', () => {
  it('builds the zone-less format the backend expects', () => {
    expect(toLocalDateTime('2026-10-04', 10)).toBe('2026-10-04T10:00:00')
    expect(toLocalDateTime('2026-10-04', 8)).toBe('2026-10-04T08:00:00')
  })
})

describe('parseLocalDateTime', () => {
  it('reads a zone-less string as local time', () => {
    const d = parseLocalDateTime('2026-10-04T10:30:00')
    expect([d.getFullYear(), d.getMonth(), d.getDate(), d.getHours(), d.getMinutes()]).toEqual([2026, 9, 4, 10, 30])
  })
})

describe('formatDate', () => {
  it('formats weekday, month, day and year without relying on the locale', () => {
    expect(formatDate(new Date(2026, 9, 4))).toBe('Sun, Oct 4, 2026')
    expect(formatDate(new Date(2026, 0, 31))).toBe('Sat, Jan 31, 2026')
  })
})
