# Booking chat v1.1: delivery ticks and file attachments

Adds to [CHAT_INTEGRATION.md](CHAT_INTEGRATION.md). Same prefixes and auth: customer `/api/v1/customers/me`,
provider `/api/v1/providers/me`. Paths below are written after the prefix.

> **Status:** implemented and tested (597 tests passing). **Not deployed yet.**

---

## 1. Sent → Delivered → Seen

### How it works

| Tick | Meaning | Source |
|---|---|---|
| grey ✓ `SENT` | Saved on the server | `deliveryStatus` |
| grey ✓✓ `DELIVERED` | **Every** other participant's app has received it (acked) | `deliveryStatus` |
| blue ✓✓ `SEEN` | **Every** other participant has read it | `deliveryStatus` |
| no tick / ✓ `UNKNOWN` | Sent before delivery tracking existed and not yet seen | `deliveryStatus` |

- **Use `deliveryStatus` on each message.** The server works it out, so the app doesn't need to compare cursors.
  It is set only on **your own** messages and is `null` on other people's.
- **A push is never counted as delivered.** A message is delivered only when the recipient app calls the ack
  endpoint.
- **Reading counts as receiving.** `.../messages/read` also moves the delivered cursor.
- **Cursors are on the server and only move forward.** Duplicate or old acks change nothing. After an app restart,
  the thread GET returns the same statuses.
- **Team chat:** delivered or seen means **all** other current participants have it. A provider who has never
  opened the app keeps your message at `SENT`. A newly added provider starts with no position, so your earlier
  messages can drop back to `SENT` until they catch up.
- **Old messages:** messages sent before this release have `deliveryTracked = null` in the database. They show
  `UNKNOWN` until someone's delivered cursor passes them, or `SEEN` once they're read.

### Ack: `POST /bookings/{jobId}/messages/delivered`

