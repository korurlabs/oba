package com.korur.oba.apps.kaizen.chat.mapper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;
import com.korur.oba.apps.kaizen.chat.dto.projection.ChatReactionSummaryProjection;
import com.korur.oba.apps.kaizen.chat.dto.projection.ChatReplyPreviewProjection;
import com.korur.oba.apps.kaizen.chat.dto.projection.ChatThreadSummaryProjection;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatLinkedTicketResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatMessagePageResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatMessageResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatReactionSummaryResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatReplyPreviewResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatThreadSummaryResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatTicketRefResponse;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatUserResponse;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessage;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessageMention;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessageTicketRef;
import com.korur.oba.apps.kaizen.chat.entity.ChatThread;
import com.korur.oba.apps.kaizen.chat.helper.ChatTokenParser;
import com.korur.oba.apps.kaizen.chat.helper.ChatTokenParser.ChatTokens;
import com.korur.oba.apps.kaizen.chat.mapper.ChatMessageMapper.ChatMessageParts;
import com.korur.oba.apps.kaizen.chat.repository.ChatMessageMentionRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatMessageRepository;
import com.korur.oba.apps.kaizen.chat.repository.ChatThreadMemberRepository;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdateResponse;
import com.korur.oba.apps.kaizen.dailyupdate.service.DailyUpdateQueryService;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.PullRequestShareResponse;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.PullRequestShareReviewerResponse;
import com.korur.oba.apps.kaizen.pullrequest.service.PullRequestShareQueryService;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.service.TicketQueryService;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.access.service.ResourceAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberSummaryProjection;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentQueryService;

/**
 * Sets the relations of chat messages (author, organization, thread, mention
 * rows, ticket snapshots) and builds message responses for one reader.
 *
 * A page is assembled in a fixed number of queries, whatever its size: one
 * per kind of data (reply previews, ticket access by ID and by key,
 * reactions, thread summaries, the reader's here mentions and thread
 * memberships, daily updates, pull request shares, attachments, members and
 * users), each run only when the page needs it. Nothing is looked up per
 * message.
 *
 * Nothing leaks from what the reader may not see: a quoted message that was
 * deleted shows neither author, text nor files; a typed ticket key links only when
 * the reader can open the ticket; a daily update the reader may not read
 * leaves the message without its card instead of failing the page; a pull
 * request share links its ticket key only when the reader can open the
 * ticket; the placeholder of a deleted thread root carries only its thread
 * summary.
 *
 * A realtime signal carries one message built for no particular reader, so
 * every reader of the channel gets the same copy: nothing marks it as
 * mentioning, quoting or reacted to by anyone, no ticket opens and no typed
 * key links. A message whose copy differs per reader in ways only the server
 * can tell is not built that way at all, and each client reads it for itself.
 */
@Component
@RequiredArgsConstructor
public class ChatMessageMapperHelper {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatTokenParser chatTokenParser;
    private final ResourceAccessQueryService resourceAccessQueryService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageMentionRepository chatMessageMentionRepository;
    private final ChatThreadMemberRepository chatThreadMemberRepository;
    private final TicketQueryService ticketQueryService;
    private final UserQueryService userQueryService;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final AttachmentQueryService attachmentQueryService;
    private final DailyUpdateQueryService dailyUpdateQueryService;
    private final PullRequestShareQueryService pullRequestShareQueryService;

    // -------------Relations-----------//

    /**
     * Sets the relations of a new message: organization, author, thread, the
     * everyone and here flags, the mention rows and the ticket snapshots.
     * Direct mentions come first; every user online for a here mention who is
     * not mentioned directly and is not the author gets a row marked viaHere.
     *
     * @param message        the new message, from {@link ChatMessageMapper#toEntity}
     * @param author         the sender
     * @param organizationId the channel's organization
     * @param thread         the thread; null for a channel message
     * @param tokens         the tokens of the message's text
     * @param ticketsById    the referenced tickets, all of tokens.ticketIds()
     * @param hereUserIds    members online at send time; read only when the message mentions here
     */
    public void setCreateRelations(ChatMessage message, User author, UUID organizationId, ChatThread thread,
                                   ChatTokens tokens, Map<UUID, Ticket> ticketsById, Collection<UUID> hereUserIds) {
        message.setOrganizationId(organizationId);
        message.setAuthor(author);
        message.setThread(thread);
        message.setMentionsEveryone(tokens.mentionsEveryone());
        message.setMentionsHere(tokens.mentionsHere());
        Set<UUID> hereIds = tokens.mentionsHere()
                ? collectHereUserIds(tokens, hereUserIds, author.getId())
                : Set.of();
        applyMentions(message, tokens, hereIds);
        applyTicketRefs(message, tokens, ticketsById);
    }

