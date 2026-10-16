import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { bookingsApi } from '../api/bookings'
import type { Booking } from '../api/types'
import { CANCEL_WINDOW_HOURS, isCancellable, splitBookings } from '../lib/cancellation'
import { formatDate, formatTime, parseLocalDateTime } from '../lib/datetime'
import { errorMessage } from '../lib/errors'

export function MyBookingsPage() {
  const queryClient = useQueryClient()
  // The user is asked to confirm in two steps; this is the booking waiting for the answer.
  const [confirmingId, setConfirmingId] = useState<number | null>(null)

  const bookings = useQuery({ queryKey: ['bookings', 'mine'], queryFn: bookingsApi.mine })

  const cancel = useMutation({
    mutationFn: (id: number) => bookingsApi.cancel(id),
    onSettled: () => {
      setConfirmingId(null)
      // Refresh the list (also after a failure: the server's view may differ from ours) and
      // free the time range on the detail page.
      void queryClient.invalidateQueries({ queryKey: ['bookings'] })
      void queryClient.invalidateQueries({ queryKey: ['availability'] })
    },
  })

  if (bookings.isPending) return <p>Loading…</p>
  if (bookings.isError) {
    return (
      <p>
        Failed to load your bookings: {errorMessage(bookings.error)}{' '}
        <button type="button" onClick={() => bookings.refetch()}>
          Retry
        </button>
      </p>
    )
  }

  const now = new Date()
  const { upcoming, history } = splitBookings(bookings.data, now)

  function renderBooking(booking: Booking, actionable: boolean) {
    const start = parseLocalDateTime(booking.startTime)
    const end = parseLocalDateTime(booking.endTime)
    const cancellable = actionable && isCancellable(booking, now)

    return (
      <li key={booking.id} className="booking-card">
        <div>
          <Link to={`/facilities/${booking.facilityId}`}>{booking.facilityName}</Link>{' '}
          <span className={booking.status === 'CONFIRMED' ? 'badge badge-ok' : 'badge'}>
            {booking.status === 'CONFIRMED' ? 'Confirmed' : 'Cancelled'}
          </span>
        </div>
        <div className="small">
          {formatDate(start)} · {formatTime(start)}–{formatTime(end)}
        </div>

        {actionable && cancellable && confirmingId !== booking.id && (
          <button type="button" onClick={() => setConfirmingId(booking.id)} disabled={cancel.isPending}>
            Cancel booking
          </button>
        )}
        {actionable && cancellable && confirmingId === booking.id && (
          <span className="confirm-row">
            Cancel this booking?{' '}
            <button type="button" onClick={() => cancel.mutate(booking.id)} disabled={cancel.isPending}>
              {cancel.isPending ? 'Cancelling…' : 'Yes, cancel it'}
            </button>{' '}
            <button type="button" onClick={() => setConfirmingId(null)} disabled={cancel.isPending}>
              Keep it
            </button>
          </span>
        )}
        {actionable && !cancellable && (
          <span className="small">
            Bookings can only be cancelled up to {CANCEL_WINDOW_HOURS} hours before they start.
          </span>
        )}
      </li>
    )
  }

  return (
    <div>
      <h1>My bookings</h1>

      {cancel.isError && (
        <p className="field-error" role="alert">
          Could not cancel the booking: {errorMessage(cancel.error)}
        </p>
      )}

      {bookings.data.length === 0 && (
        <p>
          You have no bookings yet. <Link to="/facilities">Browse facilities</Link>
        </p>
      )}

      {upcoming.length > 0 && (
        <>
          <h2>Upcoming</h2>
          <ul className="booking-list">{upcoming.map((b) => renderBooking(b, true))}</ul>
        </>
      )}

      {history.length > 0 && (
        <>
          <h2>History</h2>
          <ul className="booking-list">{history.map((b) => renderBooking(b, false))}</ul>
        </>
      )}
    </div>
  )
}
