import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { authApi } from '../api/auth'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { errorMessage } from '../lib/errors'

// These rules mirror the backend's RegisterRequest. The backend answers a validation failure
// with one generic sentence, so the browser has to say exactly which field is wrong.
const schema = z
  .object({
    username: z.string().trim().min(3, 'Username must be at least 3 characters').max(50, 'Username must be at most 50 characters'),
    email: z.email('Please enter a valid email address').max(100, 'Email must be at most 100 characters'),
    password: z.string().min(8, 'Password must be at least 8 characters').max(72, 'Password must be at most 72 characters'),
    confirmPassword: z.string(),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ['confirmPassword'], // show this error under the "confirm password" field
    message: 'The two passwords do not match',
  })
type FormValues = z.infer<typeof schema>

export function RegisterPage() {
  const { session } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  // Someone who is already logged in has no reason to register
  if (session) return <Navigate to="/facilities" replace />

  const onSubmit = handleSubmit(async (values) => {
    try {
      // A successful registration returns the user but no token, so the user must log in next
      await authApi.register({ username: values.username, email: values.email, password: values.password })
      navigate('/login', { replace: true, state: { from: location.state?.from, registered: true } })
    } catch (error) {
      const message =
        error instanceof ApiError && error.status === 409 ? 'That email or username is already registered' : errorMessage(error)
      setError('root', { message })
    }
  })

  return (
    <div className="form-card">
      <h1>Register</h1>

      <form onSubmit={onSubmit} noValidate>
        <label className="field">
          <span>Username</span>
          <input autoComplete="username" {...register('username')} />
          {errors.username && <span className="field-error">{errors.username.message}</span>}
        </label>

        <label className="field">
          <span>Email</span>
          <input type="email" autoComplete="email" {...register('email')} />
          {errors.email && <span className="field-error">{errors.email.message}</span>}
        </label>

        <label className="field">
          <span>Password (8–72 characters)</span>
          <input type="password" autoComplete="new-password" {...register('password')} />
          {errors.password && <span className="field-error">{errors.password.message}</span>}
        </label>

        <label className="field">
          <span>Confirm password</span>
          <input type="password" autoComplete="new-password" {...register('confirmPassword')} />
          {errors.confirmPassword && <span className="field-error">{errors.confirmPassword.message}</span>}
        </label>

        {errors.root && (
          <p className="field-error" role="alert">
            {errors.root.message}
          </p>
        )}

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Creating account…' : 'Register'}
        </button>
      </form>

      <p>
        Already have an account?{' '}
        <Link to="/login" state={location.state}>
          Log in
        </Link>
      </p>
    </div>
  )
}
