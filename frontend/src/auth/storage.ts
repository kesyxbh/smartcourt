import type { User } from '../api/types'

export interface Session {
  token: string
  user: User
}

const STORAGE_KEY = 'smartcourt.session'

// Also keep a copy in memory: if the browser blocks localStorage (private mode, ...) the app
// still works until the page is reloaded.
let current: Session | null = readFromStorage()

function readFromStorage(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<Session>
    return typeof parsed.token === 'string' && parsed.user ? { token: parsed.token, user: parsed.user } : null
  } catch {
    return null
  }
}

export function getSession(): Session | null {
  return current
}

export function getToken(): string | null {
  return current?.token ?? null
}

export function setSession(session: Session | null): void {
  current = session
  try {
    if (session) localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Could not persist: the in-memory copy is all we have.
  }
}
