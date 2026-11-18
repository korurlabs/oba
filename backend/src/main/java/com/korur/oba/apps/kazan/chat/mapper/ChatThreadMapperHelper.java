package com.korur.oba.apps.kazan.chat.mapper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.dto.request.ChatThreadCreateRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatThreadPageResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatThreadResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatThreadSummaryResponse;
import com.korur.oba.apps.kazan.chat.dto.response.ChatUserResponse;
import com.korur.oba.apps.kazan.chat.entity.ChatMessage;
import com.korur.oba.apps.kazan.chat.entity.ChatMessageTicketRef;
import com.korur.oba.apps.kazan.chat.entity.ChatThread;
import com.korur.oba.apps.kazan.chat.entity.ChatThreadMember;
import com.korur.oba.apps.kazan.chat.helper.ChatThreadStatusResolver;
import com.korur.oba.apps.kazan.chat.helper.ChatTokenParser;
import com.korur.oba.apps.kazan.chat.repository.ChatThreadMemberRepository;
import com.korur.oba.apps.kazan.chat.validator.ChatValidator;
import com.korur.oba.apps.kazan.pullrequest.constant.PullRequestShareConstant;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestShareResponse;
import com.korur.oba.apps.kazan.pullrequest.service.PullRequestShareQueryService;
import com.korur.oba.dojo.time.BusinessCalendar;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentQueryService;

/**
 * Sets the relations of new chat threads and builds thread responses for one
 * reader.
 *
 * A thread page is assembled in a fixed number of queries: the roots (with
 * their summaries and preview members) in one batch, the reader's
 * memberships in one query, the users who set the threads' statuses in one
 * batch and the reader's role in one lookup. A thread outlives its root: a
 * deleted root becomes the placeholder.
 */
@Component
@RequiredArgsConstructor
public class ChatThreadMapperHelper {

    private static final DateTimeFormatter DAILY_UPDATE_TITLE_DATE_FORMATTER = DateTimeFormatter
            .ofPattern(ChatConstant.DAILY_UPDATE_THREAD_TITLE_DATE_PATTERN,
                    ChatConstant.DAILY_UPDATE_THREAD_TITLE_LOCALE)
            .withZone(BusinessCalendar.ZONE);

    private final ChatThreadMapper chatThreadMapper;
    private final ChatMessageMapperHelper chatMessageMapperHelper;
    private final ChatTokenParser chatTokenParser;
    private final ChatThreadStatusResolver chatThreadStatusResolver;
    private final ChatValidator chatValidator;
    private final ChatThreadMemberRepository chatThreadMemberRepository;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final AttachmentQueryService attachmentQueryService;
    private final ResourceAccessQueryService resourceAccessQueryService;
    private final PullRequestShareQueryService pullRequestShareQueryService;
    private final Clock clock;

    // -------------Relations-----------//

    /**
     * Sets the relations of a new thread: organization, channel (the root's),
     * creator, title and activity time. A null title in the request takes the default derived
     * from the root message; a given title is stored trimmed.
     *
     * @param thread         the new thread, from {@link ChatThreadMapper#toEntity}
     * @param request        the create request
     * @param root           the live root message
     * @param creator        the user starting the thread
     * @param organizationId the organization of the channel
     * @param now            the creation time, also the first activity time
     */
    public void setCreateRelations(ChatThread thread, ChatThreadCreateRequest request, ChatMessage root,
                                   User creator, UUID organizationId, Instant now) {
        thread.setOrganizationId(organizationId);
        thread.setChannelId(root.getChannelId());
        thread.setRootMessageId(root.getId());
        thread.setCreatedBy(creator.getId());
        thread.setLastMessageAt(now);
        thread.setTitle(request.title() != null
                ? request.title().trim()
                : resolveDefaultTitle(root, creator, organizationId));
    }

    /**
     * Trims the title an update copied from the request. The request only
     * refuses a title without visible characters, so leading and trailing
     * whitespace reach this point and are not stored.
     *
     * @param thread the thread after {@link ChatThreadMapper#updateEntity}
     */
    public void normalizeTitle(ChatThread thread) {
        thread.setTitle(thread.getTitle().trim());
    }

    /**
     * Derives the default title of a thread from its root message.
     *
     * - A daily update: "Daily update - First Last - 20 Oct", the day in the report time zone; the
     *   author's email stands in for a missing name, and "Thread" for an author with neither.
     * - A pull request share: the first line of its summary, "KEY: title" or the title, cut as text is;
     *   "Thread" when the share is gone.
     * - Text: the text with mentions and tickets shown as names, cut at 40 characters plus an ellipsis.
     * - Files only: the first file name, cut the same way.
     * - Otherwise: "Thread".
     *
     * @param root           the live root message of the organization, in a channel the caller has
     *                       checked the reader can read
     * @param reader         the user starting the thread, who reads the root's files
     * @param organizationId the organization of the channel
     * @return the title
     */
    public String resolveDefaultTitle(ChatMessage root, User reader, UUID organizationId) {
        if (root.getDailyUpdateId() != null) {
            return dailyUpdateTitle(root, organizationId);
        }
        if (root.getPullRequestShareId() != null) {
            return pullRequestShareTitle(root, reader, organizationId);
        }
        String text = renderTitleText(root, organizationId);
        if (!text.isEmpty()) {
            return shorten(text);
        }
        // The caller has checked the root and its channel for this reader.
        List<AttachmentResponse> attachments = resourceAccessQueryService.withReadGrant(reader,
                ResourceAccessType.CHAT_MESSAGE, List.of(root.getId()),
                () -> attachmentQueryService.listAttachmentsByEntity(reader, organizationId,
                        StorageEntityType.CHAT_MESSAGE, root.getId()));
        if (!attachments.isEmpty()) {
            return shorten(attachments.getFirst().fileName());
        }
        return ChatConstant.DEFAULT_THREAD_TITLE;
    }

