# Booking chat: frontend integration guide (Customer + Provider apps)

This describes the chat API **as deployed on https://jobhub.pentarixlabs.com** (chat v1, commit `c7f4bc9`,
verified live on 2026-11-21). Where the apps asked for something that isn't built yet, the
[Gaps](#8-gaps-not-in-the-deployed-api) section says so plainly. Don't build against those items until they ship.

Base reference: [CHAT_API.md](CHAT_API.md). This guide answers the app teams' questions and adds test scenarios.

> **v1.1 (built, not deployed yet):** delivery ticks (Sent/Delivered/Seen), image and PDF attachments, and a
> machine-readable `code` on every chat error. See [CHAT_DELIVERY_ATTACHMENTS.md](CHAT_DELIVERY_ATTACHMENTS.md).
> That resolves gaps G2 and G7 below once deployed.

---

## 1. Chat availability

### The rule that is actually deployed

> ⚠️ The deployed rule is **not** "30 minutes before scheduled start". Chat opens when the booking is
> **confirmed** (paid or wallet reserved), whatever the scheduled time is.

| Booking `jobStatus` | Read | Send |
|---|---|---|
| `PENDING`, `AWAITING_PAYMENT` (unpaid / not confirmed) | ❌ 409 | ❌ 409 |
| `CONFIRMED`, `IN_PROGRESS`, `AWAITING_COMPLETION_CONFIRMATION`, `NO_SHOW_REVIEW` | ✅ | ✅ no time limit |
| `COMPLETED` | ✅ | ✅ until `sendUntil`, then read-only |
| `CANCELLED`, chat had messages before cancellation | ✅ | ✅ until `sendUntil`, then read-only |
| `CANCELLED`, chat never used | ❌ 409 | ❌ 409 |
| `COMPLETED`, no messages and `sendUntil` passed | ❌ 409 (not listed in the inbox) | ❌ |

Answers to the specific questions:

| Question | Deployed behaviour |
|---|---|
| Open time based on scheduled start or actual start? | **Neither.** It opens at confirmation (`CONFIRMED`). |
| 24 h measured from `actualFinishedAt` or `completionConfirmedAt`? | **Neither exactly.** It's measured from the booking's last update after it became `COMPLETED`/`CANCELLED`. In normal use that's the moment the customer confirms completion (≈ `completionConfirmedAt`) or the cancellation time. While the booking is `AWAITING_COMPLETION_CONFIRMATION` (provider finished, customer hasn't confirmed), chat stays open with no limit. See gap G3. |
| Early start | Chat is already open from confirmation, so an early start doesn't change anything. |
| Reschedule | There is no reschedule feature in the backend. |
| Cancellation | See the table: 24 h of sending only if the chat had messages, then read-only. |
| Expiry (unpaid request times out) | The unpaid job is cancelled before it was ever confirmed, so chat was never open: 409. |
| Unpaid | `PENDING` / `AWAITING_PAYMENT` → 409 `Chat opens once the booking is confirmed`. |
| Disputed | `NO_SHOW_REVIEW` (no-show dispute) is fully open. There is no other dispute status. |
| Read outside the window? | Yes. After `sendUntil` the chat is **read-only forever** (message box hidden, history visible). It's only blocked entirely when it was never open or never used (rows above). |
| Availability in booking/provider responses? | **No.** Booking responses carry no chat fields. Only chat responses carry `canSend` (inbox and thread) and `sendUntil` (thread). See gap G1. |

### What the app should use today

- **Message box:** show it only when `canSend == true` from `GET .../bookings/{jobId}/messages`. Don't work it out
  from `jobStatus` in the app.
- **"Chat" button on Booking Details:** show it when `jobStatus` is one of `CONFIRMED`, `IN_PROGRESS`,
  `AWAITING_COMPLETION_CONFIRMATION`, `NO_SHOW_REVIEW`, `COMPLETED` or `CANCELLED`. If opening the chat returns 409,
  hide the button for that booking. This is a stop-gap until G1 ships.
