import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { z } from 'zod'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { errorMessage } from '../lib/errors'

// Validate in the browser first: there is no point sending an obviously malformed request
// (and the backend only answers a validation failure with one generic sentence).
const schema = z.object({
  email: z.email('Please enter a valid email address'),
  password: z.string().min(1, 'Please enter your password'),
})
type FormValues = z.infer<typeof schema>

export function LoginPage() {
  const { session, login } = useAuth()
  const location = useLocation()
  // A navigation can carry some "state" along: RequireAuth passes `from` (where the user
  // wanted to go) and the register page passes `registered`.
  const from = location.state?.from
  const target = from ? `${from.pathname}${from.search}` : '/facilities'

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  // After a successful login `session` is no longer null and this is the one place that
  // redirects, so it cannot race with any other navigation.
  if (session) return <Navigate to={target} replace />

  const onSubmit = handleSubmit(async (values) => {
    try {
      await login(values.email, values.password)
    } catch (error) {
      const message = error instanceof ApiError && error.status === 401 ? 'Incorrect email or password' : errorMessage(error)
      setError('root', { message })
    }
  })

  return (
    <div className="form-card">
      <h1>Log in</h1>
      {location.state?.registered && <p className="notice">Registration successful. Please log in.</p>}
      {from && !location.state?.registered && <p className="notice">Please log in to continue.</p>}

      <form onSubmit={onSubmit} noValidate>
        <label className="field">
          <span>Email</span>
          <input type="email" autoComplete="email" {...register('email')} />
          {errors.email && <span className="field-error">{errors.email.message}</span>}
        </label>

        <label className="field">
          <span>Password</span>
          <input type="password" autoComplete="current-password" {...register('password')} />
          {errors.password && <span className="field-error">{errors.password.message}</span>}
        </label>

        {errors.root && (
          <p className="field-error" role="alert">
            {errors.root.message}
          </p>
        )}

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Logging in…' : 'Log in'}
        </button>
      </form>

      <p>
        Don&apos;t have an account yet?{' '}
        <Link to="/register" state={location.state}>
          Register
        </Link>
      </p>
    </div>
  )
}