    // -------------Responses-----------//

    /**
     * Maps one thread for its panel, with every member.
     *
     * @param thread         the thread
     * @param reader         the user reading it
     * @param organizationId the organization of the thread
     * @return the response
     */
    public ChatThreadResponse toResponse(ChatThread thread, User reader, UUID organizationId) {
        List<UUID> memberIds = chatThreadMemberRepository.findByThread_IdOrderByCreatedAtAsc(thread.getId()).stream()
                .map(ChatThreadMember::getUserId)
                .toList();
        List<ChatUserResponse> members = new ArrayList<>(
                chatMessageMapperHelper.resolveUsers(organizationId, memberIds).values());
        ChatMessageResponse root = chatMessageMapperHelper
                .toThreadRootResponses(List.of(thread.getRootMessageId()), reader, organizationId)
                .get(thread.getRootMessageId());
        Instant now = clock.instant();
        Map<UUID, ChatUserResponse> changers = resolveStatusChangers(List.of(thread), organizationId, now);
        return toResponse(thread, root, now, changers, memberIds.contains(reader.getId()), members,
                canManage(thread, reader.getId(), findReaderRole(organizationId, reader.getId())));
    }

    /**
     * Maps threads for a list, in the given order. The members are the
     * preview members of each thread's summary, as the list shows them.
     *
     * @param threads        threads of one organization
     * @param reader         the user reading them
     * @param organizationId the organization of the threads
     * @return the responses in the same order
     */
    public List<ChatThreadResponse> toResponses(List<ChatThread> threads, User reader, UUID organizationId) {
        if (threads.isEmpty()) {
            return List.of();
        }
        Set<UUID> rootIds = new LinkedHashSet<>();
        Set<UUID> threadIds = new LinkedHashSet<>();
        for (ChatThread thread : threads) {
            rootIds.add(thread.getRootMessageId());
            threadIds.add(thread.getId());
        }
        Map<UUID, ChatMessageResponse> roots = chatMessageMapperHelper
                .toThreadRootResponses(rootIds, reader, organizationId);
        Set<UUID> memberThreadIds = new HashSet<>(
                chatThreadMemberRepository.findThreadIdsByUserIdAndThreadIds(reader.getId(), threadIds));
        OrganizationMemberRole role = findReaderRole(organizationId, reader.getId());
        Instant now = clock.instant();
        Map<UUID, ChatUserResponse> changers = resolveStatusChangers(threads, organizationId, now);

        List<ChatThreadResponse> responses = new ArrayList<>();
        for (ChatThread thread : threads) {
            ChatMessageResponse root = roots.get(thread.getRootMessageId());
            responses.add(toResponse(thread, root, now, changers, memberThreadIds.contains(thread.getId()),
                    previewMembers(root), canManage(thread, reader.getId(), role)));
        }
        return responses;
    }

    /**
     * Maps a keyset page of threads for a list.
     *
     * @param threads        the page's threads
     * @param hasMore        true when more threads exist after the last item
     * @param reader         the user reading them
     * @param organizationId the organization of the threads
     * @return the page response
     */
    public ChatThreadPageResponse toPageResponse(List<ChatThread> threads, boolean hasMore, User reader,
                                                 UUID organizationId) {
        return chatThreadMapper.toPageResponse(toResponses(threads, reader, organizationId), hasMore);
    }

    // -------------Building blocks-----------//

    /**
     * Maps one thread with the status the reader sees at the read time.
     */
    private ChatThreadResponse toResponse(ChatThread thread, ChatMessageResponse root, Instant now,
                                          Map<UUID, ChatUserResponse> changers, boolean member,
                                          List<ChatUserResponse> members, boolean canManage) {
        UUID changerId = chatThreadStatusResolver.resolveChangedBy(thread, now);
        return chatThreadMapper.toResponse(thread, root, chatThreadStatusResolver.resolve(thread, now),
                chatThreadStatusResolver.resolveChangedAt(thread, now),
                changerId == null ? null : changers.get(changerId), member, members, canManage);
    }