    /**
     * Replaces the mentions and ticket references of an edited message.
     *
     * A user or ticket that stays keeps its row, because the unique keys on
     * (message, user) and (message, ticket) would refuse a new row inserted
     * before the old one is removed. A kept ticket reference takes a new
     * snapshot. A here mention that stays keeps the users it reached when it
     * was added; only a here mention new to this edit reaches the users
     * online now; a removed here mention removes them.
     *
     * @param message     the stored message, text already replaced
     * @param tokens      the tokens of the new text
     * @param ticketsById the referenced tickets, all of tokens.ticketIds()
     * @param hereUserIds members online now; read only when the edit adds a here mention
     */
    public void setUpdateRelations(ChatMessage message, ChatTokens tokens, Map<UUID, Ticket> ticketsById,
                                   Collection<UUID> hereUserIds) {
        Set<UUID> hereIds;
        if (!tokens.mentionsHere()) {
            hereIds = Set.of();
        } else if (message.isMentionsHere()) {
            hereIds = new LinkedHashSet<>();
            for (ChatMessageMention mention : message.getMentions()) {
                if (mention.isViaHere() && !tokens.userIds().contains(mention.getUserId())) {
                    hereIds.add(mention.getUserId());
                }
            }
        } else {
            hereIds = collectHereUserIds(tokens, hereUserIds, message.getAuthor().getId());
        }
        message.setMentionsEveryone(tokens.mentionsEveryone());
        message.setMentionsHere(tokens.mentionsHere());
        applyMentions(message, tokens, hereIds);
        applyTicketRefs(message, tokens, ticketsById);
    }

    /**
     * Replaces the mention rows of a pull request share entry by one row per
     * reviewer, so the entry counts as a mention for each reviewer in the
     * unread counts. The entry has no text, so reviewers are its only
     * mentions. A reviewer who stays keeps the row.
     *
     * @param message     the share entry, new or stored
     * @param reviewerIds the share's reviewers in display order
     */
    public void setPullRequestShareMentions(ChatMessage message, Collection<UUID> reviewerIds) {
        Map<UUID, ChatMessageMention> stored = new HashMap<>();
        for (ChatMessageMention mention : message.getMentions()) {
            stored.put(mention.getUserId(), mention);
        }
        List<ChatMessageMention> mentions = new ArrayList<>();
        for (UUID reviewerId : new LinkedHashSet<>(reviewerIds)) {
            mentions.add(keepOrCreateMention(message, stored.get(reviewerId), reviewerId, false));
        }
        message.getMentions().clear();
        message.getMentions().addAll(mentions);
    }

    // -------------Responses-----------//

    /**
     * Maps messages to responses for one reader, in the given order.
     *
     * @param messages       messages of one organization, live ones and deleted thread roots
     * @param reader         the user reading them
     * @param organizationId the organization of the messages
     * @return the responses in the same order
     */
    public List<ChatMessageResponse> toResponses(List<ChatMessage> messages, User reader, UUID organizationId) {
        return new ArrayList<>(assemble(messages, List.of(), reader, reader, organizationId, null).values());
    }

    /**
     * Maps one message to a response for one reader.
     *
     * @param message        the message
     * @param reader         the user reading it
     * @param organizationId the organization of the message
     * @param clientNonce    nonce of the send that created the message, echoed so the
     *                       sender replaces its pending row; null outside that response
     * @return the response
     */
    public ChatMessageResponse toResponse(ChatMessage message, User reader, UUID organizationId, String clientNonce) {
        return assemble(List.of(message), List.of(), reader, reader, organizationId, clientNonce)
                .get(message.getId());
    }

    /**
     * Maps one live message to the copy a realtime signal carries, the same
     * for every reader of its channel: mentionsMe, repliesToMe and every
     * reactedByMe and canOpen false, no linked keys, no daily update and no
     * pull request share.
     * Built in the same queries as one message for a reader.
     *
     * Empty when the copy would differ per reader in ways a client cannot
     * work out: the text references a ticket or types a ticket key, whose
     * link depends on the reader's boards; it mentions here, which reached
     * only the members online at send time; it is, or quotes, a daily
     * update entry, whose card depends on who may read the update; or it is a
     * pull request share entry, whose ticket link and mention depend on the
     * reader. The client then reads the message for itself.
     *
     * @param message     the live message
     * @param clientNonce nonce of the send that created the message; null otherwise
     * @return the reader-neutral response, or empty when the message needs a per-reader read
     */
    public Optional<ChatMessageResponse> toNeutralResponse(ChatMessage message, String clientNonce) {
        if (message.isDeleted() || dependsOnReader(message)) {
            return Optional.empty();
        }
        // Files are listed as the author, who can read the channel the
        // message was just written in.
        return Optional.ofNullable(assemble(List.of(message), List.of(), null, message.getAuthor(),
                message.getOrganizationId(), clientNonce).get(message.getId()));
    }

    /**
     * Maps a keyset page of messages for one reader.
     *
     * @param messages       the page's messages, oldest first
     * @param hasOlder       true when older messages exist
     * @param hasNewer       true when newer messages exist
     * @param reader         the user reading them
     * @param organizationId the organization of the messages
     * @return the page response
     */
    public ChatMessagePageResponse toPageResponse(List<ChatMessage> messages, boolean hasOlder, boolean hasNewer,
                                                  User reader, UUID organizationId) {
        return chatMessageMapper.toPageResponse(toResponses(messages, reader, organizationId), hasOlder, hasNewer);
    }