Call this when the recipient app has the message locally: after a thread or poll response shows new messages from
others, or after the inbox shows `unreadCount > 0` for a chat (use that row's `lastMessage.messageId`). Send the
**newest** `messageId` you have. Everything older counts as received too.

```json
{ "lastDeliveredMessageId": 142 }
```

`200`

```json
{ "jobId": 70, "yourLastDeliveredMessageId": 142, "yourLastReadMessageId": 139 }
```

| Error | When |
|---|---|
| 400 `MESSAGE_NOT_IN_CHAT` | That id isn't a message in this booking's chat |
| 400 validation | Missing, zero or negative id |
| 404 `BOOKING_NOT_FOUND` | Not your booking / not an active provider |
| 409 `CHAT_NOT_OPEN` | The chat was never open |

Acks still work after the chat becomes read-only.

### New fields in `GET /bookings/{jobId}/messages`

```json
{
  "jobId": 70,
  "jobStatus": "IN_PROGRESS",
  "canSend": true,
  "sendUntil": null,
  "participants": [ "…" ],
  "messages": [
    { "messageId": 139, "fromYou": true, "text": "Gate code is [hidden]", "deliveryStatus": "SEEN",
      "attachments": [], "…": "…" },
    { "messageId": 141, "fromYou": true, "text": "I'm outside", "deliveryStatus": "DELIVERED",
      "attachments": [], "…": "…" },
    { "messageId": 142, "fromYou": true, "text": "", "deliveryStatus": "SENT",
      "attachments": [ { "attachmentId": 12, "kind": "IMAGE", "…": "…" } ], "…": "…" },
    { "messageId": 143, "fromYou": false, "text": "Coming", "deliveryStatus": null, "attachments": [], "…": "…" }
  ],
  "hasOlder": false,
  "hasNewer": false,
  "yourLastReadMessageId": 143,
  "othersLastReadMessageId": 139,
  "unreadCount": 0,
  "othersLastDeliveredMessageId": 141,
  "receipts": [
    { "role": "PROVIDER", "providerId": 14, "displayName": "Tharushi Silva",
      "lastDeliveredMessageId": 141, "lastReadMessageId": 139 }
  ]
}
```

| Field | Meaning |
|---|---|
| `messages[].deliveryStatus` | `SENT` \| `DELIVERED` \| `SEEN` \| `UNKNOWN` on your messages; `null` on others' |
| `othersLastDeliveredMessageId` | Newest message **every** other participant has received (0 if none) |
| `othersLastReadMessageId` | Unchanged from v1: newest message **any** other participant has read. For ticks use `deliveryStatus` |
| `receipts[]` | One row per other participant: `lastDeliveredMessageId`, `lastReadMessageId`. Use for "Seen by Tharushi, Nethmi" in team chats |

`POST /bookings/{jobId}/messages` returns the new message with `"deliveryStatus": "SENT"`.

---

## 2. Attachments (images and PDFs)

### Rules

| Rule | Value |
|---|---|
| Types | JPG, PNG, WebP images; PDF documents. The type is read from the file's bytes, so a renamed `.exe` is rejected. HEIC/GIF/Word aren't accepted: convert HEIC to JPG on the phone |
| Size | 10 MB per file |
| Per message | Up to 5 attachments |
| Message content | Text only, files only, or text + files. File-only messages have `"text": ""` |
| Upload rate | 20 uploads per user per 10 minutes |
| Unsent limit | 10 unsent files per user per chat |
| Unsent files | Deleted automatically 24 h after upload (`deleteIfUnsentAt`) |
| Storage | Private S3 bucket, encrypted at rest by S3. Never public |
| Links | Signed URLs valid for **15 minutes** (`urlExpiresAt`). They work without auth headers, so image loaders can use them directly |
| Who can see | Sent files: every current participant of that booking chat. Unsent files: only the uploader |
| Read-only chat | Sent files stay viewable. New uploads are blocked (409 `CHAT_CLOSED`) |
| File name | Path removed, unsafe characters removed, phone numbers/emails masked, at most 120 characters, extension set to the real type |
| Images | Width and height returned. No server thumbnail: use the image URL with client-side resizing |
| Privacy | **Before uploading, compress/re-encode photos in the app so GPS EXIF data is removed.** The server stores files unchanged. PDF contents aren't scanned or masked |

### Flow

```
1. POST /bookings/{jobId}/attachments  (multipart, one file)   → attachmentId   [repeat per file, max 5]
2. POST /bookings/{jobId}/messages  { text?, clientMessageId, attachmentIds: [...] }  → message with attachments
   (cancel before sending → DELETE /bookings/{jobId}/attachments/{attachmentId})
3. Show attachments[].url; when it expires → GET /bookings/{jobId}/attachments/{attachmentId} or refetch the thread
```

### Upload: `POST /bookings/{jobId}/attachments` → `201`

`Content-Type: multipart/form-data`

| Part / param | Required | Meaning |
|---|---|---|
| `file` | yes | The file |
| `clientUploadId` | recommended | 8–64 characters `A–Z a–z 0–9 - _`. A retry with the same id returns the same attachment; nothing is uploaded twice |

```bash
curl -X POST "$BASE/api/v1/customers/me/bookings/70/attachments" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@gate.jpg" -F "clientUploadId=u-3f9a1c2e-0001"
```

```json
{
  "attachmentId": 12,
  "kind": "IMAGE",
  "fileName": "gate.jpg",
  "mimeType": "image/jpeg",
  "sizeBytes": 482113,
  "width": 1600,
  "height": 1200,
  "url": "https://jobhube.s3.eu-north-1.amazonaws.com/chat/70/7c1e…jpg?X-Amz-Algorithm=…&X-Amz-Expires=900&X-Amz-Signature=…",
  "urlExpiresAt": "2026-11-22T10:15:00Z",
  "messageId": null,
  "uploadedAt": "2026-11-22T10:00:00Z",
  "deleteIfUnsentAt": "2026-11-23T10:00:00Z"
}
```

PDF example: `"kind": "DOCUMENT"`, `"mimeType": "application/pdf"`, `"width": null`, `"height": null`. The
URL downloads the file with its `fileName`. Image URLs open inline.

### Send with attachments: `POST /bookings/{jobId}/messages` → `201`

```json
{ "text": "This is the gate", "clientMessageId": "c-3f9a1c2e-0002", "attachmentIds": [12, 13] }
```

A file-only message omits `text` or sends `""`.

```json
{
  "messageId": 142,
  "senderRole": "CUSTOMER",
  "senderProviderId": null,
  "senderName": "Madu Perera",
  "fromYou": true,
  "text": "This is the gate",
  "contactInfoHidden": false,
  "sentAt": "2026-11-22T10:00:05.211Z",
  "clientMessageId": "c-3f9a1c2e-0002",
  "attachments": [
    { "attachmentId": 12, "kind": "IMAGE", "fileName": "gate.jpg", "mimeType": "image/jpeg", "sizeBytes": 482113,
      "width": 1600, "height": 1200, "url": "https://…", "urlExpiresAt": "2026-11-22T10:15:05Z",
      "messageId": 142, "uploadedAt": "2026-11-22T10:00:00Z", "deleteIfUnsentAt": null },
    { "attachmentId": 13, "kind": "DOCUMENT", "fileName": "Booking notes.pdf", "mimeType": "application/pdf",
      "sizeBytes": 91822, "width": null, "height": null, "url": "https://…", "urlExpiresAt": "2026-11-22T10:15:05Z",
      "messageId": 142, "uploadedAt": "2026-11-22T10:00:01Z", "deleteIfUnsentAt": null }
  ],
  "deliveryStatus": "SENT"
}
```

- Attachments come back in the order of `attachmentIds`.
- Only your own unsent uploads **for this booking** can be attached.
- **Retrying the send** with the same `clientMessageId` returns the original message with its attachments. No
  second message, no files moved.

### Refresh a link / view one file: `GET /bookings/{jobId}/attachments/{attachmentId}` → `200`

Returns the same `Attachment` object with a new `url` and `urlExpiresAt`. Call it when an image fails to load
(403 from S3) or `urlExpiresAt` has passed. Refetching the thread also returns new URLs.

### Cancel an unsent upload: `DELETE /bookings/{jobId}/attachments/{attachmentId}` → `204`

Only for your own unsent files. After a send, files can't be removed (409 `ATTACHMENT_ALREADY_SENT`). A 404
on retry means it's already gone, so treat it as done.

### Inbox preview

`lastMessage.preview` is `null` for a file-only message, and `lastMessage.attachmentCount` gives the number of
files. Show "📷 Photo" / "📄 Document" / "2 files".

### Failure handling

| Case | What to do |
|---|---|
| Upload fails or times out | Retry with the same `clientUploadId`. You get the same `attachmentId`, never a duplicate |
| User cancels before sending | `DELETE` the attachment. If the app forgets, it's deleted after 24 h |
| Upload OK, send fails | Retry the send with the same `clientMessageId` and the same `attachmentIds` |
| Send 400 `ATTACHMENT_NOT_FOUND` | The upload was deleted (24 h passed, or cancelled). Upload again |
| Send 409 `ATTACHMENT_ALREADY_SENT` | That file is already in another message. Refresh the thread |
| Image URL returns 403 | The link expired. `GET .../attachments/{id}` for a new one |

---

## 3. Error codes (all chat endpoints)

Every chat error body now also has a `code`:

```json
{ "type": "about:blank", "title": "Resource conflict", "status": 409,
  "detail": "This chat is closed for new messages", "code": "CHAT_CLOSED",
  "instance": "/api/v1/customers/me/bookings/70/messages" }
```

| HTTP | `code` | Meaning |
|---|---|---|
| 400 | `MESSAGE_EMPTY` | No text and no attachments |
| 400 | `MESSAGE_NOT_IN_CHAT` | Read/delivered id is not in this chat |
| 400 | `TOO_MANY_ATTACHMENTS` | More than 5 in one message (also returned as a validation error on `attachmentIds`) |
| 400 | `ATTACHMENT_NOT_FOUND` | An `attachmentIds` entry isn't your unsent upload in this chat |
| 400 | `FILE_EMPTY` / `FILE_UNREADABLE` | No file part, or it couldn't be read |
| 400 | `INVALID_CLIENT_UPLOAD_ID` | Bad `clientUploadId` format |
| 404 | `BOOKING_NOT_FOUND` | Not your booking, not an active provider, or it doesn't exist |
| 404 | `ATTACHMENT_NOT_FOUND` | No such file in this chat, or it's someone else's unsent upload |
| 409 | `CHAT_NOT_OPEN` | The chat was never open (unpaid, expired, cancelled unused) |
| 409 | `CHAT_CLOSED` | Read-only now. Reading and viewing files still work |
| 409 | `CLIENT_MESSAGE_ID_REUSED` / `CLIENT_UPLOAD_ID_REUSED` | That id was used in another booking's chat |
| 409 | `ATTACHMENT_ALREADY_SENT` | The file is in a message already (send or delete) |
| 409 | `TOO_MANY_UNSENT_ATTACHMENTS` | 10 unsent files in this chat. Send or delete some |
| 413 | `FILE_TOO_LARGE` | Over 10 MB |
| 415 | `UNSUPPORTED_FILE_TYPE` | Not JPG/PNG/WebP/PDF by content |
| 429 | `CHAT_RATE_LIMITED` | Message rate limit (`retryAfterSeconds` 1 or 60) |
| 429 | `UPLOAD_RATE_LIMITED` | 20 uploads per 10 minutes (`retryAfterSeconds` 120) |
| 503 | `FILE_STORAGE_UNAVAILABLE` | File storage isn't configured on the server |

401 and 403 bodies are unchanged and have no `code`.

---

## 4. Team-chat permissions

| Action | Customer | Active provider on the booking | Removed/declined provider |
|---|---|---|---|
| Upload / send files | ✅ while `canSend` | ✅ while `canSend` | ❌ 404 |
| View sent files | ✅ | ✅ (also files sent before they joined) | ❌ 404 |
| View someone's unsent upload | ❌ 404 | ❌ 404 | ❌ 404 |
| Delete | own unsent only | own unsent only | ❌ |
| Ack delivered / read | ✅ | ✅ | ❌ 404 |

Admins see attachments in the audited admin thread (`GET /api/v1/admin/bookings/{jobId}/messages`).

---

## 5. Frontend test scenarios

| # | Steps | Expected |
|---|---|---|
| D1 | Customer sends; provider app closed | Customer sees `SENT` |
| D2 | Provider app polls and calls `/messages/delivered` with the newest id | Customer refresh → `DELIVERED` |
| D3 | Provider opens the chat and calls `/messages/read` | Customer → `SEEN` |
| D4 | Provider acks an older id after a newer one | `yourLastDeliveredMessageId` unchanged |
| D5 | Same ack twice | Same response; no change |
| D6 | Restart the customer app | Statuses identical after the thread GET |
| D7 | Message sent before this release, not yet read | `UNKNOWN` |
| D8 | Team booking: provider A read, provider B hasn't opened it | Customer sees `SENT`; `receipts` shows A read, B 0 |
| D9 | Ack with a message id from another booking | 400 `MESSAGE_NOT_IN_CHAT` |
| A1 | Upload a 1600×1200 JPG | 201, `kind: IMAGE`, `width: 1600`, `height: 1200`, `messageId: null` |
| A2 | Upload a PDF | 201, `kind: DOCUMENT`, `width: null` |
| A3 | Upload a `.exe` renamed `.jpg` | 415 `UNSUPPORTED_FILE_TYPE` |
| A4 | Upload 11 MB | 413 `FILE_TOO_LARGE` |
| A5 | Upload twice with the same `clientUploadId` | Same `attachmentId` |
| A6 | Send `{ attachmentIds: [id] }` without text | 201, `text: ""`, one attachment |
| A7 | Send text + 2 files | 201, attachments in the given order |
| A8 | Send 6 attachment ids | 400 |
| A9 | Retry the same send (same `clientMessageId`) | Same `messageId`, files not duplicated |
| A10 | Attach the same file to a second message | 409 `ATTACHMENT_ALREADY_SENT` |
| A11 | Provider opens the customer's unsent `attachmentId` | 404 `ATTACHMENT_NOT_FOUND` |
| A12 | Wait 16 minutes, then load the URL | S3 403; `GET .../attachments/{id}` gives a working URL |
| A13 | Delete an unsent upload | 204; sending it afterwards → 400 `ATTACHMENT_NOT_FOUND` |
| A14 | Delete a sent attachment | 409 `ATTACHMENT_ALREADY_SENT` |
| A15 | Chat read-only (after `sendUntil`) | Old file URLs still work; upload → 409 `CHAT_CLOSED` |
| A16 | Removed team provider opens a file | 404 |
| A17 | Inbox after a file-only message | `preview: null`, `attachmentCount: 1` |
| A18 | 21 uploads within 10 minutes | 21st → 429 `UPLOAD_RATE_LIMITED` |

---

## 6. Server notes

- New table `chat_attachment` and columns `chat_message.delivery_tracked` and
  `chat_read_state.last_delivered_message_id` are created by Hibernate (`ddl-auto=update`). See
  [CHAT_SCHEMA.sql](CHAT_SCHEMA.sql).
- Files go to the existing `AWS_S3_BUCKET` under `chat/{jobId}/`. The server's IAM user needs `s3:PutObject`,
  `s3:GetObject` and `s3:DeleteObject` on `chat/*`. If `DeleteObject` is missing, cancel and cleanup still remove
  the database row (nobody can get a link) but leave the object, and log a warning.
- A cleanup job runs hourly (`chat.attachment-cleanup-ms`, default 3600000).
- `spring.servlet.multipart.max-file-size` / `max-request-size` stay at 10 MB.