- **Countdown "you can reply until …":** use `sendUntil` (UTC). For server time, read the HTTP `Date` response
  header. There is no `serverTime` field.

---

## 2. Endpoints

Customer prefix: `/api/v1/customers/me` (role `CUSTOMER`). Provider prefix: `/api/v1/providers/me` (role
`SERVICE_PROVIDER`). **Both apps use the same paths after the prefix, with identical request and response shapes.**
Every call needs `Authorization: Bearer <accessToken>`. All times are ISO-8601 UTC instants.

| Need | Method + path (after prefix) |
|---|---|
| Inbox + total unread | `GET /conversations` |
| Open a booking chat / history | `GET /bookings/{jobId}/messages` |
| Newer messages (polling, reconnect) | `GET /bookings/{jobId}/messages?after={messageId}` |
| Older messages (scroll up) | `GET /bookings/{jobId}/messages?before={messageId}` |
| Send text | `POST /bookings/{jobId}/messages` |
| Read receipt | `POST /bookings/{jobId}/messages/read` |
| Re-check availability | `GET /bookings/{jobId}/messages?after={lastId}` → `canSend`, `sendUntil` (no separate endpoint) |

`jobId` is the booking's `jobId` (Booking Details / `GET /customers/me/bookings`; for providers, the booking
returned on accept / start / complete).

### Team bookings

A team booking has **one shared conversation**: the customer plus every active provider on the booking. Each
message shows `senderRole`, `senderProviderId` and `senderName`. A provider who is removed or cancelled from the
team (`CANCELLED`, `REMOVED` or `DECLINED`) loses access immediately, history included (404). Their earlier
messages stay visible to everyone else.

### 2.1 Inbox: `GET /conversations`

Customer example:

```json
{
  "conversations": [
    {
      "jobId": 70,
      "jobStatus": "IN_PROGRESS",
      "canSend": true,
      "participants": [
        { "role": "PROVIDER", "providerId": 14, "displayName": "Tharushi Silva",
          "profilePhoto": "https://jobhub.pentarixlabs.com/api/v1/public/profile-photos/30/ab12.jpg", "you": false }
      ],
      "lastMessage": { "messageId": 102, "senderRole": "PROVIDER", "fromYou": false,
                       "preview": "I'm at the gate", "sentAt": "2026-11-21T04:45:02.118Z" },
      "unreadCount": 1
    }
  ],
  "totalUnread": 1
}
```

Provider example (same shape; the other side is the customer):

```json
{
  "conversations": [
    {
      "jobId": 70,
      "jobStatus": "IN_PROGRESS",
      "canSend": true,
      "participants": [
        { "role": "CUSTOMER", "providerId": null, "displayName": "Madu Perera", "profilePhoto": null, "you": false }
      ],
      "lastMessage": { "messageId": 102, "senderRole": "PROVIDER", "fromYou": true,
                       "preview": "I'm at the gate", "sentAt": "2026-11-21T04:45:02.118Z" },
      "unreadCount": 0
    }
  ],
  "totalUnread": 0
}
```

- `participants` excludes you. `lastMessage` is `null` for an open chat with no messages yet.
- Sorted by latest activity. `preview` is cut at 100 characters with "…".
- **Badge:** `totalUnread`. **Row badge:** `unreadCount`.
- It considers only the 50 most recently updated bookings (gap G6). There is no paging.

### 2.2 Thread: `GET /bookings/{jobId}/messages`

| Query | Returns |
|---|---|
| *(none)* | Latest 50 messages. Use when opening the chat. |
| `after=ID` | Up to 50 messages with `messageId > ID`. Use for polling and catching up. |
| `before=ID` | Up to 50 messages with `messageId < ID`. Use when scrolling up. |

If both `after` and `before` are sent, `after` wins. `messages` are always **oldest first**.