    /**
     * Maps the root messages of threads for one reader. A root that was
     * deleted becomes the placeholder with its thread summary, its channel
     * and its time, so a thread outlives its root.
     *
     * @param rootMessageIds IDs of the root messages
     * @param reader         the user reading them
     * @param organizationId the organization of the threads
     * @return the responses by root message ID, every requested ID included
     */
    public Map<UUID, ChatMessageResponse> toThreadRootResponses(Collection<UUID> rootMessageIds, User reader,
                                                                UUID organizationId) {
        if (rootMessageIds.isEmpty()) {
            return Map.of();
        }
        List<ChatMessage> roots = new ArrayList<>(chatMessageRepository.findAllById(
                        new LinkedHashSet<>(rootMessageIds)).stream()
                .filter(root -> organizationId.equals(root.getOrganizationId()))
                .toList());
        Set<UUID> liveRootIds = ids(roots);
        List<UUID> missingRootIds = rootMessageIds.stream()
                .distinct()
                .filter(rootId -> !liveRootIds.contains(rootId))
                .toList();
        if (!missingRootIds.isEmpty()) {
            // Deleted roots are read with their row, so the placeholder
            // carries the channel and the time of the root it stands for.
            roots.addAll(chatMessageRepository.findDeletedByIdsAndOrganizationId(missingRootIds, organizationId));
        }
        Set<UUID> foundRootIds = ids(roots);
        List<UUID> deletedRootIds = missingRootIds.stream()
                .filter(rootId -> !foundRootIds.contains(rootId))
                .toList();
        return assemble(roots, deletedRootIds, reader, reader, organizationId, null);
    }

    /**
     * Resolves users for display in the organization's chat: active members
     * by their member summary, others as non-members with the name their
     * account still has, and users without an account by ID only.
     *
     * @param organizationId the organization
     * @param userIds        the users
     * @return the users by ID in the given order
     */
    public Map<UUID, ChatUserResponse> resolveUsers(UUID organizationId, Collection<UUID> userIds) {
        return resolveUsers(organizationId, userIds, Map.of());
    }

    /**
     * Builds the responses of the given messages and of placeholders for the
     * deleted thread roots, with one query per kind of data.
     *
     * A null reader builds the reader-neutral copy of one message, which
     * toNeutralResponse has already checked: every reader-specific load is
     * skipped, and a message that quotes a daily update entry is left out of
     * the result, since only the loaded preview tells that.
     */
    private Map<UUID, ChatMessageResponse> assemble(List<ChatMessage> messages, List<UUID> deletedRootIds,
                                                    User reader, User fileReader, UUID organizationId,
                                                    String clientNonce) {
        if (messages.isEmpty() && deletedRootIds.isEmpty()) {
            return Map.of();
        }
        UUID readerId = reader == null ? null : reader.getId();
        List<ChatMessage> live = messages.stream().filter(message -> !message.isDeleted()).toList();
        Map<UUID, ChatMessage> pageById = new HashMap<>();
        for (ChatMessage message : messages) {
            pageById.put(message.getId(), message);
        }
        Map<UUID, ChatTokens> tokensById = new HashMap<>();
        Map<UUID, List<String>> typedKeysById = new HashMap<>();
        for (ChatMessage message : live) {
            tokensById.put(message.getId(), chatTokenParser.parse(message.getContent()));
            typedKeysById.put(message.getId(), chatTokenParser.extractTicketKeys(message.getContent()));
        }

        Map<UUID, ChatReplyPreviewProjection> previews = loadReplyPreviews(live, organizationId);
        if (reader == null && quotesDailyUpdate(live, previews)) {
            return Map.of();
        }
        Map<UUID, ChatTokens> quotedTokens = new HashMap<>();
        for (ChatReplyPreviewProjection preview : previews.values()) {
            if (isLiveTextMessage(preview)) {
                quotedTokens.put(preview.getId(), chatTokenParser.parse(preview.getContent()));
            }
        }
        Map<UUID, ChatMessage> quotedWithTickets = loadQuotedMessagesWithTickets(quotedTokens, pageById);

        Set<UUID> openableTicketIds = loadOpenableTicketIds(readerId, organizationId, live);
        Map<String, Ticket> openableTicketsByKey = loadOpenableTicketsByKey(readerId, organizationId,
                typedKeysById.values());
        Map<UUID, List<ChatReactionSummaryResponse>> reactionsById = reader == null
                ? loadNeutralReactions(live)
                : loadReactions(readerId, ids(live));
        Map<UUID, ChatThreadSummaryResponse> summariesByRootId = loadThreadSummaries(messages, deletedRootIds);
        Set<UUID> hereMentioningReader = loadHereMentionsOfReader(readerId, live);
        Set<UUID> readerThreadIds = loadReaderThreadIds(readerId, live);
        Map<UUID, DailyUpdateResponse> dailyUpdates = reader == null
                ? Map.of()
                : loadDailyUpdates(reader, live, previews.values());
        Map<UUID, PullRequestShareResponse> pullRequestShares = loadPullRequestShares(reader, organizationId, live,
                previews.values());
        Map<UUID, List<AttachmentResponse>> attachmentsById = loadAttachments(fileReader, reader == null,
                organizationId, live, previews.values());

        Map<UUID, User> knownUsers = new HashMap<>();
        Set<UUID> userIds = new LinkedHashSet<>();
        for (ChatMessage message : live) {
            knownUsers.put(message.getAuthor().getId(), message.getAuthor());
            userIds.add(message.getAuthor().getId());
            userIds.addAll(tokensById.get(message.getId()).userIds());
        }
        for (ChatReplyPreviewProjection preview : previews.values()) {
            if (!Boolean.TRUE.equals(preview.getDeleted())) {
                userIds.add(preview.getAuthorId());
            }
        }
        for (ChatTokens tokens : quotedTokens.values()) {
            userIds.addAll(tokens.userIds());
        }
        for (List<ChatReactionSummaryResponse> reactions : reactionsById.values()) {
            for (ChatReactionSummaryResponse reaction : reactions) {
                userIds.addAll(reaction.userIds());
            }
        }
        for (ChatThreadSummaryResponse summary : summariesByRootId.values()) {
            userIds.addAll(summary.memberPreviewIds());
        }
        Map<UUID, ChatUserResponse> users = resolveUsers(organizationId, userIds, knownUsers);

        Map<UUID, ChatMessageResponse> responses = new LinkedHashMap<>();
        for (ChatMessage message : messages) {
            if (message.isDeleted()) {
                responses.put(message.getId(), toPlaceholder(message.getId(), message.getOrganizationId(),
                        message.getChannelId(), message.getCreatedAt(), message.getUpdatedAt(),
                        summariesByRootId.get(message.getId()), users));
                continue;
            }
            UUID messageId = message.getId();
            ChatTokens tokens = tokensById.get(messageId);
            ChatReplyPreviewResponse replyTo = toReplyPreview(message.getReplyToMessageId(), previews, quotedTokens,
                    pageById, quotedWithTickets, dailyUpdates, pullRequestShares, attachmentsById, users);
            ChatThreadSummaryResponse thread = summariesByRootId.get(messageId);
            PullRequestShareResponse pullRequestShare = message.getPullRequestShareId() != null
                    ? pullRequestShares.get(message.getPullRequestShareId())
                    : null;
            List<ChatReactionSummaryResponse> reactions = reactionsById.getOrDefault(messageId, List.of());

            Set<UUID> messageUserIds = new LinkedHashSet<>();
            messageUserIds.add(message.getAuthor().getId());
            messageUserIds.addAll(tokens.userIds());
            if (replyTo != null && replyTo.authorId() != null) {
                messageUserIds.add(replyTo.authorId());
                messageUserIds.addAll(quotedTokens.get(replyTo.messageId()) == null
                        ? List.of()
                        : quotedTokens.get(replyTo.messageId()).userIds());
            }
            for (ChatReactionSummaryResponse reaction : reactions) {
                messageUserIds.addAll(reaction.userIds());
            }
            if (thread != null) {
                messageUserIds.addAll(thread.memberPreviewIds());
            }

            ChatMessageParts parts = new ChatMessageParts(
                    clientNonce,
                    replyTo,
                    tokens.userIds(),
                    mentionsReader(message, tokens, readerId, hereMentioningReader, readerThreadIds,
                            pullRequestShare),
                    repliesToReader(message, readerId, previews),
                    toTicketRefResponses(message.getTicketRefs(), openableTicketIds),
                    toLinkedTickets(typedKeysById.get(messageId), openableTicketsByKey),
                    attachmentsById.getOrDefault(messageId, List.of()),
                    reactions,
                    thread,
                    message.getDailyUpdateId() != null
                            ? dailyUpdates.get(message.getDailyUpdateId())
                            : null,
                    pullRequestShare,
                    subset(users, messageUserIds));
            responses.put(messageId, chatMessageMapper.toResponse(message, parts));
        }
        for (UUID rootId : deletedRootIds) {
            responses.put(rootId, toPlaceholder(rootId, organizationId, null, null, null,
                    summariesByRootId.get(rootId), users));
        }
        return responses;
    }

