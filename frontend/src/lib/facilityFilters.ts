import type { ApiFacilityFilter, FacilityFilters, SortDirection, SortField } from '../api/types'

/** Used until (or unless) the list of types can be loaded from GET /api/facilities/types. */
export const FACILITY_TYPES = [
  { value: 'BADMINTON', label: 'Badminton' },
  { value: 'TENNIS', label: 'Tennis' },
  { value: 'BASKETBALL', label: 'Basketball' },
  { value: 'MEETING_ROOM', label: 'Meeting room' },
] as const

/** The sort drop-down: one value encodes "field:direction". */
export const SORT_OPTIONS = [
  { value: 'name:asc', label: 'Name (A to Z)' },
  { value: 'name:desc', label: 'Name (Z to A)' },
  { value: 'hourlyRate:asc', label: 'Price (low to high)' },
  { value: 'hourlyRate:desc', label: 'Price (high to low)' },
  { value: 'capacity:desc', label: 'Capacity (large to small)' },
  { value: 'capacity:asc', label: 'Capacity (small to large)' },
] as const

const SORT_FIELDS: readonly SortField[] = ['name', 'hourlyRate', 'capacity']
const DEFAULT_SORT = 'name:asc'

/** "BADMINTON" -> "Badminton", "MEETING_ROOM" -> "Meeting room"; unknown types are humanised. */
export function typeLabel(type: string): string {
  const known = FACILITY_TYPES.find((t) => t.value === type)
  if (known) return known.label
  const words = type.toLowerCase().replaceAll('_', ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}

function parsePrice(raw: string | null): number | undefined {
  if (raw === null || raw.trim() === '') return undefined
  const value = Number(raw)
  return Number.isFinite(value) && value >= 0 ? value : undefined
}

function parsePositiveInt(raw: string | null): number | undefined {
  if (raw === null || raw.trim() === '') return undefined
  const value = Number(raw)
  return Number.isInteger(value) && value > 0 ? value : undefined
}

/**
 * Reads the filters out of the URL. URL values are untrusted (anybody can type anything),
 * so every field is validated and silently dropped when it makes no sense.
 */
export function parseFilters(params: URLSearchParams): FacilityFilters {
  const filters: FacilityFilters = {}

  const keyword = (params.get('keyword') ?? '').trim().slice(0, 100)
  if (keyword) filters.keyword = keyword

  const type = (params.get('type') ?? '').trim().toUpperCase()
  if (/^[A-Z_]{1,30}$/.test(type)) filters.type = type

  const first = parsePrice(params.get('minPrice'))
  const second = parsePrice(params.get('maxPrice'))
  // A reversed range would always be empty, so put the bounds in order instead.
  const reversed = first !== undefined && second !== undefined && first > second
  const minPrice = reversed ? second : first
  const maxPrice = reversed ? first : second
  if (minPrice !== undefined) filters.minPrice = minPrice
  if (maxPrice !== undefined) filters.maxPrice = maxPrice

  const minCapacity = parsePositiveInt(params.get('minCapacity'))
  if (minCapacity !== undefined) filters.minCapacity = minCapacity

  const sortBy = params.get('sortBy') as SortField | null
  if (sortBy && SORT_FIELDS.includes(sortBy)) {
    filters.sortBy = sortBy
    const direction = params.get('direction')
    filters.direction = direction === 'desc' ? 'desc' : 'asc'
  }

  return filters
}

/**
 * Converts the filter returned by POST /api/ai/search (unset fields are null) into our own
 * filters. It goes through the same validation as a URL: model output is untrusted too.
 */
export function fromApiFilter(raw: Partial<ApiFacilityFilter> | null | undefined): FacilityFilters {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(raw ?? {})) {
    if (value !== null && value !== undefined) params.set(key, String(value))
  }
  return parseFilters(params)
}

/** The "field:direction" value of the sort drop-down for these filters. */
export function sortValue(filters: FacilityFilters): string {
  return `${filters.sortBy ?? 'name'}:${filters.direction ?? 'asc'}`
}

/** Splits a drop-down value back into field and direction. */
export function splitSortValue(value: string): { sortBy?: SortField; direction?: SortDirection } {
  const [sortBy, direction] = value.split(':')
  return parseFilters(new URLSearchParams({ sortBy: sortBy ?? '', direction: direction ?? '' }))
}

/** Writes the filters into URL parameters, leaving out empty values and the default sort. */
export function filtersToParams(filters: FacilityFilters): URLSearchParams {
  const params = new URLSearchParams()
  if (filters.keyword) params.set('keyword', filters.keyword)
  if (filters.type) params.set('type', filters.type)
  if (filters.minPrice !== undefined) params.set('minPrice', String(filters.minPrice))
  if (filters.maxPrice !== undefined) params.set('maxPrice', String(filters.maxPrice))
  if (filters.minCapacity !== undefined) params.set('minCapacity', String(filters.minCapacity))
  if (sortValue(filters) !== DEFAULT_SORT) {
    params.set('sortBy', filters.sortBy ?? 'name')
    params.set('direction', filters.direction ?? 'asc')
  }
  return params
}

/** How many filters are active (the sort order does not count). */
export function activeFilterCount(filters: FacilityFilters): number {
  return [filters.keyword, filters.type, filters.minPrice, filters.maxPrice, filters.minCapacity].filter(
    (v) => v !== undefined,
  ).length
}

/** A short sentence describing the filters, e.g. "Badminton · up to 22 / hour · Price (low to high)". */
export function describeFilters(filters: FacilityFilters): string {
  const parts: string[] = []
  if (filters.keyword) parts.push(`matching "${filters.keyword}"`)
  if (filters.type) parts.push(typeLabel(filters.type))
  if (filters.minPrice !== undefined && filters.maxPrice !== undefined) {
    parts.push(`${filters.minPrice}–${filters.maxPrice} / hour`)
  } else if (filters.minPrice !== undefined) {
    parts.push(`from ${filters.minPrice} / hour`)
  } else if (filters.maxPrice !== undefined) {
    parts.push(`up to ${filters.maxPrice} / hour`)
  }
  if (filters.minCapacity !== undefined) parts.push(`for at least ${filters.minCapacity} people`)
  const sort = SORT_OPTIONS.find((o) => o.value === sortValue(filters))
  if (sort && sort.value !== DEFAULT_SORT) parts.push(sort.label)
  return parts.length > 0 ? parts.join(' · ') : 'all facilities'
}