    /**
     * Resolves the users who set the threads' statuses in one batch; none
     * when no thread shows who set its status.
     */
    private Map<UUID, ChatUserResponse> resolveStatusChangers(List<ChatThread> threads, UUID organizationId,
                                                              Instant now) {
        Set<UUID> changerIds = new LinkedHashSet<>();
        for (ChatThread thread : threads) {
            UUID changerId = chatThreadStatusResolver.resolveChangedBy(thread, now);
            if (changerId != null) {
                changerIds.add(changerId);
            }
        }
        return changerIds.isEmpty() ? Map.of() : chatMessageMapperHelper.resolveUsers(organizationId, changerIds);
    }

    private String renderTitleText(ChatMessage root, UUID organizationId) {
        ChatTokenParser.ChatTokens tokens = chatTokenParser.parse(root.getContent());
        Map<UUID, ChatUserResponse> users = tokens.userIds().isEmpty()
                ? Map.of()
                : chatMessageMapperHelper.resolveUsers(organizationId, tokens.userIds());
        Map<UUID, ChatMessageTicketRef> ticketRefs = new HashMap<>();
        for (ChatMessageTicketRef ticketRef : root.getTicketRefs()) {
            if (ticketRef.getTicketId() != null) {
                ticketRefs.put(ticketRef.getTicketId(), ticketRef);
            }
        }
        String rendered = chatTokenParser.renderPlain(root.getContent(), users, ticketRefs);
        return ChatConstant.WHITESPACE_RUN_PATTERN.matcher(rendered).replaceAll(ChatConstant.TITLE_WHITESPACE).trim();
    }

    /**
     * Titles a thread on a daily update after its author. An account may have
     * no profile name yet, and a deleted account has neither name nor email,
     * so a missing name never reaches the title as "null": the email takes
     * its place, and without one the title is the plain default. Folding
     * whitespace keeps a single name or the email from leaving a double space.
     * Names and emails may be longer than a title column holds, so the title
     * is cut to the longest title allowed.
     */
    private String dailyUpdateTitle(ChatMessage root, UUID organizationId) {
        ChatUserResponse author = chatMessageMapperHelper
                .resolveUsers(organizationId, List.of(root.getAuthor().getId()))
                .get(root.getAuthor().getId());
        String firstName = blankToEmpty(author.firstName());
        String lastName = blankToEmpty(author.lastName());
        if (firstName.isEmpty() && lastName.isEmpty()) {
            firstName = blankToEmpty(author.email());
        }
        if (firstName.isEmpty() && lastName.isEmpty()) {
            return ChatConstant.DEFAULT_THREAD_TITLE;
        }
        String title = String.format(ChatConstant.DAILY_UPDATE_THREAD_TITLE_LOCALE,
                ChatConstant.DAILY_UPDATE_THREAD_TITLE_FORMAT, firstName, lastName,
                DAILY_UPDATE_TITLE_DATE_FORMATTER.format(root.getCreatedAt()));
        String folded = ChatConstant.WHITESPACE_RUN_PATTERN.matcher(title).replaceAll(ChatConstant.TITLE_WHITESPACE);
        return chatTokenParser.truncate(folded,
                ChatConstant.MAX_THREAD_TITLE_LENGTH - ChatConstant.THREAD_TITLE_ELLIPSIS.length(),
                ChatConstant.THREAD_TITLE_ELLIPSIS);
    }

    private static String blankToEmpty(String text) {
        return text == null ? ChatConstant.EMPTY_TEXT : text.trim();
    }

    /**
     * Titles a thread on a pull request share entry with the first line of
     * the share's summary, the line the card shows as its heading. The
     * summary is built without regard to the reader, so the title is the
     * same whoever starts the thread.
     */
    private String pullRequestShareTitle(ChatMessage root, User reader, UUID organizationId) {
        PullRequestShareResponse share = pullRequestShareQueryService
                .getSharesByIds(reader, organizationId, List.of(root.getPullRequestShareId()))
                .get(root.getPullRequestShareId());
        if (share == null) {
            return ChatConstant.DEFAULT_THREAD_TITLE;
        }
        String firstLine = share.summaryText().split(PullRequestShareConstant.SUMMARY_LINE_BREAK, 2)[0].trim();
        return firstLine.isEmpty() ? ChatConstant.DEFAULT_THREAD_TITLE : shorten(firstLine);
    }

    private String shorten(String text) {
        return chatTokenParser.truncate(text, ChatConstant.DEFAULT_THREAD_TITLE_LENGTH,
                ChatConstant.THREAD_TITLE_ELLIPSIS);
    }

    private static List<ChatUserResponse> previewMembers(ChatMessageResponse root) {
        if (root == null || root.thread() == null) {
            return List.of();
        }
        ChatThreadSummaryResponse summary = root.thread();
        List<ChatUserResponse> members = new ArrayList<>();
        for (UUID userId : summary.memberPreviewIds()) {
            ChatUserResponse user = root.users().get(userId);
            if (user != null) {
                members.add(user);
            }
        }
        return members;
    }

    private OrganizationMemberRole findReaderRole(UUID organizationId, UUID readerId) {
        return organizationMemberQueryService.findMember(organizationId, readerId)
                .map(OrganizationMember::getRole)
                .orElse(null);
    }

    private boolean canManage(ChatThread thread, UUID readerId, OrganizationMemberRole role) {
        return chatValidator.canManageThread(thread, readerId, role);
    }
}