```json
{
  "jobId": 70,
  "jobStatus": "IN_PROGRESS",
  "canSend": true,
  "sendUntil": null,
  "participants": [
    { "role": "CUSTOMER", "providerId": null, "displayName": "Madu Perera", "profilePhoto": null, "you": true },
    { "role": "PROVIDER", "providerId": 14, "displayName": "Tharushi Silva",
      "profilePhoto": "https://jobhub.pentarixlabs.com/api/v1/public/profile-photos/30/ab12.jpg", "you": false }
  ],
  "messages": [
    { "messageId": 101, "senderRole": "CUSTOMER", "senderProviderId": null, "senderName": "Madu Perera",
      "fromYou": true, "text": "Please call [hidden] at the gate", "contactInfoHidden": true,
      "sentAt": "2026-11-21T04:44:40.502Z", "clientMessageId": "c-7f3a9e21-0001" },
    { "messageId": 102, "senderRole": "PROVIDER", "senderProviderId": 14, "senderName": "Tharushi Silva",
      "fromYou": false, "text": "I'm at the gate", "contactInfoHidden": false,
      "sentAt": "2026-11-21T04:45:02.118Z", "clientMessageId": null }
  ],
  "hasOlder": false,
  "hasNewer": false,
  "yourLastReadMessageId": 101,
  "othersLastReadMessageId": 101,
  "unreadCount": 1
}
```

After the booking ends (customer confirmed completion):

```json
{ "jobId": 70, "jobStatus": "COMPLETED", "canSend": true, "sendUntil": "2026-11-22T07:20:11Z", "...": "..." }
```

After `sendUntil` the same call returns `"canSend": false`, and the history is still returned.

- `text` is `null` only if a stored message can't be decrypted. Show "Message unavailable".
- `clientMessageId` is returned only on **your own** messages.
- Pagination: keep loading `before=<oldest id>` while `hasOlder` is true, and `after=<newest id>` while `hasNewer`
  is true.

### 2.3 Send: `POST /bookings/{jobId}/messages` → `201`

```json
{ "text": "Please call 0771234567 at the gate", "clientMessageId": "c-7f3a9e21-0001" }
```

The response is one message object, with the text masked as it was stored:

```json
{ "messageId": 101, "senderRole": "CUSTOMER", "senderProviderId": null, "senderName": "Madu Perera",
  "fromYou": true, "text": "Please call [hidden] at the gate", "contactInfoHidden": true,
  "sentAt": "2026-11-21T04:44:40.502Z", "clientMessageId": "c-7f3a9e21-0001" }
```

Replace the local bubble's text with the returned `text`, because masking may have changed it.

### 2.4 Read receipt: `POST /bookings/{jobId}/messages/read`

```json
{ "lastReadMessageId": 102 }
```

Response:

```json
{ "jobId": 70, "yourLastReadMessageId": 102, "unreadCount": 0 }
```

- Send the newest `messageId` the user actually saw. Read state only moves forward; an older id is ignored.
- Your own messages count as read automatically.
- **Knowing the other side read yours:** `othersLastReadMessageId` in the thread response. Mark your messages with
  `messageId <= othersLastReadMessageId` as "Seen". In a team booking this is the newest message read by **any**
  other participant; there's no per-person receipt.

---

## 3. Sending and message states

### Content rules

- Plain text, 1–1000 characters, counted in UTF-16 units before cleaning, so an emoji can count as 2. Over the
  limit or empty returns 400.
- **Render as plain text only, never as HTML, Markdown or auto-linked text.**
- Before saving, the server:
  - removes control and bidi-override characters;
  - collapses 3 or more blank lines to one blank line;
  - trims surrounding whitespace.
  - Zero-width joiners are kept, so Sinhala conjuncts and emoji stay intact.
- **Masking:** email addresses (including `name (at) mail.com` and `[at]` forms) and phone numbers (9–15 digits,
  optional `+`, spaces/dots/dashes/brackets between digits) become `[hidden]`, and `contactInfoHidden: true` is
  set. Dates such as `2026-11-21` and `21/11/2026` are not masked. Show a hint like "Contact details are hidden
  for your safety".
- If the text is empty after cleaning, the server returns 400 `Message text is required`.

