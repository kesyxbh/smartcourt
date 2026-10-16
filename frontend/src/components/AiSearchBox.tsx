import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { aiApi } from '../api/ai'
import type { FacilityFilters } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { aiErrorMessage } from '../lib/errors'
import { describeFilters, fromApiFilter } from '../lib/facilityFilters'

interface Props {
  /** Called with the filters the AI extracted; the page writes them into the URL. */
  onApply: (filters: FacilityFilters) => void
}

// One sentence in, structured filters out. The AI never produces the list itself: its answer is
// validated like any URL and then drives the normal, paged list query.
export function AiSearchBox({ onApply }: Props) {
  const { session } = useAuth()
  const location = useLocation()
  const [query, setQuery] = useState('')
  const [explanation, setExplanation] = useState<string | null>(null)

  const search = useMutation({
    mutationFn: (text: string) => aiApi.search({ query: text }),
    onSuccess: (data) => {
      // The server already validated the filters; parsing them again costs nothing and means
      // the page never trusts model output.
      const filters = fromApiFilter(data.filters)
      const recognised = Object.keys(filters).length > 0
      setExplanation(
        recognised
          ? data.explanation || `Understood as: ${describeFilters(filters)}`
          : 'No filters could be extracted from that request, so all facilities are shown.',
      )
      onApply(filters)
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const text = query.trim()
    if (text && !search.isPending) search.mutate(text)
  }

  // The AI endpoints are for logged-in users only (this also stops anonymous token spending)
  if (!session) {
    return (
      <section className="ai-search" aria-label="AI search">
        <p className="notice">
          <Link to="/login" state={{ from: location }}>
            Log in
          </Link>{' '}
          to describe what you need in your own words and let the AI fill in the filters.
        </p>
      </section>
    )
  }

  return (
    <section className="ai-search" aria-label="AI search">
      <form onSubmit={handleSubmit} className="ai-search-form">
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          maxLength={300}
          placeholder='Describe what you need, e.g. "a cheap badminton court under 25 per hour"'
          aria-label="Describe what you are looking for"
        />
        <button type="submit" disabled={search.isPending || query.trim() === ''}>
          {search.isPending ? 'Thinking…' : 'Ask AI'}
        </button>
      </form>
      {search.isError && (
        <p className="field-error" role="alert">
          {aiErrorMessage(search.error)}
        </p>
      )}
      {explanation && !search.isError && <p className="notice">{explanation}</p>}
    </section>
  )
}
