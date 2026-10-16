import { describe, expect, it } from 'vitest'
import {
  activeFilterCount,
  describeFilters,
  filtersToParams,
  fromApiFilter,
  parseFilters,
  sortValue,
  splitSortValue,
  typeLabel,
} from './facilityFilters'

const parse = (query: string) => parseFilters(new URLSearchParams(query))

describe('parseFilters', () => {
  it('returns no filters for an empty query string', () => {
    expect(parse('')).toEqual({})
  })

  it('reads every supported filter', () => {
    expect(
      parse('keyword=court&type=badminton&minPrice=10&maxPrice=30&minCapacity=6&sortBy=hourlyRate&direction=desc'),
    ).toEqual({
      keyword: 'court',
      type: 'BADMINTON',
      minPrice: 10,
      maxPrice: 30,
      minCapacity: 6,
      sortBy: 'hourlyRate',
      direction: 'desc',
    })
  })

  it('drops values that make no sense', () => {
    expect(parse('type=a%20b&minPrice=abc&maxPrice=-5&minCapacity=0&sortBy=password')).toEqual({})
    expect(parse('minCapacity=2.5')).toEqual({})
  })

  it('puts a reversed price range in order', () => {
    expect(parse('minPrice=50&maxPrice=20')).toEqual({ minPrice: 20, maxPrice: 50 })
  })

  it('defaults the direction to ascending and ignores a direction without a sort field', () => {
    expect(parse('sortBy=capacity')).toEqual({ sortBy: 'capacity', direction: 'asc' })
    expect(parse('direction=desc')).toEqual({})
  })

  it('keeps zero as a valid price', () => {
    expect(parse('minPrice=0')).toEqual({ minPrice: 0 })
  })
})

describe('filtersToParams', () => {
  it('omits empty values and the default sort', () => {
    expect(filtersToParams({}).toString()).toBe('')
    expect(filtersToParams({ sortBy: 'name', direction: 'asc' }).toString()).toBe('')
  })

  it('round-trips through parseFilters', () => {
    const filters = {
      keyword: 'court',
      type: 'TENNIS',
      minPrice: 5,
      maxPrice: 40,
      minCapacity: 4,
      sortBy: 'capacity',
      direction: 'desc',
    } as const
    expect(parseFilters(filtersToParams(filters))).toEqual(filters)
  })
})

describe('fromApiFilter', () => {
  it('ignores nulls, which is how the backend reports "not set"', () => {
    expect(
      fromApiFilter({
        keyword: null,
        type: 'BADMINTON',
        minPrice: null,
        maxPrice: 22,
        minCapacity: null,
        sortBy: 'hourlyRate',
        direction: 'asc',
      }),
    ).toEqual({ type: 'BADMINTON', maxPrice: 22, sortBy: 'hourlyRate', direction: 'asc' })
  })

  it('accepts a filter whose unset fields are simply absent', () => {
    expect(fromApiFilter({ type: 'TENNIS', minCapacity: 4 })).toEqual({ type: 'TENNIS', minCapacity: 4 })
  })

  it('validates like a URL: a hostile model answer is cleaned up', () => {
    expect(fromApiFilter({ type: 'a b', minPrice: -3, sortBy: 'password', minCapacity: -1 })).toEqual({})
    expect(fromApiFilter(null)).toEqual({})
  })
})

describe('describeFilters', () => {
  it('summarises the active filters in one line', () => {
    expect(describeFilters({})).toBe('all facilities')
    expect(
      describeFilters({ type: 'BADMINTON', maxPrice: 22, minCapacity: 6, sortBy: 'hourlyRate', direction: 'asc' }),
    ).toBe('Badminton · up to 22 / hour · for at least 6 people · Price (low to high)')
    expect(describeFilters({ minPrice: 10, maxPrice: 30 })).toBe('10–30 / hour')
  })
})

describe('sort helpers', () => {
  it('encodes and decodes the drop-down value', () => {
    expect(sortValue({})).toBe('name:asc')
    expect(sortValue({ sortBy: 'hourlyRate', direction: 'desc' })).toBe('hourlyRate:desc')
    expect(splitSortValue('hourlyRate:desc')).toEqual({ sortBy: 'hourlyRate', direction: 'desc' })
    expect(splitSortValue('nonsense')).toEqual({})
  })
})

describe('typeLabel and activeFilterCount', () => {
  it('labels known and unknown types', () => {
    expect(typeLabel('MEETING_ROOM')).toBe('Meeting room')
    expect(typeLabel('SWIMMING_POOL')).toBe('Swimming pool')
  })

  it('does not count the sort order as a filter', () => {
    expect(activeFilterCount({ keyword: 'a', minPrice: 0, sortBy: 'name', direction: 'desc' })).toBe(2)
  })
})