    private ChatMessageResponse toPlaceholder(UUID messageId, UUID organizationId, UUID channelId, Instant createdAt,
                                              Instant version, ChatThreadSummaryResponse thread,
                                              Map<UUID, ChatUserResponse> users) {
        ChatMessageParts parts = new ChatMessageParts(null, null, List.of(), false, false, List.of(), List.of(),
                List.of(), List.of(), thread, null, null,
                thread == null ? Map.of() : subset(users, thread.memberPreviewIds()));
        return chatMessageMapper.toPlaceholderResponse(messageId, organizationId, channelId, createdAt, version,
                parts);
    }

    /**
     * Tells whether a message of someone else mentions the reader: directly,
     * through everyone or here, or as a reviewer of the pull request share
     * the message stands for. In a thread, everyone and here reach only
     * the thread's current members. The author's own message never mentions
     * the author, so their own everyone, here or self-mention is not
     * highlighted for them. A reader-neutral copy mentions nobody.
     */
    private static boolean mentionsReader(ChatMessage message, ChatTokens tokens, UUID readerId,
                                          Set<UUID> hereMentioningReader, Set<UUID> readerThreadIds,
                                          PullRequestShareResponse pullRequestShare) {
        if (readerId == null || message.getAuthor().getId().equals(readerId)) {
            return false;
        }
        if (tokens.userIds().contains(readerId)) {
            return true;
        }
        if (pullRequestShare != null && isReviewer(pullRequestShare, readerId)) {
            return true;
        }
        boolean broadcast = message.isMentionsEveryone() || hereMentioningReader.contains(message.getId());
        if (message.getThread() == null) {
            return broadcast;
        }
        return broadcast && readerThreadIds.contains(message.getThread().getId());
    }

    /**
     * Tells whether a message of someone else replies to a live message of the
     * reader. Read from the reply previews the page loads anyway, so it costs
     * no query; a quoted message that was deleted or is gone notifies nobody.
     * A reader-neutral copy replies to nobody.
     */
    private static boolean repliesToReader(ChatMessage message, UUID readerId,
                                           Map<UUID, ChatReplyPreviewProjection> previews) {
        if (readerId == null || message.getReplyToMessageId() == null
                || message.getAuthor().getId().equals(readerId)) {
            return false;
        }
        ChatReplyPreviewProjection preview = previews.get(message.getReplyToMessageId());
        return preview != null
                && !Boolean.TRUE.equals(preview.getDeleted())
                && readerId.equals(preview.getAuthorId());
    }

