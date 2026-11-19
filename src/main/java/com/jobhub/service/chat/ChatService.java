package com.jobhub.service.chat;

import com.jobhub.dto.chat.ChatApi.*;
import com.jobhub.entity.chat.ChatMessage;
import com.jobhub.entity.chat.ChatReadState;
import com.jobhub.entity.customer.Customer;
import com.jobhub.entity.job.BookingDispatchEvent;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.exception.TooManyRequestsException;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

/**
 * Text chat between a booking's customer and its providers. Every call checks that the signed-in user is the
 * booking's customer or an active provider on it (anyone else gets "Booking not found"). Chat opens at confirmation
 * and accepts new messages until 24 hours after the booking is completed or cancelled; after that it is read-only.
 */
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
    static final String CUSTOMER = "CUSTOMER";
    static final String PROVIDER = "PROVIDER";
    private static final String NOT_FOUND = "Booking not found";
    private static final int PREVIEW_LENGTH = 100;

    private final JobRepository jobs;
    private final CustomerRepository customers;
    private final ServiceProviderRepository providers;
    private final JobProviderAssignmentRepository jobAssignments;
    private final ChatMessageRepository messages;
    private final ChatReadStateRepository reads;
    private final BookingRequestRepository requests;
    private final BookingDispatchEventRepository events;
    private final ChatCipher cipher;
    private final ProfilePhotoUrls photoUrls;
    private final AuditService audit;

    /** The signed-in user in one booking chat. providerId is set for providers. */
    private record Viewer(String role, Long userId, Long providerId) {}

    private record Member(String role, Long userId, Long providerId, String displayName, String profilePhoto) {}

    // ---------- customer ----------

    @Transactional(readOnly = true)
    public ChatThread customerThread(Long userId, Long jobId, Long after, Long before) {
        var job = customerJob(userId, jobId);
        return thread(job, new Viewer(CUSTOMER, userId, null), after, before);
    }

    @Transactional
    public Message customerSend(Long userId, Long jobId, SendMessage input) {
        return send(customerJob(userId, jobId), new Viewer(CUSTOMER, userId, null), input);
    }

    @Transactional
    public ReadState customerRead(Long userId, Long jobId, MarkRead input) {
        return markRead(customerJob(userId, jobId), userId, input.lastReadMessageId());
    }

    @Transactional(readOnly = true)
    public Conversations customerConversations(Long userId) {
        var customer = customers.findByUserId(userId).orElse(null);
        if (customer == null) return new Conversations(List.of(), 0);
        var list = jobs.findTop50ByCustomerIdAndJobStatusInOrderByUpdatedAtDesc(customer.getCustomerId(), chatStatuses());
        return conversations(list, new Viewer(CUSTOMER, userId, null));
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
        var job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        long cursor = after == null ? 0 : Math.max(0, after);
        audit.record(actorId, "ADMIN_CHAT_VIEWED", "JOB", jobId, null, Map.of("after", cursor));
        var members = members(job);
        var page = messages.findTop50ByJobIdAndMessageIdGreaterThanOrderByMessageIdAsc(jobId, cursor);
        var names = names(members);
        var list = page.stream().map(m -> view(m, null, names)).toList();
        boolean hasNewer = !page.isEmpty() && messages.existsByJobIdAndMessageIdGreaterThan(jobId, last(page));
        return new ChatThread(jobId, job.getJobStatus(), false, sendUntil(job),
                members.stream().map(m -> participant(m, null)).toList(), list,
                cursor > 0 && messages.existsByJobIdAndMessageIdLessThan(jobId, cursor + 1), hasNewer, null,
                maxRead(jobId, null), 0);
    }

    // ---------- shared ----------

    private ChatThread thread(Job job, Viewer viewer, Long after, Long before) {
        boolean canSend = canSend(job);
        if (!readable(job, canSend)) throw new ConflictException("Chat opens once the booking is confirmed");
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
        long myRead = myRead(jobId, viewer.userId());
        return new ChatThread(jobId, job.getJobStatus(), canSend, sendUntil(job),
                members.stream().map(m -> participant(m, viewer)).toList(),
                page.stream().map(m -> view(m, viewer, names)).toList(), hasOlder, hasNewer,
                myRead, maxRead(jobId, viewer.userId()), unread(jobId, viewer.userId(), myRead));
    }

    private Message send(Job job, Viewer viewer, SendMessage input) {
        String clientId = input.clientMessageId();
        if (clientId != null) {
            var existing = messages.findBySenderUserIdAndClientMessageId(viewer.userId(), clientId);
            if (existing.isPresent()) {
                if (!existing.get().getJobId().equals(job.getJobId())) {
                    throw new ConflictException("clientMessageId was already used for another chat");
                }
                return view(existing.get(), viewer, names(members(job)));
            }
        }
        if (!canSend(job)) throw new ConflictException("This chat is closed for new messages");
        Instant now = Instant.now();
        var previous = messages.findTopBySenderUserIdOrderByMessageIdDesc(viewer.userId()).orElse(null);
        if (previous != null && now.isBefore(previous.getCreatedAt().plus(MIN_GAP))) {
            throw new TooManyRequestsException("Please wait a moment before sending another message", 1);
        }
        if (messages.countBySenderUserIdAndCreatedAtAfter(viewer.userId(), now.minusSeconds(60)) >= MAX_PER_MINUTE) {
            throw new TooManyRequestsException("Too many messages; please wait a minute", 60);
        }
        var cleaned = ChatContentFilter.clean(input.text());
        if (cleaned.text().isBlank()) throw new IllegalArgumentException("Message text is required");

        var message = new ChatMessage();
        message.setJobId(job.getJobId());
        message.setSenderUserId(viewer.userId());
        message.setSenderRole(viewer.role());
        message.setSenderProviderId(viewer.providerId());
        message.setClientMessageId(clientId);
        message.setBodyCiphertext(cipher.encrypt(job.getJobId(), viewer.userId(), cleaned.text()));
        message.setContactInfoHidden(cleaned.contactInfoHidden());
        message.setCreatedAt(now);
        message = messages.save(message);
        advanceRead(job.getJobId(), viewer.userId(), message.getMessageId(), now);

        var members = members(job);
        notifyOthers(job, viewer, members, now);
        return view(message, viewer, names(members));
    }

    private ReadState markRead(Job job, Long userId, Long lastReadMessageId) {
        if (!readable(job, canSend(job))) throw new ConflictException("Chat opens once the booking is confirmed");
        var message = messages.findById(lastReadMessageId)
                .filter(m -> m.getJobId().equals(job.getJobId()))
                .orElseThrow(() -> new IllegalArgumentException("Message is not part of this chat"));
        long read = advanceRead(job.getJobId(), userId, message.getMessageId(), Instant.now());
        return new ReadState(job.getJobId(), read, unread(job.getJobId(), userId, read));
    }

    private long advanceRead(Long jobId, Long userId, Long messageId, Instant now) {
        var state = reads.findByJobIdAndUserId(jobId, userId).orElseGet(() -> {
            var created = new ChatReadState();
            created.setJobId(jobId);
            created.setUserId(userId);
            return created;
        });
        if (state.getLastReadMessageId() == null || messageId > state.getLastReadMessageId()) {
            state.setLastReadMessageId(messageId);
            state.setUpdatedAt(now);
            reads.save(state);
        }
        return state.getLastReadMessageId();
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
                String preview = text == null ? null
                        : text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) + "…" : text;
                last = new LastMessage(latest.getMessageId(), latest.getSenderRole(),
                        viewer.userId().equals(latest.getSenderUserId()), preview, latest.getCreatedAt());
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

    private Job customerJob(Long userId, Long jobId) {
        var customer = customers.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        var job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (!customer.getCustomerId().equals(job.getCustomerId())) throw new ResourceNotFoundException(NOT_FOUND);
        return job;
    }

    private Viewer providerViewer(Long userId) {
        var provider = providers.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        return new Viewer(PROVIDER, userId, provider.getServiceProviderId());
    }

    private Job providerJob(Viewer viewer, Long jobId) {
        var job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (!providerIds(job).contains(viewer.providerId())) throw new ResourceNotFoundException(NOT_FOUND);
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

    private Map<Long, String> names(List<Member> members) {
        Map<Long, String> names = new HashMap<>();
        for (var m : members) if (m.userId() != null) names.put(m.userId(), m.displayName());
        return names;
    }

    private Participant participant(Member m, Viewer viewer) {
        return new Participant(m.role(), m.providerId(), m.displayName(), m.profilePhoto(),
                viewer != null && viewer.userId().equals(m.userId()));
    }

    private Message view(ChatMessage m, Viewer viewer, Map<Long, String> names) {
        String senderName = names.get(m.getSenderUserId());
        if (senderName == null && m.getSenderProviderId() != null) {
            senderName = providers.findById(m.getSenderProviderId())
                    .map(p -> name(p.getFirstName(), p.getLastName())).orElse(null);
        }
        return new Message(m.getMessageId(), m.getSenderRole(), m.getSenderProviderId(), senderName,
                viewer != null && viewer.userId().equals(m.getSenderUserId()), text(m), m.isContactInfoHidden(),
                m.getCreatedAt(), viewer != null && viewer.userId().equals(m.getSenderUserId()) ? m.getClientMessageId() : null);
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
}
