import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { fetchFacilities, fetchFacilityTypes } from '../api/facilities'
import { AiSearchBox } from '../components/AiSearchBox'
import { errorMessage } from '../lib/errors'
import {
  FACILITY_TYPES,
  SORT_OPTIONS,
  activeFilterCount,
  filtersToParams,
  parseFilters,
  sortValue,
  splitSortValue,
  typeLabel,
} from '../lib/facilityFilters'

const PAGE_SIZE = 4

export function FacilitiesPage() {
  // The search conditions live in the URL (?keyword=...&type=...&page=...): reloading,
  // going back and sharing the link all keep them.
  const [searchParams, setSearchParams] = useSearchParams()
  const filters = parseFilters(searchParams)
  // URL content is untrusted: anything that is not a non-negative number means page 0
  // (the backend fails on page=-1).
  const page = Math.max(0, Number.parseInt(searchParams.get('page') ?? '0', 10) || 0)
  const [formError, setFormError] = useState<string | null>(null)

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['facilities', filters, page],
    queryFn: () => fetchFacilities({ ...filters, page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  })

  // The type drop-down offers the types that really exist; the built-in list is the fallback
  // while loading. A type that is only present in the URL is added so the select stays truthful.
  const types = useQuery({ queryKey: ['facility-types'], queryFn: fetchFacilityTypes, staleTime: 5 * 60_000 })
  const typeOptions = [
    ...new Set([
      ...(types.data ?? FACILITY_TYPES.map((t) => t.value)),
      ...(filters.type ? [filters.type] : []),
    ]),
  ]

  function handleApply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const text = (name: string) => String(form.get(name) ?? '').trim()

    const minPrice = text('minPrice')
    const maxPrice = text('maxPrice')
    if (minPrice !== '' && maxPrice !== '' && Number(minPrice) > Number(maxPrice)) {
      setFormError('The minimum price cannot be higher than the maximum price.')
      return
    }
    setFormError(null)

    const next = parseFilters(
      new URLSearchParams({
        keyword: text('keyword'),
        type: text('type'),
        minPrice,
        maxPrice,
        minCapacity: text('minCapacity'),
        ...splitSortValue(text('sort')),
      }),
    )
    setSearchParams(filtersToParams(next)) // a new search always goes back to the first page
  }

  function clearFilters() {
    setFormError(null)
    setSearchParams({})
  }

  function goToPage(next: number) {
    const params = new URLSearchParams(searchParams)
    if (next <= 0) params.delete('page')
    else params.set('page', String(next))
    setSearchParams(params)
  }

  const hasFilters = activeFilterCount(filters) > 0

  return (
    <div>
      <h1>Facilities</h1>

      <AiSearchBox
        onApply={(next) => {
          setFormError(null)
          setSearchParams(filtersToParams(next))
        }}
      />

      {/* The key remounts the uncontrolled form whenever the URL filters change (AI search, reset, back button) */}
      <form key={filtersToParams(filters).toString()} onSubmit={handleApply} className="filters">
        <label className="field">
          <span>Keyword</span>
          <input name="keyword" defaultValue={filters.keyword ?? ''} placeholder="Name, location or type" />
        </label>

        <label className="field">
          <span>Type</span>
          <select name="type" defaultValue={filters.type ?? ''}>
            <option value="">All types</option>
            {typeOptions.map((value) => (
              <option key={value} value={value}>
                {typeLabel(value)}
              </option>
            ))}
          </select>
        </label>

        <label className="field">
          <span>Min price / hour</span>
          <input name="minPrice" type="number" min="0" step="0.01" defaultValue={filters.minPrice ?? ''} />
        </label>

        <label className="field">
          <span>Max price / hour</span>
          <input name="maxPrice" type="number" min="0" step="0.01" defaultValue={filters.maxPrice ?? ''} />
        </label>

        <label className="field">
          <span>Min people</span>
          <input name="minCapacity" type="number" min="1" step="1" defaultValue={filters.minCapacity ?? ''} />
        </label>

        <label className="field">
          <span>Sort by</span>
          <select name="sort" defaultValue={sortValue(filters)}>
            {SORT_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </label>

        <div className="filter-actions">
          <button type="submit">Apply</button>
          <button type="button" onClick={clearFilters}>
            Reset
          </button>
        </div>
      </form>
      {formError && (
        <p className="field-error" role="alert">
          {formError}
        </p>
      )}

      {isPending && <p>Loading…</p>}
      {isError && (
        <p>
          Failed to load: {errorMessage(error)}{' '}
          <button type="button" onClick={() => refetch()}>
            Retry
          </button>
        </p>
      )}

      {data && data.content.length === 0 && (
        <p>
          No facilities match your search.{' '}
          {hasFilters && (
            <button type="button" onClick={clearFilters}>
              Clear filters
            </button>
          )}
        </p>
      )}

      {data && data.content.length > 0 && (
        <>
          <ul className="facility-list">
            {data.content.map((f) => (
              <li key={f.id} className="facility-card">
                <Link to={`/facilities/${f.id}`}>{f.name}</Link>
                <span className="small">
                  {typeLabel(f.type)} · {f.location} · up to {f.capacity} people · {f.hourlyRate.toFixed(2)} / hour
                </span>
                {f.status !== 'ACTIVE' && <span className="badge badge-warn">Under maintenance</span>}
              </li>
            ))}
          </ul>

          <p>
            {data.totalElements} {data.totalElements === 1 ? 'facility' : 'facilities'} found
          </p>
          <button type="button" onClick={() => goToPage(page - 1)} disabled={page <= 0}>
            Previous
          </button>
          <span>
            {' '}
            Page {page + 1} of {data.totalPages}{' '}
          </span>
          <button type="button" onClick={() => goToPage(page + 1)} disabled={page >= data.totalPages - 1}>
            Next
          </button>
        </>
      )}
    </div>
  )
}