    /**
     * Builds the preview of a quoted message. A deleted target shows neither
     * author, text nor files. A daily update shows the start of its summary
     * when the reader may read the update. A pull request share shows the
     * start of its summary, which reads the same for every reader; a share
     * that is gone shows no text. A daily update or a share has no files.
     */
    private ChatReplyPreviewResponse toReplyPreview(UUID replyToMessageId,
                                                    Map<UUID, ChatReplyPreviewProjection> previews,
                                                    Map<UUID, ChatTokens> quotedTokens,
                                                    Map<UUID, ChatMessage> pageById,
                                                    Map<UUID, ChatMessage> quotedWithTickets,
                                                    Map<UUID, DailyUpdateResponse> dailyUpdates,
                                                    Map<UUID, PullRequestShareResponse> pullRequestShares,
                                                    Map<UUID, List<AttachmentResponse>> attachmentsById,
                                                    Map<UUID, ChatUserResponse> users) {
        if (replyToMessageId == null) {
            return null;
        }
        ChatReplyPreviewProjection preview = previews.get(replyToMessageId);
        if (preview == null || Boolean.TRUE.equals(preview.getDeleted())) {
            return chatMessageMapper.toReplyPreviewResponse(replyToMessageId, true, null, ChatConstant.EMPTY_CONTENT,
                    List.of());
        }
        String text;
        if (preview.getDailyUpdateId() != null) {
            DailyUpdateResponse dailyUpdate = dailyUpdates.get(preview.getDailyUpdateId());
            text = dailyUpdate == null ? ChatConstant.EMPTY_CONTENT : dailyUpdate.summaryText();
        } else if (preview.getPullRequestShareId() != null) {
            PullRequestShareResponse pullRequestShare = pullRequestShares.get(preview.getPullRequestShareId());
            text = pullRequestShare == null ? ChatConstant.EMPTY_CONTENT : pullRequestShare.summaryText();
        } else {
            ChatMessage quoted = pageById.containsKey(replyToMessageId)
                    ? pageById.get(replyToMessageId)
                    : quotedWithTickets.get(replyToMessageId);
            text = quotedTokens.containsKey(replyToMessageId)
                    ? chatTokenParser.renderPlain(preview.getContent(), users, ticketRefsByTicketId(quoted))
                    : ChatConstant.EMPTY_CONTENT;
        }
        return chatMessageMapper.toReplyPreviewResponse(replyToMessageId, false, preview.getAuthorId(),
                chatTokenParser.truncate(text, ChatConstant.REPLY_SNIPPET_LENGTH, ChatConstant.REPLY_SNIPPET_SUFFIX),
                attachmentsById.getOrDefault(replyToMessageId, List.of()));
    }

    private List<ChatTicketRefResponse> toTicketRefResponses(List<ChatMessageTicketRef> ticketRefs,
                                                             Set<UUID> openableTicketIds) {
        return ticketRefs.stream()
                .map(ticketRef -> chatMessageMapper.toTicketRefResponse(ticketRef,
                        ticketRef.getTicketId() != null && openableTicketIds.contains(ticketRef.getTicketId())))
                .toList();
    }

    private List<ChatLinkedTicketResponse> toLinkedTickets(List<String> typedKeys,
                                                           Map<String, Ticket> openableTicketsByKey) {
        List<ChatLinkedTicketResponse> linked = new ArrayList<>();
        for (String typedKey : typedKeys) {
            Ticket ticket = openableTicketsByKey.get(chatTokenParser.normalizeTicketKey(typedKey));
            if (ticket != null) {
                linked.add(chatMessageMapper.toLinkedTicketResponse(typedKey, ticket));
            }
        }
        return linked;
    }

    // -------------Batched loads-----------//

