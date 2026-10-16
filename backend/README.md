# SmartCourt Backend

REST API for booking shared facilities such as sports courts and meeting rooms. Users register, log in,
search facilities, and book time slots. The API guarantees that one time slot can never be booked twice,
even when two requests arrive at the same moment, and it includes an AI booking assistant that can search,
book and cancel on behalf of the signed-in user through function calling.

## Tech stack

| Area | Choice |
|---|---|
| Language / runtime | Java 17 |
| Framework | Spring Boot 3.5 (Web, Validation, Data JPA, Security) |
| Database | H2 in-memory, PostgreSQL compatibility mode (nothing to install) |
| Auth | JWT (jjwt 0.12), BCrypt password hashing, stateless sessions |
| AI | Spring AI 1.1 with any OpenAI-compatible API (DeepSeek by default) |
| Tests | JUnit 5, MockMvc, AssertJ, a scripted fake LLM server |
| Build | Maven |

## Run it

Requires JDK 17 and Maven. No database or Docker needed.

```powershell
$env:JAVA_HOME = 'D:\java learning\javase-jdk'   # adjust to your JDK 17 location
mvn spring-boot:run
```

The API is at <http://localhost:8080>. Twelve demo facilities of six types are inserted on startup. Data lives in memory,
so it is reset on every restart. The H2 console is at <http://localhost:8080/h2-console>
(JDBC URL `jdbc:h2:mem:smartcourt`, user `sa`, empty password).

### Enable the AI features

The application starts without a key, and the two AI endpoints answer `503` until one is configured.

```powershell
$env:LLM_API_KEY  = 'your-key'                    # required, never commit it
$env:LLM_BASE_URL = 'https://api.deepseek.com'    # optional, this is the default
$env:LLM_MODEL    = 'deepseek-flash'               # optional, this is the default
```

Any OpenAI-compatible provider works by changing these three variables.

### Other configuration

| Variable | Default | Meaning |
|---|---|---|
| `JWT_SECRET` | a development value | HMAC key for tokens, at least 32 bytes. Always set it outside development. |

Token lifetime is 120 minutes (`backend.jwt.expiration-minutes`).

## API

Everything is under `/api`. Errors use the RFC 9457 problem format, `{"status":409,"detail":"..."}`.

| Method and path | Auth | Purpose | Main errors |
|---|---|---|---|
| `POST /auth/register` | public | Create an account | 400 invalid input, 409 email or username taken |
| `POST /auth/login` | public | Returns `{token, user}` | 401 |
| `GET /auth/me` | token | The signed-in user | 401 |
| `GET /facilities` | public | Search, filter, sort, paginate | |
| `GET /facilities/types` | public | The facility types that exist | |
| `GET /facilities/{id}` | public | One facility | 404 |
| `GET /facilities/{id}/availability?date=2026-10-20` | public | Booked time ranges on that day | 400, 404 |
| `POST /bookings` | token | Book a time slot | 400, 404, 409 slot taken or facility closed |
| `GET /bookings/me` | token | My bookings, newest first | |
| `PATCH /bookings/{id}/cancel` | token | Cancel my booking (at least 24 hours before it starts) | 403, 404, 409 |
| `POST /ai/chat` | token | Booking assistant: `{conversationId?, message}` | 400, 502, 503 |
| `POST /ai/search` | token | Natural language to a facility filter: `{query}` | 400, 502, 503 |

`GET /facilities` parameters: `keyword`, `type`, `minPrice`, `maxPrice`, `minCapacity`, `page` (default 0),
`size` (default 10, at most 100), `sortBy` (`name`, `hourlyRate` or `capacity`), `direction` (`asc` or `desc`).

Authenticated calls send `Authorization: Bearer <token>`.

### AI response shapes

The backend DTO records are the source of truth for these shapes; clients follow them. Changes are
additive (new optional fields), never renames.

`POST /ai/chat` returns `{conversationId, reply, toolCalls}`, where each tool call is
`{name, args, ok}` and `args` is an object holding only the arguments the model supplied:

```json
{ "conversationId": "c-1", "reply": "Booked Court A tomorrow 15:00-16:00.",
  "toolCalls": [ { "name": "createBooking", "ok": true,
                   "args": { "facilityId": 1, "startTime": "2026-10-14T15:00:00", "endTime": "2026-10-14T16:00:00" } } ] }
```

`POST /ai/search` returns the validated `filters` (unset fields are `null`), a one-sentence
`explanation` built from them, and the first page of `results`. `filter` is an alias of `filters`
kept under its earlier name.

```json
{ "filters": { "keyword": null, "type": "TENNIS", "minPrice": null, "maxPrice": 50, "minCapacity": 4,
               "sortBy": "hourlyRate", "direction": "asc" },
  "explanation": "Showing TENNIS, for at least 4 people, up to 50 per hour, sorted by price (lowest first).",
  "results": { "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 } }
```

```powershell
$base = 'http://localhost:8080/api'
Invoke-RestMethod "$base/auth/register" -Method Post -ContentType 'application/json' `
  -Body '{"username":"alice","email":"alice@example.com","password":"password123"}'
$login = Invoke-RestMethod "$base/auth/login" -Method Post -ContentType 'application/json' `
  -Body '{"email":"alice@example.com","password":"password123"}'
$auth = @{ Authorization = "Bearer $($login.token)" }

Invoke-RestMethod "$base/facilities?type=TENNIS&sortBy=hourlyRate&direction=desc"

$day = (Get-Date).AddDays(3).ToString('yyyy-MM-dd')
Invoke-RestMethod "$base/bookings" -Method Post -Headers $auth -ContentType 'application/json' `
  -Body "{`"facilityId`":1,`"startTime`":`"${day}T10:00:00`",`"endTime`":`"${day}T11:00:00`"}"
Invoke-RestMethod "$base/bookings/me" -Headers $auth
```

## Tests

```powershell
mvn test
```

45 tests, none of which needs network access or an API key.

| Class | What it proves |
|---|---|
| `BookingConcurrencyTest` | Eight threads start together and book the same slot: exactly one succeeds |
| `BookingFlowIntegrationTest` | Overlap rules, cancellation rules, ownership, validation, over HTTP |
| `AuthFlowIntegrationTest` | Registration, case-insensitive email, login, token handling, tampered tokens |
| `FacilitySearchIntegrationTest` | Filters, sorting, pagination, unknown ids |
| `AiEndpointsIntegrationTest` | AI endpoint contract without a key, the assistant tools, validation of model output |
| `AiChatFlowIntegrationTest` | The whole chat path against a scripted fake model: tool call, booking created, memory, errors |

## Known limitations

- Data is in memory. A `postgres` profile is not included yet.
- Times are local date-times without a time zone.
- A JWT cannot be revoked before it expires.
- There is no admin role: every signed-in user has the same permissions.
- AI conversation memory is in memory and per instance.
- The automated tests use a scripted fake model. The AI features were tried against DeepSeek `deepseek-flash`
  through the HTTP API on 2026-10-14 and through the web interface on 2026-10-15 (search, availability,
  confirmation, booking). That was a handful of requests, not a systematic evaluation.
