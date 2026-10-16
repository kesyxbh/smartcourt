import { createContext, useContext } from 'react'
import type { Session } from './storage'

export interface AuthContextValue {
  session: Session | null // null when logged out
  login: (email: string, password: string) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

// Any component can call `const { session } = useAuth()` to read the login state.
export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>')
  return value
}