### Idempotency and duplicates

- `clientMessageId` is optional, but **send it on every message**: 8–64 characters from `A–Z a–z 0–9 - _`
  (a UUID works).
- Resending with the same `clientMessageId` returns the **original** message (`201`, same `messageId`) and never
  creates a duplicate. This works even if the chat closed in between, and it isn't rate limited.
- The same `clientMessageId` with different text still returns the original. The id identifies the message, not
  the text.
- The same `clientMessageId` used in a different booking's chat returns 409.

### Timeout reconciliation

1. Keep the unsent bubble with its `clientMessageId` and state `sending`.
2. On a timeout or network error, retry the same POST with the **same** `clientMessageId` (for example after
   2 s, 5 s, 10 s).
3. Or call `GET ...?after=<last confirmed id>` and look for a message with your `clientMessageId`. If it's there,
   the message was sent.
4. After the retries fail, show "Not sent – tap to retry". Tapping retries with the same id.

### States the backend supports

| State | Supported? | How |
|---|---|---|
| sending / failed | client-side only | — |
| sent | ✅ | `201` response; the message has a `messageId` |
| delivered | ❌ | not tracked |
| read / seen | ✅ | `othersLastReadMessageId` (only the newest message read, see 2.4) |

### Rate limits

| Limit | Response |
|---|---|
| 1 message per second per user | 429, `retryAfterSeconds: 1`, `Retry-After: 1` header |
| 30 messages per minute per user (across all chats) | 429, `retryAfterSeconds: 60`, `Retry-After: 60` header |

On a 429, keep the bubble, wait `retryAfterSeconds`, then retry with the same `clientMessageId`.

### Not supported in v1

Attachments/images/voice, edit, delete/unsend, reactions, replies/quotes, typing indicator, online/last-seen
status, voice/video calls, and blocking or reporting a user. Hide these in the UI.

---

## 4. Live updates and notifications

- **No WebSocket and no SSE.** Use polling:
  - **Chat screen open:** `GET ...messages?after=<newest id>` every **3–5 s**. Stop while the app is in the
    background. After showing new messages, call `.../read`.
  - **Inbox screen open:** `GET /conversations` when the screen opens, when a chat push arrives, when the app
    returns to the foreground, and at most every 30 s while it's visible.
  - **Global badge:** `totalUnread` from `GET /conversations` on app start, on foreground and on a chat push.
- **Push (FCM):** recipients get a push with this `data`:

  ```json
  { "type": "CHAT_MESSAGE_FROM_CUSTOMER", "jobId": "70", "requestId": "501", "eventId": "9123" }
  ```

  `type` is `CHAT_MESSAGE_FROM_CUSTOMER` (provider app receives) or `CHAT_MESSAGE_FROM_PROVIDER` (customer app
  receives). The visible text is "New message from your customer/provider – Open the app to read it." It never
  includes the message. The notification tag is `chat-<requestId>`.
  - **On tap:** open the chat for `jobId`.
  - At most one push per chat per recipient every 30 s, so a push only tells the app to fetch. **Always
    refetch; never build the message from the push.**
  - Pushes and events are created only for bookings made through booking requests (`requestId` exists). Older
    direct bookings get no chat push (gap G5).
  - On the production server we've seen push events stay `PENDING`, so FCM may not be configured there yet.
    Don't rely on push for correctness.
- **Events feed:** the same chat events also appear in `GET /api/v1/customers/me/booking-events?after=<eventId>`
  and `GET /api/v1/providers/me/booking-events?after=<eventId>` (oldest first, up to 100 per call, with
  `eventType`, `jobId`, `requestId`, `eventId`).
- **Duplicate or out-of-order events:** de-duplicate messages by `messageId` and order by `messageId` (it only
  increases), not by `sentAt` or arrival time. De-duplicate events by `eventId`. A push may arrive after you
  already fetched the message, which is harmless.
