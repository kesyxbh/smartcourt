import { describe, expect, it } from 'vitest'
import type { Slot } from '../api/types'
import { overlaps, validateSelection } from './booking'

// A given hour and minute on October 4th
const at = (hour: number, minute = 0) => new Date(2026, 9, 4, hour, minute)

describe('overlaps', () => {
  const base = { start: at(10), end: at(11) }

  it('detects partial overlap from both sides', () => {
    expect(overlaps(base, { start: at(9), end: at(10, 30) })).toBe(true)
    expect(overlaps(base, { start: at(10, 30), end: at(12) })).toBe(true)
  })

  it('detects one range containing the other', () => {
    expect(overlaps(base, { start: at(9), end: at(12) })).toBe(true)
    expect(overlaps({ start: at(9), end: at(12) }, base)).toBe(true)
  })

  it('treats back-to-back ranges as not overlapping', () => {
    expect(overlaps(base, { start: at(11), end: at(12) })).toBe(false)
    expect(overlaps(base, { start: at(9), end: at(10) })).toBe(false)
  })

  it('treats disjoint ranges as not overlapping', () => {
    expect(overlaps(base, { start: at(13), end: at(14) })).toBe(false)
  })
})

describe('validateSelection', () => {
  const now = new Date(2026, 9, 3, 12, 0) // October 3rd, 12:00
  const occupied: Slot[] = [{ startTime: '2026-10-04T10:00:00', endTime: '2026-10-04T11:00:00' }]
  const date = '2026-10-04'

  it('accepts a free time range in the future', () => {
    expect(validateSelection({ date, startHour: 14, endHour: 15 }, occupied, now)).toBeNull()
  })

  it('asks for a date when none is chosen', () => {
    expect(validateSelection({ date: '', startHour: 14, endHour: 15 }, occupied, now)).not.toBeNull()
  })

  it('rejects an end time that is not after the start time', () => {
    expect(validateSelection({ date, startHour: 14, endHour: 14 }, occupied, now)).not.toBeNull()
    expect(validateSelection({ date, startHour: 15, endHour: 14 }, occupied, now)).not.toBeNull()
  })

  it('rejects the past but accepts a later time on the same day', () => {
    expect(validateSelection({ date: '2026-10-03', startHour: 9, endHour: 10 }, [], now)).not.toBeNull()
    expect(validateSelection({ date: '2026-10-03', startHour: 13, endHour: 14 }, [], now)).toBeNull()
  })

  it('rejects ranges that overlap an existing booking', () => {
    expect(validateSelection({ date, startHour: 10, endHour: 12 }, occupied, now)).not.toBeNull()
    expect(validateSelection({ date, startHour: 9, endHour: 11 }, occupied, now)).not.toBeNull()
    expect(validateSelection({ date, startHour: 9, endHour: 12 }, occupied, now)).not.toBeNull()
  })

  it('accepts the ranges directly before and after an existing booking', () => {
    expect(validateSelection({ date, startHour: 11, endHour: 12 }, occupied, now)).toBeNull()
    expect(validateSelection({ date, startHour: 9, endHour: 10 }, occupied, now)).toBeNull()
  })
})
