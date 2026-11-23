package com.jobhub.service.chat;

import com.jobhub.dto.chat.ChatApi.*;
import com.jobhub.entity.chat.ChatAttachment;
import com.jobhub.entity.chat.ChatMessage;
import com.jobhub.entity.chat.ChatReadState;
import com.jobhub.entity.customer.Customer;
import com.jobhub.entity.job.BookingDispatchEvent;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.exception.ApiException;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.InvalidInputException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.exception.TooManyRequestsException;
import com.jobhub.repository.chat.ChatAttachmentRepository;
import com.jobhub.repository.chat.ChatMessageRepository;
import com.jobhub.repository.chat.ChatReadStateRepository;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.job.BookingDispatchEventRepository;
import com.jobhub.repository.job.BookingRequestRepository;
import com.jobhub.repository.job.JobProviderAssignmentRepository;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.service.audit.AuditService;
import com.jobhub.service.upload.ProfilePhotoUrls;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Chat between a booking's customer and its providers: text, images and PDFs, with delivery and read receipts.
 * Every call checks that the signed-in user is the booking's customer or an active provider on it (anyone else gets
 * "Booking not found"). Chat opens at confirmation and accepts new messages until 24 hours after the booking is
 * completed or cancelled; after that it is read-only, attachments included.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    static final Set<String> OPEN_STATUSES = Set.of("CONFIRMED", "IN_PROGRESS",
            "AWAITING_COMPLETION_CONFIRMATION", "NO_SHOW_REVIEW");
    static final Set<String> ENDED_STATUSES = Set.of("COMPLETED", "CANCELLED");
    static final Set<String> INACTIVE_ASSIGNMENTS = Set.of("CANCELLED", "REMOVED", "DECLINED");
    static final Duration GRACE = Duration.ofHours(24);
    static final Duration MIN_GAP = Duration.ofSeconds(1);
    static final int MAX_PER_MINUTE = 30;
    static final Duration PUSH_COALESCE = Duration.ofSeconds(30);
    static final int MAX_ATTACHMENTS_PER_MESSAGE = 5;
    static final int MAX_UPLOADS_PER_10_MINUTES = 20;
    static final int MAX_UNSENT_PER_CHAT = 10;
    static final Duration UNSENT_TTL = Duration.ofHours(24);
    static final String CUSTOMER = "CUSTOMER";
    static final String PROVIDER = "PROVIDER";
    private static final String NOT_FOUND = "Booking not found";
    private static final String NOT_OPEN = "Chat opens once the booking is confirmed";
    private static final int PREVIEW_LENGTH = 100;
    private static final java.util.regex.Pattern CLIENT_ID = java.util.regex.Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final JobRepository jobs;
    private final CustomerRepository customers;
    private final ServiceProviderRepository providers;
    private final JobProviderAssignmentRepository jobAssignments;
    private final ChatMessageRepository messages;
    private final ChatReadStateRepository reads;
    private final ChatAttachmentRepository attachments;
    private final ChatAttachmentStorage storage;
    private final BookingRequestRepository requests;
    private final BookingDispatchEventRepository events;
    private final ChatCipher cipher;
    private final ProfilePhotoUrls photoUrls;
    private final AuditService audit;

    /** The signed-in user in one booking chat. providerId is set for providers. */
    private record Viewer(String role, Long userId, Long providerId) {}

    private record Member(String role, Long userId, Long providerId, String displayName, String profilePhoto) {}

    /** Receipt positions relative to one viewer: the newest message all others received and all others read. */
    private record Receipts(long allDelivered, long allRead, List<Receipt> list) {}

    // ---------- customer ----------

    @Transactional(readOnly = true)
    public ChatThread customerThread(Long userId, Long jobId, Long after, Long before) {
        var job = customerJob(userId, jobId);
        return thread(job, customerViewer(userId), after, before);
    }

    @Transactional
    public Message customerSend(Long userId, Long jobId, SendMessage input) {
        return send(customerJob(userId, jobId), customerViewer(userId), input);
    }

    @Transactional
    public ReadState customerRead(Long userId, Long jobId, MarkRead input) {
        return markRead(customerJob(userId, jobId), userId, input.lastReadMessageId());
    }

    @Transactional
    public DeliveryState customerDelivered(Long userId, Long jobId, MarkDelivered input) {
        return markDelivered(customerJob(userId, jobId), userId, input.lastDeliveredMessageId());
    }

    @Transactional
    public Attachment customerUpload(Long userId, Long jobId, MultipartFile file, String clientUploadId) {
        return upload(customerJob(userId, jobId), customerViewer(userId), file, clientUploadId);
    }

    @Transactional(readOnly = true)
    public Attachment customerAttachment(Long userId, Long jobId, Long attachmentId) {
        return attachment(customerJob(userId, jobId), customerViewer(userId), attachmentId);
    }

    @Transactional
    public void customerDeleteAttachment(Long userId, Long jobId, Long attachmentId) {
        deleteUnsent(customerJob(userId, jobId), customerViewer(userId), attachmentId);
    }

    @Transactional(readOnly = true)
    public Conversations customerConversations(Long userId) {
        var customer = customers.findByUserId(userId).orElse(null);
        if (customer == null) return new Conversations(List.of(), 0);
        var list = jobs.findTop50ByCustomerIdAndJobStatusInOrderByUpdatedAtDesc(customer.getCustomerId(), chatStatuses());
        return conversations(list, customerViewer(userId));
    }

    // ---------- provider ----------

    @Transactional(readOnly = true)
    public ChatThread providerThread(Long userId, Long jobId, Long after, Long before) {
        var viewer = providerViewer(userId);
        return thread(providerJob(viewer, jobId), viewer, after, before);
    }

    @Transactional
    public Message providerSend(Long userId, Long jobId, SendMessage input) {
        var viewer = providerViewer(userId);
        return send(providerJob(viewer, jobId), viewer, input);
    }

    @Transactional
    public ReadState providerRead(Long userId, Long jobId, MarkRead input) {
        var viewer = providerViewer(userId);
        return markRead(providerJob(viewer, jobId), userId, input.lastReadMessageId());
    }

    @Transactional
    public DeliveryState providerDelivered(Long userId, Long jobId, MarkDelivered input) {
        var viewer = providerViewer(userId);
        return markDelivered(providerJob(viewer, jobId), userId, input.lastDeliveredMessageId());
    }

    @Transactional
    public Attachment providerUpload(Long userId, Long jobId, MultipartFile file, String clientUploadId) {
        var viewer = providerViewer(userId);
        return upload(providerJob(viewer, jobId), viewer, file, clientUploadId);
    }

    @Transactional(readOnly = true)
    public Attachment providerAttachment(Long userId, Long jobId, Long attachmentId) {
        var viewer = providerViewer(userId);
        return attachment(providerJob(viewer, jobId), viewer, attachmentId);
    }

    @Transactional
    public void providerDeleteAttachment(Long userId, Long jobId, Long attachmentId) {
        var viewer = providerViewer(userId);
        deleteUnsent(providerJob(viewer, jobId), viewer, attachmentId);
    }

    @Transactional(readOnly = true)
    public Conversations providerConversations(Long userId) {
        var viewer = providerViewer(userId);
        var list = jobs.findChatJobsForProvider(viewer.providerId(), chatStatuses(), INACTIVE_ASSIGNMENTS,
                PageRequest.of(0, 50));
        return conversations(list, viewer);
    }

    // ---------- admin ----------

    /** Read-only view for disputes. Every read is written to the audit log. */
    @Transactional
    public ChatThread adminThread(Long actorId, Long jobId, Long after) {
        var job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND, "BOOKING_NOT_FOUND"));
        long cursor = after == null ? 0 : Math.max(0, after);
        audit.record(actorId, "ADMIN_CHAT_VIEWED", "JOB", jobId, null, Map.of("after", cursor));
        var members = members(job);
        var page = messages.findTop50ByJobIdAndMessageIdGreaterThanOrderByMessageIdAsc(jobId, cursor);
        var names = names(members);
        var files = attachmentsFor(page);
        var receipts = receipts(jobId, members, null);
        var list = page.stream().map(m -> view(m, null, names, files, receipts)).toList();
        boolean hasNewer = !page.isEmpty() && messages.existsByJobIdAndMessageIdGreaterThan(jobId, last(page));
        return new ChatThread(jobId, job.getJobStatus(), false, sendUntil(job),
                members.stream().map(m -> participant(m, null)).toList(), list,
                cursor > 0 && messages.existsByJobIdAndMessageIdLessThan(jobId, cursor + 1), hasNewer, null,
                maxRead(jobId, null), 0, receipts.allDelivered(), receipts.list());
    }

    /** Deletes files that were uploaded but never sent within a day. Called by the cleanup worker. */
    @Transactional
    public int purgeUnsentAttachments() {
        var stale = attachments.findTop100ByMessageIdIsNullAndCreatedAtBeforeOrderByAttachmentIdAsc(
                Instant.now().minus(UNSENT_TTL));
        for (var a : stale) {
            removeObject(a);
            attachments.delete(a);
        }
        return stale.size();
    }

    /**
     * Deletes the stored object. A failure is logged and not rethrown: the row is removed anyway so nobody can get a
     * link to the file again, and a stuck row cannot block the cleanup queue.
     */
    private void removeObject(ChatAttachment a) {
        try {
            storage.delete(a.getStorageKey());
        } catch (RuntimeException e) {
            log.warn("Could not delete chat file object {} (attachment {}): {}", a.getStorageKey(), a.getAttachmentId(),
                    e.getMessage());
        }
    }

    // ---------- shared ----------

    private ChatThread thread(Job job, Viewer viewer, Long after, Long before) {
        boolean canSend = canSend(job);
        if (!readable(job, canSend)) throw new ConflictException(NOT_OPEN, "CHAT_NOT_OPEN");
        Long jobId = job.getJobId();
        List<ChatMessage> page;
        boolean hasOlder;
        boolean hasNewer;
        if (after != null) {
            page = messages.findTop50ByJobIdAndMessageIdGreaterThanOrderByMessageIdAsc(jobId, Math.max(0, after));
            hasNewer = !page.isEmpty() && messages.existsByJobIdAndMessageIdGreaterThan(jobId, last(page));
            hasOlder = messages.existsByJobIdAndMessageIdLessThan(jobId, page.isEmpty() ? after + 1 : page.get(0).getMessageId());
        } else {
            page = new ArrayList<>(before == null
                    ? messages.findTop50ByJobIdOrderByMessageIdDesc(jobId)
                    : messages.findTop50ByJobIdAndMessageIdLessThanOrderByMessageIdDesc(jobId, before));
            Collections.reverse(page);
            hasOlder = !page.isEmpty() && messages.existsByJobIdAndMessageIdLessThan(jobId, page.get(0).getMessageId());
            hasNewer = before != null && messages.existsByJobIdAndMessageIdGreaterThan(jobId,
                    page.isEmpty() ? before - 1 : last(page));
        }
        var members = members(job);
        var names = names(members);
        var files = attachmentsFor(page);
        var receipts = receipts(jobId, members, viewer.userId());
        long myRead = myRead(jobId, viewer.userId());
        return new ChatThread(jobId, job.getJobStatus(), canSend, sendUntil(job),
                members.stream().map(m -> participant(m, viewer)).toList(),
                page.stream().map(m -> view(m, viewer, names, files, receipts)).toList(), hasOlder, hasNewer,
                myRead, maxRead(jobId, viewer.userId()), unread(jobId, viewer.userId(), myRead),
                receipts.allDelivered(), receipts.list());
    }

    private Message send(Job job, Viewer viewer, SendMessage input) {
        String clientId = input.clientMessageId();
        if (clientId != null) {
            var existing = messages.findBySenderUserIdAndClientMessageId(viewer.userId(), clientId);
            if (existing.isPresent()) {
                if (!existing.get().getJobId().equals(job.getJobId())) {
                    throw new ConflictException("clientMessageId was already used for another chat", "CLIENT_MESSAGE_ID_REUSED");
                }
                return single(existing.get(), viewer, job);
            }
        }
        List<Long> fileIds = input.attachmentIds() == null ? List.of()
                : input.attachmentIds().stream().filter(Objects::nonNull).distinct().toList();
        if (fileIds.size() > MAX_ATTACHMENTS_PER_MESSAGE) {
            throw new InvalidInputException("A message can have at most 5 attachments", "TOO_MANY_ATTACHMENTS");
        }
        if (!canSend(job)) throw closedOrNotOpen(job);
        Instant now = Instant.now();
        var previous = messages.findTopBySenderUserIdOrderByMessageIdDesc(viewer.userId()).orElse(null);
        if (previous != null && now.isBefore(previous.getCreatedAt().plus(MIN_GAP))) {
            throw new TooManyRequestsException("Please wait a moment before sending another message", 1, "CHAT_RATE_LIMITED");
        }
        if (messages.countBySenderUserIdAndCreatedAtAfter(viewer.userId(), now.minusSeconds(60)) >= MAX_PER_MINUTE) {
            throw new TooManyRequestsException("Too many messages; please wait a minute", 60, "CHAT_RATE_LIMITED");
        }
        var cleaned = ChatContentFilter.clean(input.text());
        if (cleaned.text().isBlank() && fileIds.isEmpty()) {
            throw new InvalidInputException("Message text or an attachment is required", "MESSAGE_EMPTY");
        }
        List<ChatAttachment> files = fileIds.isEmpty() ? List.of() : attachments.lockAllById(fileIds);
        if (files.size() != fileIds.size() || files.stream().anyMatch(a ->
                !a.getJobId().equals(job.getJobId()) || !a.getUploaderUserId().equals(viewer.userId()))) {
            throw new InvalidInputException("Attachment not found in this chat; upload it again", "ATTACHMENT_NOT_FOUND");
        }
        if (files.stream().anyMatch(a -> a.getMessageId() != null)) {
            throw new ConflictException("An attachment was already sent in another message", "ATTACHMENT_ALREADY_SENT");
        }

        var message = new ChatMessage();
        message.setJobId(job.getJobId());
        message.setSenderUserId(viewer.userId());
        message.setSenderRole(viewer.role());
        message.setSenderProviderId(viewer.providerId());
        message.setClientMessageId(clientId);
        message.setBodyCiphertext(cipher.encrypt(job.getJobId(), viewer.userId(), cleaned.text()));
        message.setContactInfoHidden(cleaned.contactInfoHidden());
        message.setDeliveryTracked(true);
        message.setCreatedAt(now);
        message = messages.save(message);
        if (!files.isEmpty()) {
            var ordered = new ArrayList<>(files);
            ordered.sort(Comparator.comparingInt(a -> fileIds.indexOf(a.getAttachmentId())));
            for (var a : ordered) {
                a.setMessageId(message.getMessageId());
                a.setAttachedAt(now);
            }
            attachments.saveAll(ordered);
        }
        advance(job.getJobId(), viewer.userId(), message.getMessageId(), true, now);

        var members = members(job);
        notifyOthers(job, viewer, members, now);
        return single(message, viewer, job, members);
    }

    private ReadState markRead(Job job, Long userId, Long lastReadMessageId) {
        if (!readable(job, canSend(job))) throw new ConflictException(NOT_OPEN, "CHAT_NOT_OPEN");
        long id = messageInChat(job, lastReadMessageId);
        var state = advance(job.getJobId(), userId, id, true, Instant.now());
        return new ReadState(job.getJobId(), state.getLastReadMessageId(),
                unread(job.getJobId(), userId, state.getLastReadMessageId()));
    }

    private DeliveryState markDelivered(Job job, Long userId, Long lastDeliveredMessageId) {
        if (!readable(job, canSend(job))) throw new ConflictException(NOT_OPEN, "CHAT_NOT_OPEN");
        long id = messageInChat(job, lastDeliveredMessageId);
        var state = advance(job.getJobId(), userId, id, false, Instant.now());
        return new DeliveryState(job.getJobId(), delivered(state), state.getLastReadMessageId());
    }

    private long messageInChat(Job job, Long messageId) {
        return messages.findById(messageId)
                .filter(m -> m.getJobId().equals(job.getJobId()))
                .orElseThrow(() -> new InvalidInputException("Message is not part of this chat", "MESSAGE_NOT_IN_CHAT"))
                .getMessageId();
    }

    /** Moves the delivered cursor (and the read cursor too when read is true) forward; never backward. */
    private ChatReadState advance(Long jobId, Long userId, Long messageId, boolean read, Instant now) {
        var state = reads.findByJobIdAndUserId(jobId, userId).orElseGet(() -> {
            var created = new ChatReadState();
            created.setJobId(jobId);
            created.setUserId(userId);
            return created;
        });
        boolean changed = state.getReadStateId() == null;
        if (messageId > delivered(state)) {
            state.setLastDeliveredMessageId(messageId);
            changed = true;
        }
        if (read && (state.getLastReadMessageId() == null || messageId > state.getLastReadMessageId())) {
            state.setLastReadMessageId(messageId);
            changed = true;
        }
        if (changed) {
            state.setUpdatedAt(now);
            reads.save(state);
        }
        return state;
    }

    private Attachment upload(Job job, Viewer viewer, MultipartFile file, String clientUploadId) {
        if (clientUploadId != null && !CLIENT_ID.matcher(clientUploadId).matches()) {
            throw new InvalidInputException("clientUploadId must be 8-64 letters, digits, - or _", "INVALID_CLIENT_UPLOAD_ID");
        }
        if (clientUploadId != null) {
            var existing = attachments.findByUploaderUserIdAndClientUploadId(viewer.userId(), clientUploadId);
            if (existing.isPresent()) {
                if (!existing.get().getJobId().equals(job.getJobId())) {
                    throw new ConflictException("clientUploadId was already used for another chat", "CLIENT_UPLOAD_ID_REUSED");
                }
                return attachmentView(existing.get());
            }
        }
        if (!canSend(job)) throw closedOrNotOpen(job);
        Instant now = Instant.now();
        if (attachments.countByUploaderUserIdAndCreatedAtAfter(viewer.userId(), now.minus(Duration.ofMinutes(10)))
                >= MAX_UPLOADS_PER_10_MINUTES) {
            throw new TooManyRequestsException("Too many uploads; please wait a few minutes", 120, "UPLOAD_RATE_LIMITED");
        }
        if (attachments.countByUploaderUserIdAndJobIdAndMessageIdIsNull(viewer.userId(), job.getJobId()) >= MAX_UNSENT_PER_CHAT) {
            throw new ConflictException("Send or remove your unsent attachments before uploading more",
                    "TOO_MANY_UNSENT_ATTACHMENTS");
        }
        if (file == null || file.isEmpty()) throw new InvalidInputException("File is required", "FILE_EMPTY");
        if (file.getSize() > ChatFiles.MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File must not exceed 10 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidInputException("Could not read the uploaded file", "FILE_UNREADABLE");
        }
        var type = ChatFiles.detect(bytes).orElseThrow(() -> new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_FILE_TYPE", "Only JPG, PNG, WebP images and PDF documents can be sent"));
        var size = ChatFiles.dimensions(bytes, type).orElse(null);
        String key = "chat/" + job.getJobId() + "/" + UUID.randomUUID() + type.extension();
        storage.put(key, bytes, type.mimeType());

        var a = new ChatAttachment();
        a.setJobId(job.getJobId());
        a.setUploaderUserId(viewer.userId());
        a.setClientUploadId(clientUploadId);
        a.setStorageKey(key);
        a.setFileName(ChatFiles.fileName(file.getOriginalFilename(), type));
        a.setMimeType(type.mimeType());
        a.setSizeBytes(bytes.length);
        a.setWidth(size == null ? null : size.width());
        a.setHeight(size == null ? null : size.height());
        a.setSha256(sha256(bytes));
        a.setCreatedAt(now);
        try {
            a = attachments.saveAndFlush(a);
        } catch (RuntimeException e) {
            try { storage.delete(key); } catch (RuntimeException ignored) { /* the cleanup cannot see it; log only */ }
            throw e;
        }
        return attachmentView(a);
    }

    private Attachment attachment(Job job, Viewer viewer, Long attachmentId) {
        if (!readable(job, canSend(job))) throw new ConflictException(NOT_OPEN, "CHAT_NOT_OPEN");
        return attachmentView(visibleAttachment(job, viewer, attachmentId));
    }

    private void deleteUnsent(Job job, Viewer viewer, Long attachmentId) {
        var a = visibleAttachment(job, viewer, attachmentId);
        if (a.getMessageId() != null) {
            throw new ConflictException("A sent attachment cannot be removed", "ATTACHMENT_ALREADY_SENT");
        }
        removeObject(a);
        attachments.delete(a);
    }

    /** Sent files are visible to every participant; unsent files only to their uploader. */
    private ChatAttachment visibleAttachment(Job job, Viewer viewer, Long attachmentId) {
        return attachments.findById(attachmentId)
                .filter(a -> a.getJobId().equals(job.getJobId()))
                .filter(a -> a.getMessageId() != null || a.getUploaderUserId().equals(viewer.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found", "ATTACHMENT_NOT_FOUND"));
    }

    private Conversations conversations(List<Job> list, Viewer viewer) {
        List<Conversation> rows = new ArrayList<>();
        Map<Conversation, Instant> activity = new HashMap<>();
        for (var job : list) {
            var latest = messages.findTopByJobIdOrderByMessageIdDesc(job.getJobId()).orElse(null);
            boolean canSend = canSend(job);
            if (latest == null && !canSend) continue;
            var members = members(job);
            long myRead = myRead(job.getJobId(), viewer.userId());
            LastMessage last = null;
            if (latest != null) {
                String text = text(latest);
                String preview = text == null || text.isBlank() ? null
                        : text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) + "…" : text;
                last = new LastMessage(latest.getMessageId(), latest.getSenderRole(),
                        viewer.userId().equals(latest.getSenderUserId()), preview, latest.getCreatedAt(),
                        (int) attachments.countByMessageId(latest.getMessageId()));
            }
            var row = new Conversation(job.getJobId(), job.getJobStatus(), canSend,
                    members.stream().filter(m -> !viewer.userId().equals(m.userId()))
                            .map(m -> participant(m, viewer)).toList(),
                    last, latest == null ? 0 : unread(job.getJobId(), viewer.userId(), myRead));
            rows.add(row);
            activity.put(row, latest != null ? latest.getCreatedAt() : instant(job.getUpdatedAt()));
        }
        rows.sort(Comparator.comparing((Conversation c) -> activity.get(c),
                Comparator.nullsLast(Comparator.reverseOrder())));
        return new Conversations(rows, rows.stream().mapToLong(Conversation::unreadCount).sum());
    }

    private void notifyOthers(Job job, Viewer sender, List<Member> members, Instant now) {
        var request = requests.findByJobId(job.getJobId()).orElse(null);
        if (request == null) return;
        String type = CUSTOMER.equals(sender.role()) ? "CHAT_MESSAGE_FROM_CUSTOMER" : "CHAT_MESSAGE_FROM_PROVIDER";
        for (var member : members) {
            if (member.userId() == null || member.userId().equals(sender.userId())) continue;
            // One push per conversation every 30 seconds is enough to bring the recipient into the chat.
            if (events.existsByUserIdAndJobIdAndEventTypeAndCreatedAtAfter(member.userId(), job.getJobId(), type,
                    now.minus(PUSH_COALESCE))) continue;
            var event = new BookingDispatchEvent();
            event.setUserId(member.userId());
            event.setRequestId(request.getRequestId());
            event.setJobId(job.getJobId());
            event.setEventType(type);
            event.setCreatedAt(now);
            event.setNextDeliveryAt(now);
            event.setExpiresAt(now.plus(Duration.ofHours(1)));
            events.save(event);
        }
    }

    // ---------- access ----------

    private Viewer customerViewer(Long userId) {
        return new Viewer(CUSTOMER, userId, null);
    }

    private Job customerJob(Long userId, Long jobId) {
        var customer = customers.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND, "BOOKING_NOT_FOUND"));
        var job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND, "BOOKING_NOT_FOUND"));
        if (!customer.getCustomerId().equals(job.getCustomerId())) throw new ResourceNotFoundException(NOT_FOUND, "BOOKING_NOT_FOUND");
        return job;
    }

    private Viewer providerViewer(Long userId) {
        var provider = providers.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND, "BOOKING_NOT_FOUND"));
        return new Viewer(PROVIDER, userId, provider.getServiceProviderId());
    }

    private Job providerJob(Viewer viewer, Long jobId) {
        var job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND, "BOOKING_NOT_FOUND"));
        if (!providerIds(job).contains(viewer.providerId())) throw new ResourceNotFoundException(NOT_FOUND, "BOOKING_NOT_FOUND");
        return job;
    }

    private List<Long> providerIds(Job job) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (job.getAssignedServiceProviderId() != null) ids.add(job.getAssignedServiceProviderId());
        jobAssignments.findAllByJobIdOrderByJobProviderAssignmentIdAsc(job.getJobId()).stream()
                .filter(a -> a.getStatus() == null || !INACTIVE_ASSIGNMENTS.contains(a.getStatus()))
                .forEach(a -> ids.add(a.getServiceProviderId()));
        return List.copyOf(ids);
    }

    // ---------- window ----------

    boolean canSend(Job job) {
        String status = job.getJobStatus();
        if (status == null) return false;
        if (OPEN_STATUSES.contains(status)) return true;
        if (!ENDED_STATUSES.contains(status)) return false;
        Instant until = sendUntil(job);
        if (until == null || !Instant.now().isBefore(until)) return false;
        // A booking cancelled before confirmation never had a chat; do not open one after the fact.
        return "COMPLETED".equals(status) || messages.existsByJobId(job.getJobId());
    }

    private boolean readable(Job job, boolean canSend) {
        return canSend || ENDED_STATUSES.contains(job.getJobStatus()) && messages.existsByJobId(job.getJobId());
    }

    private ConflictException closedOrNotOpen(Job job) {
        return readable(job, false)
                ? new ConflictException("This chat is closed for new messages", "CHAT_CLOSED")
                : new ConflictException(NOT_OPEN, "CHAT_NOT_OPEN");
    }

    private Instant sendUntil(Job job) {
        if (!ENDED_STATUSES.contains(job.getJobStatus())) return null;
        Instant ended = instant(job.getUpdatedAt());
        return ended == null ? null : ended.plus(GRACE);
    }

    /** created_at/updated_at are written by Hibernate in the JVM time zone. */
    private static Instant instant(java.time.LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static Collection<String> chatStatuses() {
        var all = new HashSet<>(OPEN_STATUSES);
        all.addAll(ENDED_STATUSES);
        return all;
    }

    // ---------- views ----------

    private List<Member> members(Job job) {
        List<Member> result = new ArrayList<>();
        Customer customer = job.getCustomerId() == null ? null : customers.findById(job.getCustomerId()).orElse(null);
        if (customer != null) {
            result.add(new Member(CUSTOMER, customer.getUserId(), null,
                    name(customer.getFirstName(), customer.getLastName()), photoUrls.publicUrl(customer.getProfilePhoto())));
        }
        for (Long providerId : providerIds(job)) {
            ServiceProvider p = providers.findById(providerId).orElse(null);
            if (p == null) continue;
            result.add(new Member(PROVIDER, p.getUserId(), p.getServiceProviderId(),
                    name(p.getFirstName(), p.getLastName()), photoUrls.publicUrl(p.getProfilePhoto())));
        }
        return result;
    }

    /**
     * Positions of everyone except the viewer (everyone for admins). A message counts as delivered or seen only once
     * every other current participant has received or read it.
     */
    private Receipts receipts(Long jobId, List<Member> members, Long viewerUserId) {
        Map<Long, ChatReadState> states = reads.findAllByJobId(jobId).stream()
                .collect(Collectors.toMap(ChatReadState::getUserId, s -> s, (a, b) -> a));
        List<Receipt> list = new ArrayList<>();
        long allDelivered = Long.MAX_VALUE;
        long allRead = Long.MAX_VALUE;
        for (var m : members) {
            if (m.userId() == null || m.userId().equals(viewerUserId)) continue;
            var s = states.get(m.userId());
            long read = s == null || s.getLastReadMessageId() == null ? 0 : s.getLastReadMessageId();
            long delivered = s == null ? 0 : Math.max(delivered(s), read);
            list.add(new Receipt(m.role(), m.providerId(), m.displayName(), delivered, read));
            allDelivered = Math.min(allDelivered, delivered);
            allRead = Math.min(allRead, read);
        }
        if (list.isEmpty()) return new Receipts(0, 0, list);
        return new Receipts(allDelivered, allRead, list);
    }

    private static long delivered(ChatReadState s) {
        long d = s.getLastDeliveredMessageId() == null ? 0 : s.getLastDeliveredMessageId();
        long r = s.getLastReadMessageId() == null ? 0 : s.getLastReadMessageId();
        return Math.max(d, r);
    }

    private Map<Long, String> names(List<Member> members) {
        Map<Long, String> names = new HashMap<>();
        for (var m : members) if (m.userId() != null) names.put(m.userId(), m.displayName());
        return names;
    }

    private Participant participant(Member m, Viewer viewer) {
        return new Participant(m.role(), m.providerId(), m.displayName(), m.profilePhoto(),
                viewer != null && viewer.userId().equals(m.userId()));
    }

    private Map<Long, List<Attachment>> attachmentsFor(List<ChatMessage> page) {
        if (page.isEmpty()) return Map.of();
        var ids = page.stream().map(ChatMessage::getMessageId).toList();
        Map<Long, List<Attachment>> byMessage = new HashMap<>();
        for (var a : attachments.findAllByMessageIdInOrderByAttachmentIdAsc(ids)) {
            byMessage.computeIfAbsent(a.getMessageId(), k -> new ArrayList<>()).add(attachmentView(a));
        }
        return byMessage;
    }

    private Message single(ChatMessage m, Viewer viewer, Job job) {
        return single(m, viewer, job, members(job));
    }

    private Message single(ChatMessage m, Viewer viewer, Job job, List<Member> members) {
        return view(m, viewer, names(members), attachmentsFor(List.of(m)), receipts(job.getJobId(), members, viewer.userId()));
    }

    private Message view(ChatMessage m, Viewer viewer, Map<Long, String> names, Map<Long, List<Attachment>> files,
                         Receipts receipts) {
        String senderName = names.get(m.getSenderUserId());
        if (senderName == null && m.getSenderProviderId() != null) {
            senderName = providers.findById(m.getSenderProviderId())
                    .map(p -> name(p.getFirstName(), p.getLastName())).orElse(null);
        }
        boolean mine = viewer != null && viewer.userId().equals(m.getSenderUserId());
        return new Message(m.getMessageId(), m.getSenderRole(), m.getSenderProviderId(), senderName, mine, text(m),
                m.isContactInfoHidden(), m.getCreatedAt(), mine ? m.getClientMessageId() : null,
                files.getOrDefault(m.getMessageId(), List.of()), mine ? deliveryStatus(m, receipts) : null);
    }

    private static String deliveryStatus(ChatMessage m, Receipts r) {
        if (m.getMessageId() <= r.allRead()) return "SEEN";
        if (m.getMessageId() <= r.allDelivered()) return "DELIVERED";
        return Boolean.TRUE.equals(m.getDeliveryTracked()) ? "SENT" : "UNKNOWN";
    }

    private Attachment attachmentView(ChatAttachment a) {
        boolean image = a.getMimeType().startsWith("image/");
        var url = storage.signedUrl(a.getStorageKey(), a.getMimeType(), a.getFileName(), image);
        return new Attachment(a.getAttachmentId(), image ? "IMAGE" : "DOCUMENT", a.getFileName(), a.getMimeType(),
                a.getSizeBytes(), a.getWidth(), a.getHeight(), url.url(), url.expiresAt(), a.getMessageId(),
                a.getCreatedAt(), a.getMessageId() == null ? a.getCreatedAt().plus(UNSENT_TTL) : null);
    }

    private String text(ChatMessage m) {
        try {
            return cipher.decrypt(m.getJobId(), m.getSenderUserId(), m.getBodyCiphertext());
        } catch (IllegalStateException unreadable) {
            return null;
        }
    }

    private long myRead(Long jobId, Long userId) {
        return reads.findByJobIdAndUserId(jobId, userId).map(ChatReadState::getLastReadMessageId).orElse(0L);
    }

    private Long maxRead(Long jobId, Long excludeUserId) {
        return reads.findAllByJobId(jobId).stream()
                .filter(r -> excludeUserId == null || !excludeUserId.equals(r.getUserId()))
                .map(ChatReadState::getLastReadMessageId).max(Long::compare).orElse(0L);
    }

    private long unread(Long jobId, Long userId, long lastRead) {
        return messages.countByJobIdAndMessageIdGreaterThanAndSenderUserIdNot(jobId, lastRead, userId);
    }

    private static Long last(List<ChatMessage> page) {
        return page.get(page.size() - 1).getMessageId();
    }

    private static String name(String first, String last) {
        String value = (Objects.toString(first, "") + " " + Objects.toString(last, "")).trim();
        return value.isEmpty() ? null : value;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
