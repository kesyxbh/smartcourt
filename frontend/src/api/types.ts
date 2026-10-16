// Shapes of the JSON exchanged with the backend. Keep them in sync with the backend DTO records.

export type FacilityStatus = 'ACTIVE' | 'MAINTENANCE'

export interface Facility {
  id: number
  name: string
  type: string
  location: string
  capacity: number
  hourlyRate: number
  status: FacilityStatus
}

export interface Page<T> {
  content: T[]
  page: number // zero-based
  size: number
  totalElements: number
  totalPages: number
}

export interface User {
  id: number
  username: string
  email: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  token: string
  user: User
}

export type BookingStatus = 'CONFIRMED' | 'CANCELLED'

/** One occupied time range. The backend deliberately does not reveal who booked it. */
export interface Slot {
  startTime: string // e.g. "2026-10-04T10:00:00", no time zone
  endTime: string
}

export interface BookingRequest {
  facilityId: number
  startTime: string
  endTime: string
}

export interface Booking {
  id: number
  facilityId: number
  facilityName: string
  startTime: string
  endTime: string
  status: BookingStatus
  /**
   * Whether the server will currently accept a cancellation. Optional: when the backend
   * does not send it, the client falls back to the 24-hour rule (see lib/cancellation.ts).
   */
  cancellable?: boolean
}

export type SortField = 'name' | 'hourlyRate' | 'capacity'
export type SortDirection = 'asc' | 'desc'

/** The filters understood by GET /api/facilities (everything except paging). */
export interface FacilityFilters {
  keyword?: string
  type?: string
  minPrice?: number
  maxPrice?: number
  minCapacity?: number
  sortBy?: SortField
  direction?: SortDirection
}

// ---- AI endpoints ----

export interface ChatRequest {
  /** Omit it to start a conversation; send back the id from the previous response afterwards. */
  conversationId?: string
  message: string
}

/** One tool the assistant invoked while producing its reply. */
export interface ToolCall {
  name: string
  args: Record<string, unknown> // the arguments the model supplied, without the ones it left out
  ok: boolean // false when the tool refused or failed (e.g. a booking conflict)
}

export interface ChatResponse {
  conversationId: string
  reply: string
  toolCalls: ToolCall[]
}

export interface AiSearchRequest {
  query: string
}

/** The filter as the backend sends it: fields that were not set are left out (or null). */
export interface ApiFacilityFilter {
  keyword?: string | null
  type?: string | null
  minPrice?: number | null
  maxPrice?: number | null
  minCapacity?: number | null
  sortBy?: string | null
  direction?: string | null
}

/**
 * The model never returns search results, only the filters. The client applies them to the
 * ordinary GET /api/facilities endpoint, so paging and validation behave as for a typed query.
 */
export interface AiSearchResponse {
  filters: ApiFacilityFilter // already validated by the server
  explanation: string // one sentence built from the validated filters
}
