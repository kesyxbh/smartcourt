import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { aiApi } from '../api/ai'
import type { ToolCall } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { aiErrorMessage } from '../lib/errors'

interface ChatMessage {
  role: 'user' | 'assistant' | 'error'
  text: string
  toolCalls?: ToolCall[]
}

const SUGGESTIONS = ['Which facilities do you have?', 'Show my bookings', 'What is free tomorrow afternoon?']

// {facilityId: 1, date: "2026-10-14"} -> "facilityId=1, date=2026-10-14" (shown as a tooltip)
function summariseArgs(args: Record<string, unknown> | undefined): string {
  return Object.entries(args ?? {})
    .map(([key, value]) => `${key}=${String(value)}`)
    .join(', ')
}

// Floating booking assistant. It is mounted once in the Layout and re-created when the user
// changes (see the `key` there), so a new login never inherits the previous conversation.
export function ChatWidget() {
  const { session } = useAuth()
  const location = useLocation()
  const queryClient = useQueryClient()

  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [draft, setDraft] = useState('')
  // Issued by the server with the first reply and sent back with every later message.
  const [conversationId, setConversationId] = useState<string | undefined>(undefined)
  const endRef = useRef<HTMLDivElement>(null)

  const send = useMutation({
    mutationFn: (message: string) => aiApi.chat({ conversationId, message }),
    onSuccess: (data) => {
      const toolCalls = data.toolCalls ?? []
      setConversationId(data.conversationId)
      setMessages((current) => [...current, { role: 'assistant', text: data.reply, toolCalls }])
      // If the assistant changed anything, the lists the user may be looking at are stale.
      if (toolCalls.some((call) => call.ok)) {
        void queryClient.invalidateQueries({ queryKey: ['bookings'] })
        void queryClient.invalidateQueries({ queryKey: ['availability'] })
      }
    },
    onError: (error) => {
      setMessages((current) => [...current, { role: 'error', text: aiErrorMessage(error) }])
    },
  })

  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: 'end' })
  }, [messages, send.isPending, open])

  function submit(text: string) {
    const message = text.trim()
    if (!message || send.isPending) return
    setMessages((current) => [...current, { role: 'user', text: message }])
    setDraft('')
    send.mutate(message)
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    submit(draft)
  }

  if (!open) {
    return (
      <button type="button" className="chat-launcher" onClick={() => setOpen(true)}>
        Ask the assistant
      </button>
    )
  }

  return (
    <aside className="chat-panel" aria-label="Booking assistant">
      <header className="chat-header">
        <strong>Booking assistant</strong>
        <button type="button" onClick={() => setOpen(false)} aria-label="Close the assistant">
          ×
        </button>
      </header>

      {!session ? (
        <div className="chat-body">
          <p>
            Please{' '}
            <Link to="/login" state={{ from: location }}>
              log in
            </Link>{' '}
            to use the booking assistant.
          </p>
        </div>
      ) : (
        <>
          <div className="chat-body" aria-live="polite">
            {messages.length === 0 && (
              <div>
                <p className="small">Ask about facilities, free time slots or your bookings. Try:</p>
                <div className="chat-suggestions">
                  {SUGGESTIONS.map((s) => (
                    <button key={s} type="button" onClick={() => submit(s)} disabled={send.isPending}>
                      {s}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {messages.map((m, index) => (
              <div key={index} className={`chat-message chat-${m.role}`}>
                <p>{m.text}</p>
                {m.toolCalls && m.toolCalls.length > 0 && (
                  <p className="chat-tools">
                    {m.toolCalls.map((call, i) => (
                      <span key={i} className={call.ok ? 'badge badge-ok' : 'badge badge-warn'} title={summariseArgs(call.args)}>
                        {call.name} {call.ok ? '✓' : '✗'}
                      </span>
                    ))}
                  </p>
                )}
              </div>
            ))}

            {send.isPending && <p className="chat-message chat-assistant small">Thinking…</p>}
            <div ref={endRef} />
          </div>

          <form className="chat-form" onSubmit={handleSubmit}>
            <input
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder="Type a message…"
              aria-label="Message"
              maxLength={1000}
            />
            <button type="submit" disabled={send.isPending || draft.trim() === ''}>
              Send
            </button>
          </form>
        </>
      )}
    </aside>
  )
}
