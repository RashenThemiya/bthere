# Booking chat API (v1)

Text chat between a booking's customer and its provider(s). A team booking has one shared chat containing the
customer and every active provider on the booking. Version 1 is text only: no images, voice or calls.

Frontend integration guide (availability, retries, polling, errors, test scenarios): [CHAT_INTEGRATION.md](CHAT_INTEGRATION.md).

All endpoints need `Authorization: Bearer <accessToken>`. Times are ISO-8601 UTC instants.

## When chat is available

| Booking status | Read messages | Send messages |
|---|---|---|
| `PENDING`, `AWAITING_PAYMENT`, other pre-confirmation statuses | ❌ 409 | ❌ 409 |
| `CONFIRMED`, `IN_PROGRESS`, `AWAITING_COMPLETION_CONFIRMATION`, `NO_SHOW_REVIEW` | ✅ | ✅ |
| `COMPLETED` | ✅ | ✅ for 24 hours after it ended (`sendUntil`), then read-only |
| `CANCELLED` | ✅ if the chat was used | ✅ for 24 hours, only if the chat had messages before cancellation |

Use `canSend` from the API to show or hide the message box; don't work it out from the status in the app.

## Who can access a chat

- **Customer:** only bookings that belong to them.
- **Provider:** only bookings where they are the primary provider or an active team member (not `CANCELLED`,
  `REMOVED` or `DECLINED`).
- Anyone else gets **404 `Booking not found`**, so other bookings can't be discovered.
- Admins (`SUPER_ADMIN`) have a read-only view. Every admin read is written to the audit log.

## Message rules

- Plain text, 1–1000 characters. **Always show the text as plain text, never as HTML or Markdown.**
- Phone numbers and email addresses are replaced with `[hidden]` before saving, and `contactInfoHidden: true`
  is set. Show a small note such as "Contact details are hidden for your safety."
- Rate limits: one message per second and 30 per minute per user. Over the limit returns **429**, with
  `retryAfterSeconds` in the body.
- Messages are encrypted in the database (AES-256-GCM) and sent over HTTPS.
- Push notifications never include the message text.

---

## Endpoints

Customer paths start with `/api/v1/customers/me`; provider paths start with `/api/v1/providers/me`. Both have
the same request and response shapes.

| Method | Path | Purpose |
|---|---|---|
| GET | `/conversations` | Inbox: chats with messages or that are currently open |
| GET | `/bookings/{jobId}/messages` | Open a chat (latest 50 messages) or page through it |
| POST | `/bookings/{jobId}/messages` | Send a message |
| POST | `/bookings/{jobId}/messages/read` | Mark messages as read |
| GET | `/api/v1/admin/bookings/{jobId}/messages?after=` | Admin, read-only, audited |

### GET `/conversations`

```json
{
  "conversations": [
    {
      "jobId": 40,
      "jobStatus": "CONFIRMED",
      "canSend": true,
      "participants": [
        { "role": "PROVIDER", "providerId": 14, "displayName": "Tharushi Silva",
          "profilePhoto": "https://jobhub.pentarixlabs.com/api/v1/public/profile-photos/30/....jpg", "you": false }
      ],
      "lastMessage": { "messageId": 102, "senderRole": "PROVIDER", "fromYou": false,
                       "preview": "I'm at the gate", "sentAt": "2026-11-18T10:15:02Z" },
      "unreadCount": 1
    }
  ],
  "totalUnread": 1
}
```

- Lists up to 50 recent bookings, most recent activity first.
- `participants` excludes you. `lastMessage` is `null` for an open chat with no messages yet.
- Use `totalUnread` for a badge.

### GET `/bookings/{jobId}/messages`

| Query | Meaning |
|---|---|
| *(none)* | Latest 50 messages. Use this when opening the chat. |
| `after={messageId}` | Up to 50 messages newer than `messageId`. Poll with the last id you have. |
| `before={messageId}` | Up to 50 messages older than `messageId`. Use this when the user scrolls up. |

If both `after` and `before` are sent, `after` is used. Messages always come oldest first.

