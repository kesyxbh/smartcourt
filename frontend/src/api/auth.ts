import { request } from './client'
import type { LoginRequest, LoginResponse, RegisterRequest, User } from './types'

export const authApi = {
  register: (body: RegisterRequest) => request<User>('/auth/register', { method: 'POST', body }),
  login: (body: LoginRequest) => request<LoginResponse>('/auth/login', { method: 'POST', body }),
  me: () => request<User>('/auth/me'),
}