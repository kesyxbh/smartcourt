import { ApiError } from '../api/client'

// Turns any error into one sentence for the user. The backend's own messages are terse
// and some are very generic, so a few cases are rephrased here.
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 400) return 'Some input is invalid. Please check it and try again.'
    // When the backend is down, Vite's proxy answers 500 on its behalf, so a 5xx usually
    // means "the backend is not running".
    if (error.status >= 500) return 'The server is temporarily unavailable. Please try again later (during development, make sure the backend is running).'
    return error.message
  }
  return 'Something went wrong. Please try again later.'
}

// The AI endpoints answer 503 when no API key is configured and 502 when the model provider
// failed. Both carry a precise explanation, so show the server's sentence as it is.
export function aiErrorMessage(error: unknown): string {
  if (error instanceof ApiError && (error.status === 502 || error.status === 503)) return error.message
  return errorMessage(error)
}
