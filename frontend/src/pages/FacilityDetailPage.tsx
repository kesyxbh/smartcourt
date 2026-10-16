import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { bookingsApi } from '../api/bookings'
import { ApiError } from '../api/client'
import { fetchAvailability, fetchFacility } from '../api/facilities'
import { useAuth } from '../auth/AuthContext'
import { CLOSE_HOUR, OPEN_HOUR, validateSelection } from '../lib/booking'
import { dateFromToday, formatDate, formatHour, formatTime, parseLocalDateTime, toLocalDateTime } from '../lib/datetime'
import { errorMessage } from '../lib/errors'
import { typeLabel } from '../lib/facilityFilters'
import { NotFoundPage } from './NotFoundPage'

// range(8, 10) gives [8, 9, 10]; used to build the options of a drop-down
const range = (from: number, to: number): number[] =>
  Array.from({ length: Math.max(0, to - from + 1) }, (_, i) => from + i)

// "Sun, Oct 4, 2026 10:00–11:00"
function formatSlot(slot: { startTime: string; endTime: string }): string {
  const start = parseLocalDateTime(slot.startTime)
  const end = parseLocalDateTime(slot.endTime)
  return `${formatDate(start)} ${formatTime(start)}–${formatTime(end)}`
}

// "10:00–11:00" (the date is already known from the context)
function formatRange(slot: { startTime: string; endTime: string }): string {
  return `${formatTime(parseLocalDateTime(slot.startTime))}–${formatTime(parseLocalDateTime(slot.endTime))}`
}

export function FacilityDetailPage() {
  const { id } = useParams()
  // Hooks must not come after a conditional return, so the id check lives in this outer
  // component and everything that uses hooks lives in the inner one.
  if (!id || !/^\d+$/.test(id)) return <NotFoundPage />
  return <FacilityDetail facilityId={Number(id)} />
}

function FacilityDetail({ facilityId }: { facilityId: number }) {
  const { session } = useAuth()
  const location = useLocation()
  const queryClient = useQueryClient()

  const [date, setDate] = useState(() => dateFromToday(1))
  const [startHour, setStartHour] = useState(10)
  const [endHour, setEndHour] = useState(11)

  const facility = useQuery({
    queryKey: ['facility', facilityId],
    queryFn: () => fetchFacility(facilityId),
  })

  // The time ranges already taken on the selected day. This endpoint is public.
  const availability = useQuery({
    queryKey: ['availability', facilityId, date],
    queryFn: () => fetchAvailability(facilityId, date),
    enabled: date !== '',
  })

  // useQuery is for reading, useMutation for writing: it does not run by itself,
  // you call book.mutate() when you want it to.
  const book = useMutation({
    mutationFn: () =>
      bookingsApi.create({
        facilityId,
        startTime: toLocalDateTime(date, startHour),
        endTime: toLocalDateTime(date, endHour),
      }),
    // Success or failure, reload the taken ranges so the page shows the real situation.
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['availability', facilityId] }),
    // On success the "My bookings" list is stale as well.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['bookings'] }),
  })

  if (facility.isPending) return <p>Loading…</p>
  if (facility.isError) {
    return facility.error instanceof ApiError && facility.error.status === 404 ? (
      <NotFoundPage />
    ) : (
      <p>
        Failed to load: {errorMessage(facility.error)}{' '}
        <button type="button" onClick={() => facility.refetch()}>
          Retry
        </button>
      </p>
    )
  }

  const info = facility.data
  const open = info.status === 'ACTIVE'
  const occupied = availability.data ?? []
  const problem = validateSelection({ date, startHour, endHour }, occupied, new Date())
  const canSubmit = open && !problem && !availability.isPending && !book.isPending
  const estimatedTotal = info.hourlyRate * Math.max(0, endHour - startHour)

  function handleStartChange(hour: number) {
    book.reset() // the selection changed, so the previous success/failure message is obsolete
    setStartHour(hour)
    if (endHour <= hour) setEndHour(hour + 1) // push the end time along instead of showing an error
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (canSubmit) book.mutate()
  }

  return (
    <div>
      <p>
        <Link to="/facilities">← Back to facilities</Link>
      </p>

      <h1>{info.name}</h1>
      <p>
        {typeLabel(info.type)} · {info.location} · up to {info.capacity} people · {info.hourlyRate.toFixed(2)} / hour
      </p>
      {!open && <p className="notice">This facility is under maintenance and cannot be booked right now.</p>}

      <h2>Choose a time</h2>
      <form onSubmit={handleSubmit}>
        <div className="form-row">
          <label className="field">
            <span>Date</span>
            <input
              type="date"
              value={date}
              min={dateFromToday(0)}
              onChange={(e) => {
                book.reset()
                setDate(e.target.value)
              }}
            />
          </label>

          <label className="field">
            <span>Start time</span>
            <select value={startHour} onChange={(e) => handleStartChange(Number(e.target.value))}>
              {range(OPEN_HOUR, CLOSE_HOUR - 1).map((hour) => (
                <option key={hour} value={hour}>
                  {formatHour(hour)}
                </option>
              ))}
            </select>
          </label>

          <label className="field">
            <span>End time</span>
            <select
              value={endHour}
              onChange={(e) => {
                book.reset()
                setEndHour(Number(e.target.value))
              }}
            >
              {range(startHour + 1, CLOSE_HOUR).map((hour) => (
                <option key={hour} value={hour}>
                  {formatHour(hour)}
                </option>
              ))}
            </select>
          </label>
        </div>

        <h3>Already booked on this day</h3>
        {availability.isPending && date !== '' && <p>Loading…</p>}
        {availability.isError && <p className="field-error">{errorMessage(availability.error)}</p>}
        {availability.data &&
          (availability.data.length === 0 ? (
            <p>Nothing is booked yet. The whole day is free.</p>
          ) : (
            <ul className="slot-list">
              {availability.data.map((slot) => (
                <li key={`${slot.startTime}-${slot.endTime}`}>{formatRange(slot)}</li>
              ))}
            </ul>
          ))}

        {open && !problem && book.isIdle && (
          <p className="small">Estimated total: {estimatedTotal.toFixed(2)}</p>
        )}
        {problem && open && book.isIdle && <p className="field-error">{problem}</p>}
        {book.isSuccess && <p className="notice notice-success">Booked: {formatSlot(book.data)}</p>}
        {book.isError && (
          <p className="field-error" role="alert">
            {book.error instanceof ApiError && book.error.status === 409
              ? 'Someone else just took this time range (or the facility is unavailable). The booked ranges have been refreshed.'
              : errorMessage(book.error)}
          </p>
        )}

        {session ? (
          <button type="submit" disabled={!canSubmit}>
            {book.isPending ? 'Booking…' : 'Confirm booking'}
          </button>
        ) : (
          <p className="notice">
            Please log in to book.{' '}
            <Link to="/login" state={{ from: location }}>
              Log in
            </Link>
          </p>
        )}
      </form>
    </div>
  )
}
