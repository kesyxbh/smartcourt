import { request } from './client'
import type { Facility, FacilityFilters, Page, Slot } from './types'

export interface FacilityQuery extends FacilityFilters {
  page?: number
  size?: number
}

export function fetchFacilities(query: FacilityQuery) {
  return request<Page<Facility>>('/facilities', { params: { ...query } })
}

/** The facility types that exist, e.g. ["BADMINTON", "MEETING_ROOM"]. Public endpoint. */
export function fetchFacilityTypes() {
  return request<string[]>('/facilities/types')
}

export function fetchFacility(id: number) {
  return request<Facility>(`/facilities/${id}`)
}

/** The time ranges already booked on one day. `date` looks like "2026-10-04". */
export function fetchAvailability(id: number, date: string) {
  return request<Slot[]>(`/facilities/${id}/availability`, { params: { date } })
}
