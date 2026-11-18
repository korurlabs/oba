package com.korur.oba.connect.push.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.client.ExpoPushClient;
import com.korur.oba.connect.push.constant.PushConstant;
import com.korur.oba.connect.push.dto.PushRequest;
import com.korur.oba.connect.push.dto.expo.ExpoPushMessage;
import com.korur.oba.connect.push.dto.expo.ExpoPushTicket;
import com.korur.oba.connect.push.dto.response.PushPreferenceResponse;
import com.korur.oba.connect.push.entity.PushDevice;
import com.korur.oba.connect.push.enums.PushKind;
import com.korur.oba.connect.push.helper.PushMessageHelper;
import com.korur.oba.connect.push.helper.PushTicketTracker;
import com.korur.oba.connect.push.service.PushDeviceQueryService;
import com.korur.oba.connect.push.service.PushDeviceService;
import com.korur.oba.connect.push.service.PushDispatchService;
import com.korur.oba.connect.push.service.PushPreferenceQueryService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

import static com.korur.oba.connect.push.constant.service.PushDispatchServiceLogConstant.*;

/**
 * Implements push dispatch: picks the recipients the switches allow, builds one message per device,
 * sends them to Expo in chunks and acts on the tickets.
 *
 * It holds no transaction of its own: the device and switch reads are short read-only transactions,
 * and no connection is held while Expo is called.
 */
@Service
@RequiredArgsConstructor
public class PushDispatchServiceImpl implements PushDispatchService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PushDispatchServiceImpl.class);

    private final ExpoPushClient expoPushClient;
    private final PushDeviceQueryService pushDeviceQueryService;
    private final PushPreferenceQueryService pushPreferenceQueryService;
    private final PushDeviceService pushDeviceService;
    private final PushMessageHelper pushMessageHelper;
    private final PushTicketTracker pushTicketTracker;
    private final LoggingService loggingService;
    private final Clock clock;

    @Override
    public void dispatch(PushRequest request) {
        if (request == null || request.recipients() == null || request.recipients().isEmpty()) {
            return;
        }
        if (!expoPushClient.isEnabled()) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, DISPATCH_SKIPPED_DISABLED,
                    request.organizationId(), request.recipients().size());
            return;
        }

        Map<UUID, PushKind> candidates = candidates(request);
        Map<UUID, PushPreferenceResponse> preferences = preferencesOf(candidates);
        Map<UUID, PushKind> eligible = new LinkedHashMap<>();
        candidates.forEach((userId, kind) -> {
            if (pushMessageHelper.isAllowed(kind, preferences.get(userId))) {
                eligible.put(userId, kind);
            }
        });

        List<PushDevice> devices = eligible.isEmpty()
                ? List.of()
                : pushDeviceQueryService.getDevicesByUserIds(eligible.keySet());
        Instant sentAt = Instant.now(clock);
        List<OutgoingPush> outgoing = devices.stream()
                .filter(device -> eligible.containsKey(device.getUserId()))
                .map(device -> new OutgoingPush(device.getUserId(), device.getExpoPushToken(),
                        pushMessageHelper.toMessage(request, device.getUserId(), eligible.get(device.getUserId()),
                                preferences.get(device.getUserId()), device.getExpoPushToken(), sentAt)))
                .toList();

        SendOutcome outcome = send(request, outgoing, sentAt);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, DISPATCH_COMPLETED, request.organizationId(),
                request.recipients().size(), eligible.size(), outgoing.size(), outcome.accepted(), outcome.failed());
    }

    /**
     * The recipients before their switches: no null entries, and without the actor unless the push
     * is the actor's own READ withdrawal.
     */
    private Map<UUID, PushKind> candidates(PushRequest request) {
        Map<UUID, PushKind> candidates = new LinkedHashMap<>();
        request.recipients().forEach((userId, kind) -> {
            if (userId == null || kind == null) {
                return;
            }
            if (kind != PushKind.READ && Objects.equals(userId, request.actorId())) {
                return;
            }
            candidates.put(userId, kind);
        });
        return candidates;
    }

    private Map<UUID, PushPreferenceResponse> preferencesOf(Map<UUID, PushKind> candidates) {
        Set<UUID> userIds = new LinkedHashSet<>();
        candidates.forEach((userId, kind) -> {
            if (kind != PushKind.READ) {
                userIds.add(userId);
            }
        });
        return userIds.isEmpty() ? Map.of() : pushPreferenceQueryService.getPreferencesByUserIds(userIds);
    }

    private SendOutcome send(PushRequest request, List<OutgoingPush> outgoing, Instant sentAt) {
        Map<String, String> ticketTokens = new LinkedHashMap<>();
        Set<String> deadTokens = new LinkedHashSet<>();
        int failed = 0;

        for (int start = 0; start < outgoing.size(); start += PushConstant.EXPO_SEND_CHUNK_SIZE) {
            List<OutgoingPush> chunk = outgoing.subList(start,
                    Math.min(start + PushConstant.EXPO_SEND_CHUNK_SIZE, outgoing.size()));
            List<ExpoPushTicket> tickets;
            try {
                tickets = expoPushClient.send(chunk.stream().map(OutgoingPush::message).toList());
            } catch (RuntimeException exception) {
                // The retry module has recorded the dead event; the other chunks still go out, and the
                // unread counts stay the source of truth for what the recipients missed.
                loggingService.error(LOG_PREFIX, LogLayer.SERVICE, CHUNK_FAILED, exception,
                        request.organizationId(), chunk.size());
                failed += chunk.size();
                continue;
            }
            if (tickets.size() != chunk.size()) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, TICKET_COUNT_MISMATCH, chunk.size(), tickets.size());
            }
            for (int index = 0; index < Math.min(tickets.size(), chunk.size()); index++) {
                OutgoingPush push = chunk.get(index);
                ExpoPushTicket ticket = tickets.get(index);
                if (PushConstant.EXPO_STATUS_OK.equals(ticket.status())) {
                    if (ticket.id() != null) {
                        ticketTokens.put(ticket.id(), push.token());
                    }
                    continue;
                }
                failed++;
                String error = ticket.details() == null ? null : ticket.details().error();
                if (PushConstant.EXPO_ERROR_DEVICE_NOT_REGISTERED.equals(error)) {
                    deadTokens.add(push.token());
                } else {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, TICKET_ERROR, request.organizationId(),
                            push.userId(), error);
                }
            }
        }

        pushTicketTracker.track(ticketTokens, sentAt);
        removeDeadTokens(deadTokens);
        return new SendOutcome(ticketTokens.size(), failed);
    }

    private void removeDeadTokens(Set<String> deadTokens) {
        if (deadTokens.isEmpty()) {
            return;
        }
        try {
            pushDeviceService.removeTokens(new ArrayList<>(deadTokens));
        } catch (RuntimeException exception) {
            // The pushes are already sent; a dead token left behind is reported again by its next push.
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, TOKEN_REMOVAL_FAILED, exception, deadTokens.size());
        }
    }

    private record OutgoingPush(UUID userId, String token, ExpoPushMessage message) {

        /**
         * Keeps the token out of every log line that prints this record.
         */
        @Override
        public String toString() {
            return "OutgoingPush[userId=" + userId + "]";
        }
    }

    private record SendOutcome(int accepted, int failed) {
    }
}
