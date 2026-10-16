import { getToken } from '../auth/storage'

// Every request goes through here: one place builds the URL, attaches the token and handles errors.

export class ApiError extends Error {
  status: number // 0 means the server could not be reached at all

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

// When a request that carried a token is answered with 401 (token expired or invalid), the
// client must tell the auth state to drop the session. The client cannot change that state
// itself, so AuthProvider registers a callback here when it starts.
let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler
}

type Params = Record<string, string | number | undefined>

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'
  params?: Params // appended to the URL: ?a=1&b=2
  body?: unknown // sent as JSON in the request body
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  // Turns { keyword: 'bad', page: 1 } into ?keyword=bad&page=1 and skips empty values
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(options.params ?? {})) {
    if (value !== undefined && value !== '') query.set(key, String(value))
  }
  const queryString = query.toString()

  const token = getToken()
  const headers: Record<string, string> = {}
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  let response: Response
  try {
    response = await fetch(`/api${path}${queryString ? `?${queryString}` : ''}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    // fetch itself threw = the server was never reached (backend down, offline, ...).
    // That is different from the server answering with an error.
    throw new ApiError(0, 'Cannot reach the server. Please make sure the backend is running.')
  }

  if (!response.ok) {
    // A 401 on a request that carried a token = the login is no longer valid.
    // The login endpoint's own 401 just means "wrong password", so it does not count.
    // Only react if that token is still the current one: a slow request from a previous
    // session must not log out the user who has signed in since.
    if (response.status === 401 && token && token === getToken() && path !== '/auth/login') onUnauthorized?.()

    // The backend's error body looks like {"detail": "..."}, but a 401 may have no body at
    // all, so parse carefully.
    let message = response.statusText || `Request failed (status ${response.status})`
    try {
      const body = await response.json()
      if (typeof body.detail === 'string') message = body.detail
    } catch {
      // No JSON body: keep the default message above.
    }
    throw new ApiError(response.status, message)
  }

  // Status 200 does not mean the content is right: with a broken proxy this would be a web page.
  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('application/json')) {
    throw new ApiError(response.status, 'The server did not return JSON. Check the proxy setting in vite.config.ts.')
  }

  return response.json() as Promise<T>
}