```json
{
  "jobId": 40,
  "jobStatus": "CONFIRMED",
  "canSend": true,
  "sendUntil": null,
  "participants": [
    { "role": "CUSTOMER", "providerId": null, "displayName": "Madu Perera", "profilePhoto": null, "you": true },
    { "role": "PROVIDER", "providerId": 14, "displayName": "Tharushi Silva", "profilePhoto": "https://...", "you": false }
  ],
  "messages": [
    { "messageId": 101, "senderRole": "CUSTOMER", "senderProviderId": null, "senderName": "Madu Perera",
      "fromYou": true, "text": "Please call [hidden] at the gate", "contactInfoHidden": true,
      "sentAt": "2026-11-18T10:14:40Z", "clientMessageId": "a1b2c3d4-0001" },
    { "messageId": 102, "senderRole": "PROVIDER", "senderProviderId": 14, "senderName": "Tharushi Silva",
      "fromYou": false, "text": "I'm at the gate", "contactInfoHidden": false,
      "sentAt": "2026-11-18T10:15:02Z", "clientMessageId": null }
  ],
  "hasOlder": false,
  "hasNewer": false,
  "yourLastReadMessageId": 101,
  "othersLastReadMessageId": 101,
  "unreadCount": 1
}
```

- `othersLastReadMessageId` is the newest message any other participant has read. Mark your messages with ids
  up to it as "Seen".
- `sendUntil` is set once the booking has ended. After that time `canSend` is `false`.
- `text` is `null` only if a stored message can no longer be decrypted. Show "Message unavailable".
- `clientMessageId` is returned only on your own messages.

### POST `/bookings/{jobId}/messages` → 201

```json
{ "text": "Please call 0771234567 at the gate", "clientMessageId": "a1b2c3d4-0001" }
```

- `clientMessageId` is optional but recommended: 8–64 characters from `A–Z a–z 0–9 - _`, for example a UUID.
- If the app retries after a timeout with the same id, the server returns the original message instead of
  creating a duplicate.
- The response is one message object (same shape as in `messages` above), with the masked `text` as stored.

### POST `/bookings/{jobId}/messages/read`

```json
{ "lastReadMessageId": 102 }
```

Response: `{ "jobId": 40, "yourLastReadMessageId": 102, "unreadCount": 0 }`.

- Read state only moves forward; an older id is ignored.
- The id must belong to this chat, otherwise **400**.
- Your own sent messages count as read automatically.

## Errors

| Status | When |
|---|---|
| 400 | Empty text, text over 1000 characters, invalid `clientMessageId`, or `lastReadMessageId` from another chat |
| 401 / 403 | Not logged in, or wrong role for the path |
| 404 | Booking doesn't exist or isn't yours |
| 409 | Chat not open yet (`Chat opens once the booking is confirmed`), chat closed for new messages, or `clientMessageId` reused in another chat |
| 429 | Rate limit reached; wait `retryAfterSeconds` |

## Push notifications and live updates

- When a message arrives, the other participants get an FCM push. The `data` payload is:
  ```
  type      CHAT_MESSAGE_FROM_CUSTOMER | CHAT_MESSAGE_FROM_PROVIDER
  jobId     the booking to open
  requestId the booking request
  eventId   the event id
  ```
  The visible notification says "New message from your provider/customer – Open the app to read it.", never
  the message text. Chat notifications use the tag `chat-<requestId>`, so they don't replace booking updates.
- At most one push per chat per recipient every 30 seconds.
- The same events also appear in the existing booking events feed.
- **While a chat is open:** poll `GET .../messages?after=<last messageId>` every 3–5 seconds, then call
  `.../messages/read` with the newest id you displayed.
- **On the inbox screen:** refresh `GET /conversations` when a chat push arrives or the screen opens.

## Server configuration

- Nothing new is required. The chat key is derived from `BOOKING_VERIFICATION_KEY`.
- To use a separate key, set `CHAT_ENCRYPTION_KEY` (32-byte base64, e.g. `openssl rand -base64 32`) **before**
  the first message is sent. Changing either key later makes existing messages unreadable.
- Tables `chat_message` and `chat_read_state`, and the `booking_dispatch_event.job_id` column, are created by
  Hibernate on startup. See [CHAT_SCHEMA.sql](CHAT_SCHEMA.sql) for manual setups.
