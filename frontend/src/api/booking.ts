import { request } from './client'
import type { Booking, BookingRequest } from './types'

export const bookingsApi = {
  create: (body: BookingRequest) => request<Booking>('/bookings', { method: 'POST', body }),
}