    private Map<UUID, ChatReplyPreviewProjection> loadReplyPreviews(List<ChatMessage> live, UUID organizationId) {
        Set<UUID> replyIds = new LinkedHashSet<>();
        for (ChatMessage message : live) {
            if (message.getReplyToMessageId() != null) {
                replyIds.add(message.getReplyToMessageId());
            }
        }
        if (replyIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ChatReplyPreviewProjection> previews = new HashMap<>();
        for (ChatReplyPreviewProjection preview : chatMessageRepository.findReplyPreviewsByIds(replyIds,
                organizationId)) {
            previews.put(preview.getId(), preview);
        }
        return previews;
    }

    /**
     * Loads the quoted messages that are not on the page and reference a
     * ticket, for the ticket snapshots their snippet renders. Most quotes
     * reference none or are on the page, so this query rarely runs.
     */
    private Map<UUID, ChatMessage> loadQuotedMessagesWithTickets(Map<UUID, ChatTokens> quotedTokens,
                                                                 Map<UUID, ChatMessage> pageById) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (Map.Entry<UUID, ChatTokens> entry : quotedTokens.entrySet()) {
            if (!entry.getValue().ticketIds().isEmpty() && !pageById.containsKey(entry.getKey())) {
                ids.add(entry.getKey());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ChatMessage> quoted = new HashMap<>();
        for (ChatMessage message : chatMessageRepository.findAllById(ids)) {
            quoted.put(message.getId(), message);
        }
        return quoted;
    }

    /**
     * Returns the referenced tickets the reader can open: live tickets on
     * boards of the chat's organization the reader is a member of. One query
     * for the page; none for a reader-neutral copy, which opens no ticket.
     */
    private Set<UUID> loadOpenableTicketIds(UUID readerId, UUID organizationId, List<ChatMessage> live) {
        if (readerId == null) {
            return Set.of();
        }
        Set<UUID> ticketIds = new LinkedHashSet<>();
        for (ChatMessage message : live) {
            for (ChatMessageTicketRef ticketRef : message.getTicketRefs()) {
                if (ticketRef.getTicketId() != null) {
                    ticketIds.add(ticketRef.getTicketId());
                }
            }
        }
        if (ticketIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> openable = new HashSet<>();
        for (Ticket ticket : ticketQueryService.getAccessibleTickets(readerId, organizationId, new ArrayList<>(ticketIds))) {
            openable.add(ticket.getId());
        }
        return openable;
    }

    /**
     * Resolves the typed ticket keys of the page among the tickets the reader
     * can open in the chat's organization. Keys are upper-cased first, as
     * stored, because the lookup matches exactly. One query for the page; none
     * for a reader-neutral copy.
     */
    private Map<String, Ticket> loadOpenableTicketsByKey(UUID readerId, UUID organizationId,
                                                         Collection<List<String>> typedKeys) {
        if (readerId == null) {
            return Map.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        for (List<String> messageKeys : typedKeys) {
            for (String typedKey : messageKeys) {
                keys.add(chatTokenParser.normalizeTicketKey(typedKey));
            }
        }
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<String, Ticket> ticketsByKey = new HashMap<>();
        for (Ticket ticket : ticketQueryService.getAccessibleTicketsByKeys(readerId, organizationId, keys)) {
            ticketsByKey.put(ticket.getKey(), ticket);
        }
        return ticketsByKey;
    }

    private Map<UUID, List<ChatReactionSummaryResponse>> loadReactions(UUID readerId, Set<UUID> liveIds) {
        if (liveIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<ChatReactionSummaryResponse>> reactionsById = new HashMap<>();
        for (ChatReactionSummaryProjection reaction : chatMessageRepository.findReactionSummaries(liveIds, readerId)) {
            reactionsById.computeIfAbsent(reaction.getMessageId(), messageId -> new ArrayList<>())
                    .add(chatMessageMapper.toReactionSummaryResponse(reaction));
        }
        return reactionsById;
    }

    /**
     * Loads the reactions for a reader-neutral copy, marking none as the
     * reader's. The query is asked with the author's ID only because it needs
     * a reader, and that flag is dropped.
     */
    private Map<UUID, List<ChatReactionSummaryResponse>> loadNeutralReactions(List<ChatMessage> live) {
        if (live.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<ChatReactionSummaryResponse>> reactionsById = new HashMap<>();
        for (ChatReactionSummaryProjection reaction : chatMessageRepository.findReactionSummaries(ids(live),
                live.getFirst().getAuthor().getId())) {
            reactionsById.computeIfAbsent(reaction.getMessageId(), messageId -> new ArrayList<>())
                    .add(chatMessageMapper.toNeutralReactionSummaryResponse(reaction));
        }
        return reactionsById;
    }

    /**
     * Loads the thread summaries of the channel messages and of the deleted
     * roots; a thread message cannot host a thread.
     */
    private Map<UUID, ChatThreadSummaryResponse> loadThreadSummaries(List<ChatMessage> messages,
                                                                     List<UUID> deletedRootIds) {
        Set<UUID> rootIds = new LinkedHashSet<>(deletedRootIds);
        for (ChatMessage message : messages) {
            if (message.getThread() == null) {
                rootIds.add(message.getId());
            }
        }
        if (rootIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ChatThreadSummaryResponse> summaries = new HashMap<>();
        for (ChatThreadSummaryProjection summary : chatMessageRepository.findThreadSummariesByRootIds(rootIds)) {
            summaries.put(summary.getRootMessageId(), chatMessageMapper.toThreadSummaryResponse(summary));
        }
        return summaries;
    }

    /**
     * Returns the messages with a here mention that reached the reader. A
     * direct mention is read from the text, so only here mentions are asked for.
     */
    private Set<UUID> loadHereMentionsOfReader(UUID readerId, List<ChatMessage> live) {
        if (readerId == null) {
            return Set.of();
        }
        Set<UUID> hereIds = new LinkedHashSet<>();
        for (ChatMessage message : live) {
            if (message.isMentionsHere()) {
                hereIds.add(message.getId());
            }
        }
        if (hereIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(chatMessageMentionRepository.findMessageIdsMentioningUser(readerId, hereIds));
    }

    /**
     * Returns the threads of the page the reader is a member of, needed only
     * for thread messages that mention everyone or here.
     */
    private Set<UUID> loadReaderThreadIds(UUID readerId, List<ChatMessage> live) {
        if (readerId == null) {
            return Set.of();
        }
        Set<UUID> threadIds = new LinkedHashSet<>();
        for (ChatMessage message : live) {
            if (message.getThread() != null && (message.isMentionsEveryone() || message.isMentionsHere())) {
                threadIds.add(message.getThread().getId());
            }
        }
        if (threadIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(chatThreadMemberRepository.findThreadIdsByUserIdAndThreadIds(readerId, threadIds));
    }

    /**
     * Loads the daily updates of the page's daily update entries and of the
     * quoted ones. An update the reader may not read is absent from the
     * result, and its message is then shown without the card.
     */
    private Map<UUID, DailyUpdateResponse> loadDailyUpdates(User reader, List<ChatMessage> live,
                                                            Collection<ChatReplyPreviewProjection> previews) {
        Set<UUID> dailyUpdateIds = new LinkedHashSet<>();
        for (ChatMessage message : live) {
            if (message.getDailyUpdateId() != null) {
                dailyUpdateIds.add(message.getDailyUpdateId());
            }
        }
        for (ChatReplyPreviewProjection preview : previews) {
            if (!Boolean.TRUE.equals(preview.getDeleted()) && preview.getDailyUpdateId() != null) {
                dailyUpdateIds.add(preview.getDailyUpdateId());
            }
        }
        if (dailyUpdateIds.isEmpty()) {
            return Map.of();
        }
        return dailyUpdateQueryService.getDailyUpdatesByIds(reader, dailyUpdateIds);
    }

    /**
     * Loads the pull request shares of the page's share entries and of the
     * quoted ones in one call, each with the reader's linked ticket. A share
     * that was deleted is absent from the result, and its entry is then shown
     * without the card. The reader-neutral copy holds no share entry, so it
     * loads only quoted shares, whose summary reads the same for every
     * reader; without a reader no ticket is linked.
     */
    private Map<UUID, PullRequestShareResponse> loadPullRequestShares(User reader, UUID organizationId,
                                                                      List<ChatMessage> live,
                                                                      Collection<ChatReplyPreviewProjection> previews) {
        Set<UUID> shareIds = new LinkedHashSet<>();
        for (ChatMessage message : live) {
            if (message.getPullRequestShareId() != null) {
                shareIds.add(message.getPullRequestShareId());
            }
        }
        for (ChatReplyPreviewProjection preview : previews) {
            if (!Boolean.TRUE.equals(preview.getDeleted()) && preview.getPullRequestShareId() != null) {
                shareIds.add(preview.getPullRequestShareId());
            }
        }
        if (shareIds.isEmpty()) {
            return Map.of();
        }
        return pullRequestShareQueryService.getSharesByIds(reader, organizationId, shareIds);
    }

    /**
     * Loads the files of the page's live messages and of the messages they
     * quote in one storage call. Only live messages of the organization that
     * stand for neither a daily update nor a pull request share are asked for
     * and granted: the storage call refuses the whole page when one message
     * is refused, and a deleted message, a daily update or a share entry has
     * no files to show. The quoted messages come from the reply previews,
     * which are read within the organization only.
     *
     * Granting a quoted message is safe because a reply is accepted only in
     * the channel and conversation of the message it quotes, so whoever reads
     * the reply reads the quoted message's channel as well.
     *
     * A reader's request has had the reader's organization role checked
     * before it reaches the chat services. The reader-neutral copy is built
     * outside any request, for the author, so its grant checks the author's
     * membership itself.
     */
    private Map<UUID, List<AttachmentResponse>> loadAttachments(User reader, boolean outsideRequest,
                                                                UUID organizationId, List<ChatMessage> live,
                                                                Collection<ChatReplyPreviewProjection> previews) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (ChatMessage message : live) {
            if (message.getDailyUpdateId() == null && message.getPullRequestShareId() == null
                    && organizationId.equals(message.getOrganizationId())) {
                ids.add(message.getId());
            }
        }
        for (ChatReplyPreviewProjection preview : previews) {
            if (isLiveTextMessage(preview)) {
                ids.add(preview.getId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<UUID> messageIds = List.copyOf(ids);
        Supplier<Map<UUID, List<AttachmentResponse>>> read = () -> attachmentQueryService
                .listAttachmentsByEntities(reader, organizationId, StorageEntityType.CHAT_MESSAGE, messageIds);
        return outsideRequest
                ? resourceAccessQueryService.withCheckedReadGrant(reader, ResourceAccessType.CHAT_MESSAGE,
                        organizationId, messageIds, read)
                : resourceAccessQueryService.withReadGrant(reader, ResourceAccessType.CHAT_MESSAGE, messageIds, read);
    }

    /**
     * Resolves users with at most one member query and one user query.
     * Users already loaded, such as authors, are not loaded again.
     */
    private Map<UUID, ChatUserResponse> resolveUsers(UUID organizationId, Collection<UUID> userIds,
                                                     Map<UUID, User> knownUsers) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, OrganizationMemberSummaryProjection> members = new HashMap<>();
        for (OrganizationMemberSummaryProjection member
                : organizationMemberQueryService.listActiveMemberSummaries(organizationId, Instant.now())) {
            members.put(member.getUserId(), member);
        }
        Map<UUID, User> usersById = new HashMap<>(knownUsers);
        List<UUID> unloaded = userIds.stream()
                .distinct()
                .filter(userId -> !members.containsKey(userId) && !usersById.containsKey(userId))
                .toList();
        if (!unloaded.isEmpty()) {
            for (User user : userQueryService.getUserEntitiesByIds(unloaded)) {
                usersById.put(user.getId(), user);
            }
        }
        Map<UUID, ChatUserResponse> users = new LinkedHashMap<>();
        for (UUID userId : userIds) {
            if (members.containsKey(userId)) {
                users.put(userId, chatMessageMapper.toMemberUserResponse(members.get(userId)));
            } else if (usersById.containsKey(userId)) {
                users.put(userId, chatMessageMapper.toUserResponse(usersById.get(userId), false));
            } else {
                users.put(userId, chatMessageMapper.toUnknownUserResponse(userId));
            }
        }
        return users;
    }

    // -------------Building blocks-----------//

    /**
     * Collects the users a here mention reaches: the members online, without
     * the users mentioned directly and without the author.
     */
    private static Set<UUID> collectHereUserIds(ChatTokens tokens, Collection<UUID> hereUserIds, UUID authorId) {
        Set<UUID> hereIds = new LinkedHashSet<>();
        for (UUID userId : hereUserIds) {
            if (!userId.equals(authorId) && !tokens.userIds().contains(userId)) {
                hereIds.add(userId);
            }
        }
        return hereIds;
    }

    /**
     * Replaces the mention rows: direct mentions in token order, then the
     * here users. A user who keeps a mention keeps the row.
     */
    private void applyMentions(ChatMessage message, ChatTokens tokens, Set<UUID> hereIds) {
        Map<UUID, ChatMessageMention> stored = new HashMap<>();
        for (ChatMessageMention mention : message.getMentions()) {
            stored.put(mention.getUserId(), mention);
        }
        List<ChatMessageMention> mentions = new ArrayList<>();
        for (UUID userId : tokens.userIds()) {
            mentions.add(keepOrCreateMention(message, stored.get(userId), userId, false));
        }
        for (UUID userId : hereIds) {
            mentions.add(keepOrCreateMention(message, stored.get(userId), userId, true));
        }
        // The managed collection is mutated, never replaced, so orphan removal
        // deletes the rows that were left out.
        message.getMentions().clear();
        message.getMentions().addAll(mentions);
    }

    private ChatMessageMention keepOrCreateMention(ChatMessage message, ChatMessageMention stored, UUID userId,
                                                   boolean viaHere) {
        if (stored != null) {
            stored.setViaHere(viaHere);
            return stored;
        }
        ChatMessageMention mention = chatMessageMapper.toMentionEntity(userId, viaHere);
        mention.setMessage(message);
        return mention;
    }

    /**
     * Replaces the ticket references in token order. A ticket that stays
     * keeps its row with a new snapshot and position.
     */
    private void applyTicketRefs(ChatMessage message, ChatTokens tokens, Map<UUID, Ticket> ticketsById) {
        Map<UUID, ChatMessageTicketRef> stored = new HashMap<>();
        for (ChatMessageTicketRef ticketRef : message.getTicketRefs()) {
            if (ticketRef.getTicketId() != null) {
                stored.put(ticketRef.getTicketId(), ticketRef);
            }
        }
        List<ChatMessageTicketRef> ticketRefs = new ArrayList<>();
        List<UUID> ticketIds = tokens.ticketIds();
        for (int position = 0; position < ticketIds.size(); position++) {
            Ticket ticket = Objects.requireNonNull(ticketsById.get(ticketIds.get(position)));
            ChatMessageTicketRef ticketRef = stored.get(ticket.getId());
            if (ticketRef != null) {
                chatMessageMapper.updateTicketRefEntity(ticketRef, ticket, position);
            } else {
                ticketRef = chatMessageMapper.toTicketRefEntity(ticket, position);
                ticketRef.setMessage(message);
            }
            ticketRefs.add(ticketRef);
        }
        message.getTicketRefs().clear();
        message.getTicketRefs().addAll(ticketRefs);
    }

    private static Map<UUID, ChatMessageTicketRef> ticketRefsByTicketId(ChatMessage message) {
        if (message == null) {
            return Map.of();
        }
        Map<UUID, ChatMessageTicketRef> ticketRefs = new HashMap<>();
        for (ChatMessageTicketRef ticketRef : message.getTicketRefs()) {
            if (ticketRef.getTicketId() != null) {
                ticketRefs.put(ticketRef.getTicketId(), ticketRef);
            }
        }
        return ticketRefs;
    }

    /**
     * Tells whether a message's copy differs per reader in ways its stored
     * row shows: ticket references or typed ticket keys, a here mention, a
     * daily update entry, or a pull request share entry. A quoted daily
     * update entry is told by the loaded reply preview instead.
     */
    private boolean dependsOnReader(ChatMessage message) {
        return message.isMentionsHere()
                || message.getDailyUpdateId() != null
                || message.getPullRequestShareId() != null
                || !message.getTicketRefs().isEmpty()
                || !chatTokenParser.parse(message.getContent()).ticketIds().isEmpty()
                || !chatTokenParser.extractTicketKeys(message.getContent()).isEmpty();
    }

    private static boolean quotesDailyUpdate(List<ChatMessage> live,
                                             Map<UUID, ChatReplyPreviewProjection> previews) {
        for (ChatMessage message : live) {
            if (message.getReplyToMessageId() == null) {
                continue;
            }
            ChatReplyPreviewProjection preview = previews.get(message.getReplyToMessageId());
            if (preview != null && !Boolean.TRUE.equals(preview.getDeleted()) && preview.getDailyUpdateId() != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean isReviewer(PullRequestShareResponse pullRequestShare, UUID userId) {
        for (PullRequestShareReviewerResponse reviewer : pullRequestShare.reviewers()) {
            if (userId.equals(reviewer.userId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLiveTextMessage(ChatReplyPreviewProjection preview) {
        return !Boolean.TRUE.equals(preview.getDeleted())
                && preview.getDailyUpdateId() == null
                && preview.getPullRequestShareId() == null;
    }

    private static Map<UUID, ChatUserResponse> subset(Map<UUID, ChatUserResponse> users, Collection<UUID> userIds) {
        Map<UUID, ChatUserResponse> subset = new LinkedHashMap<>();
        for (UUID userId : userIds) {
            ChatUserResponse user = users.get(userId);
            if (user != null) {
                subset.put(userId, user);
            }
        }
        return subset;
    }

    private static Set<UUID> ids(Collection<ChatMessage> messages) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (ChatMessage message : messages) {
            ids.add(message.getId());
        }
        return ids;
    }
}
