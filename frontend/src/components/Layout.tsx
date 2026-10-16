import { startTransition } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ChatWidget } from './ChatWidget'

export function Layout() {
  const { session, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    // Navigation and clearing the session must take effect together, so both go in one
    // startTransition. Otherwise React first renders "logged out but still on a protected
    // page", <RequireAuth> wins the race and redirects to the login page, and that redirect
    // overrides the /facilities navigation we wanted.
    startTransition(() => {
      navigate('/facilities')
      logout()
    })
  }

  return (
    <>
      <header className="site-header">
        <strong>SmartCourt</strong>
        <nav>
          <NavLink to="/facilities">Facilities</NavLink>
          {session && <NavLink to="/my-bookings">My bookings</NavLink>}
        </nav>
        <div className="user-area">
          {session ? (
            <>
              <span>{session.user.username}</span>
              <button type="button" onClick={handleLogout}>
                Log out
              </button>
            </>
          ) : (
            <>
              <NavLink to="/login">Log in</NavLink>
              <NavLink to="/register">Register</NavLink>
            </>
          )}
        </div>
      </header>
      <main>
        <Outlet />
      </main>
      {/* Keyed by user: logging in as someone else starts a fresh conversation */}
      <ChatWidget key={session?.user.id ?? 'guest'} />
    </>
  )
}