- **Back from background/offline:** for an open chat, loop `GET ...?after=<newest id>` until `hasNewer` is false.
  If you've been away a long time, `GET` without parameters gets the latest 50 and you can page back with
  `before`. Then refresh `/conversations` for badges.

---

## 5. Errors and access

All errors are `application/problem+json`. **There is no machine-readable `code` on chat errors yet** (gap G2),
so branch on HTTP status, and for 409 on the `detail` text listed here.

| Status | Exact meaning for chat |
|---|---|
| 400 | Bad input: empty text, over 1000 characters, invalid `clientMessageId`, `lastReadMessageId` not in this chat |
| 401 | No token, expired or invalid token, **or the account is not `ACTIVE`** (suspended or deactivated) |
| 403 | Valid token but wrong app role (for example a provider token on a `/customers/me` path) |
| 404 | `Booking not found`: the booking doesn't exist, isn't yours, you aren't an active provider on it (removed provider), or you have no customer/provider profile |
| 409 | Chat not open, chat closed for new messages, or `clientMessageId` reused in another chat |
| 429 | Rate limit; wait `retryAfterSeconds` |

Live bodies:

```json
// 401
{ "timestamp": "2026-11-21T18:02:05.349+00:00", "status": 401, "error": "Unauthorized",
  "path": "/api/v1/customers/me/conversations" }
```

```json
// 403
{ "timestamp": "2026-11-21T18:02:06.214+00:00", "status": 403, "error": "Forbidden",
  "path": "/api/v1/customers/me/conversations" }
```

```json
// 404: not owned / removed provider / doesn't exist
{ "type": "about:blank", "title": "Resource not found", "status": 404, "detail": "Booking not found",
  "instance": "/api/v1/providers/me/bookings/70/messages" }
```

```json
// 409: not open yet (unpaid, expired, cancelled before use)
{ "type": "about:blank", "title": "Resource conflict", "status": 409,
  "detail": "Chat opens once the booking is confirmed", "instance": "/api/v1/customers/me/bookings/70/messages" }
```

```json
// 409: send after sendUntil (window closed); reading still works
{ "type": "about:blank", "title": "Resource conflict", "status": 409,
  "detail": "This chat is closed for new messages", "instance": "/api/v1/customers/me/bookings/70/messages" }
```

```json
// 409
{ "type": "about:blank", "title": "Resource conflict", "status": 409,
  "detail": "clientMessageId was already used for another chat", "instance": "/api/v1/customers/me/bookings/71/messages" }
```

```json
// 429
{ "type": "about:blank", "title": "Too many requests", "status": 429,
  "detail": "Please wait a moment before sending another message", "retryAfterSeconds": 1,
  "instance": "/api/v1/customers/me/bookings/70/messages" }
```

```json
// 400
{ "type": "about:blank", "title": "Validation failed", "status": 400,
  "detail": "The request contains invalid fields", "errors": { "text": "Message must not exceed 1000 characters" } }
```

Cancelled bookings don't have their own error. They follow the table in section 1: 24 h of sending if the chat
was used, otherwise 409 `Chat opens once the booking is confirmed`.

### Retention

Messages are kept with no automatic deletion today. They're encrypted at rest (AES-256-GCM). Admins can read a
chat only through an audited read-only endpoint, for disputes. The inbox shows only the 50 most recently updated
bookings, but older chats still open by `jobId`. A retention period (for example 12 months) isn't defined yet.
That's a product/legal decision.

---

## 6. UI rules (summary)

| Element | Rule |
|---|---|
| Chat button on Booking Details | Status in {`CONFIRMED`, `IN_PROGRESS`, `AWAITING_COMPLETION_CONFIRMATION`, `NO_SHOW_REVIEW`, `COMPLETED`, `CANCELLED`}; hide it if the thread returns 409 |
| Message box | `canSend == true` |
| Read-only banner | `canSend == false` and the thread loaded: "This chat is closed. You can still read past messages." |
| "Reply until" hint | `sendUntil != null && canSend` |
| Badges | `totalUnread` (global), `unreadCount` (row) |
| Seen tick | own message with `messageId <= othersLastReadMessageId` |
| Masking hint | `contactInfoHidden == true` |
| Team header | list all `participants`, and show `senderName` on every bubble |

