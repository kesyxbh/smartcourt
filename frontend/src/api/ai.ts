import { request } from './client'
import type { AiSearchRequest, AiSearchResponse, ChatRequest, ChatResponse } from './types'

// Both endpoints require a logged-in user (which also keeps anonymous visitors from spending
// LLM tokens). Without an API key on the server they answer 503.
export const aiApi = {
  /** Booking assistant. The server decides whose bookings the tools touch: the user is never a parameter. */
  chat: (body: ChatRequest) => request<ChatResponse>('/ai/chat', { method: 'POST', body }),
  /** Turns a free-text request into the structured filters of GET /api/facilities. */
  search: (body: AiSearchRequest) => request<AiSearchResponse>('/ai/search', { method: 'POST', body }),
}
