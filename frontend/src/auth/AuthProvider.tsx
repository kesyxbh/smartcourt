import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi } from '../api/auth'
import { setUnauthorizedHandler } from '../api/client'
import { AuthContext, type AuthContextValue } from './AuthContext'
import { getSession, setSession as persistSession, type Session } from './storage'

// The login session is the only piece of global client state in the app, so React's built-in
// Context is enough. This provider must sit inside <QueryClientProvider>.
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [session, setSession] = useState<Session | null>(getSession)

  const replaceSession = useCallback(
    (next: Session | null) => {
      persistSession(next) // write to localStorage so a page reload keeps the login
      queryClient.clear() // cached data belongs to the previous user and must never leak to the next
      setSession(next)
    },
    [queryClient],
  )

  const login = useCallback(
    async (email: string, password: string) => {
      const response = await authApi.login({ email, password })
      replaceSession({ token: response.token, user: response.user })
    },
    [replaceSession],
  )

  const logout = useCallback(() => replaceSession(null), [replaceSession])

  // client.ts calls this when a request is answered with 401: drop the session, and
  // <RequireAuth> then sends the user to the login page.
  useEffect(() => {
    setUnauthorizedHandler(() => replaceSession(null))
    return () => setUnauthorizedHandler(null)
  }, [replaceSession])

  // Ask the backend whether the token is still valid: on page load and whenever the tab
  // becomes visible again. If it has expired the backend answers 401 and the callback above
  // logs the user out automatically.
  useQuery({ queryKey: ['me'], queryFn: authApi.me, enabled: session !== null, retry: false })

  const value = useMemo<AuthContextValue>(() => ({ session, login, logout }), [session, login, logout])

  return <AuthContext value={value}>{children}</AuthContext>
}
