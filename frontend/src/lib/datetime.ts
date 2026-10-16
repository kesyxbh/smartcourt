// The backend uses LocalDateTime: a wall-clock time with no time zone.
// "2026-10-04T10:00:00" simply means "10 o'clock at the venue".
// So the frontend works only in the browser's local time and must never use
// Date#toISOString(): it converts to UTC, which in UTC+8 turns 00:30 into 16:30 of the
// previous day and shifts the whole date by one.

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

const pad = (n: number): string => String(n).padStart(2, '0')

/** "2026-10-04": the local date, and also the format used by <input type="date"> */
export function toDateString(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** The date `days` days from today, e.g. dateFromToday(1) is tomorrow */
export function dateFromToday(days: number): string {
  const d = new Date()
  d.setDate(d.getDate() + days)
  return toDateString(d)
}

/** Builds the backend format: ("2026-10-04", 9) -> "2026-10-04T09:00:00" */
export function toLocalDateTime(date: string, hour: number): string {
  return `${date}T${pad(hour)}:00:00`
}

/** Parses a backend timestamp. A date-time string without a zone is read as local time. */
export function parseLocalDateTime(value: string): Date {
  return new Date(value)
}

/** "09:00" */
export function formatHour(hour: number): string {
  return `${pad(hour)}:00`
}

/** "10:30" */
export function formatTime(d: Date): string {
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** "Sun, Oct 4, 2026" (built by hand so the output does not depend on the browser locale) */
export function formatDate(d: Date): string {
  return `${WEEKDAYS[d.getDay()]}, ${MONTHS[d.getMonth()]} ${d.getDate()}, ${d.getFullYear()}`
}