---

## 7. Frontend test scenarios

| # | Setup | Expected |
|---|---|---|
| 1 | Booking `AWAITING_PAYMENT` (unpaid) | Thread GET → 409 "Chat opens once the booking is confirmed"; button hidden |
| 2 | Confirm payment/wallet | Thread GET → 200, `canSend: true`, `messages: []`; inbox lists it with `lastMessage: null` |
| 3 | Customer sends "hi" with a `clientMessageId` | 201; provider inbox `unreadCount: 1`; provider push `CHAT_MESSAGE_FROM_CUSTOMER` (if FCM works) |
| 4 | Provider opens the chat and calls read | Customer thread `othersLastReadMessageId` ≥ that id → "Seen" |
| 5 | Send `call me 077 123 4567 or a@b.com` | Stored as `call me [hidden] or [hidden]`, `contactInfoHidden: true` |
| 6 | Send `2026-11-21` | Not masked |
| 7 | Same POST twice with the same `clientMessageId` (simulated timeout) | Same `messageId` both times; one bubble |
| 8 | Two sends within 1 s | Second → 429, `retryAfterSeconds: 1`; retry succeeds |
| 9 | 1001-character text / whitespace only | 400 |
| 10 | Provider A tries provider B's booking chat | 404 `Booking not found` |
| 11 | Provider token on `/customers/me/conversations` | 403 |
| 12 | Customer confirms completion | `jobStatus: COMPLETED`, `sendUntil` ≈ confirm time + 24 h, `canSend: true` |
| 13 | After `sendUntil` | Thread GET 200 with `canSend: false`; POST → 409 "closed for new messages"; message box hidden |
| 14 | Cancel a confirmed booking whose chat has messages | Can send for 24 h, then read-only |
| 15 | Cancel a confirmed booking with no messages | Thread → 409; not in the inbox |
| 16 | Team booking, provider removed | Removed provider → 404; others still see that provider's old messages |
| 17 | 60 messages in a chat | Open → 50, `hasOlder: true`; `before=<oldest>` → 10, `hasOlder: false` |
| 18 | App offline for a while, 3 messages arrive | On return `after=<newest>` gets 3; badges refresh |
| 19 | Retry the same `clientMessageId` after the chat closed | Returns the original message (no 409) |
| 20 | Customer account deactivated | All chat calls → 401 |

---

## 8. Gaps (not in the deployed API)

| # | Gap | Proposal |
|---|---|---|
| G1 | No authoritative availability fields; none on booking responses | Add `chat: { canRead, canSend, opensAt, sendUntil, unavailableReason, serverTime }` to the thread, the inbox rows and the booking/provider responses. `unavailableReason`: `NOT_CONFIRMED`, `NOT_OPEN_YET`, `CLOSED`, `CANCELLED_UNUSED`, `NOT_PARTICIPANT` |
| G2 | No machine-readable error `code` | Add `code` to errors: `CHAT_NOT_OPEN`, `CHAT_CLOSED`, `CLIENT_MESSAGE_ID_REUSED`, `CHAT_RATE_LIMITED` |
| G3 | The 24 h window is based on the booking's last update, so a later admin/settlement update could extend it | Base it on `completionConfirmedAt` (completed) or the cancellation time |
| G4 | The apps expect "opens 30 min before scheduled start" | **Product decision.** Today it opens at confirmation. If 30 min is wanted, also decide whether provider "on the way" messages before that are blocked |
| G5 | No chat push/events for older direct bookings (no `requestId`) | Create events by `jobId` alone |
| G6 | Inbox capped at 50 bookings, no paging | Add `?before=` paging |
| G7 | No per-participant read receipts in team chats | Add `readers: [{role, providerId, lastReadMessageId}]` if needed |
| G8 | No retention period | Decide one (for example 12 months after the booking ends) and add a cleanup job |
