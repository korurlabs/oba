package com.korur.oba.connect.push.scheduler;

// 1. Framework-level
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 4. Internal
import com.korur.oba.connect.push.client.ExpoPushClient;
import com.korur.oba.connect.push.constant.PushConstant;
import com.korur.oba.connect.push.dto.expo.ExpoPushReceipt;
import com.korur.oba.connect.push.helper.PushTicketTracker;
import com.korur.oba.connect.push.service.PushDeviceService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

import static com.korur.oba.connect.push.constant.scheduler.PushReceiptSchedulerLogConstant.*;

/**
 * Reads, every 15 minutes, the Expo receipts of the tickets {@link PushTicketTracker} holds, and
 * removes the devices whose receipt says DeviceNotRegistered: Apple or Google refused the token after
 * Expo accepted the push, which happens when the app was removed.
 *
 * A run stops at the first chunk Expo does not answer, so an Expo outage holds the shared scheduler
 * thread for one retried call only; the remaining tickets wait for the next run. Tickets older than
 * Expo keeps receipts are forgotten.
 */
@Component
@RequiredArgsConstructor
public class PushReceiptScheduler {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PushReceiptScheduler.class);

    private final ExpoPushClient expoPushClient;
    private final PushTicketTracker pushTicketTracker;
    private final PushDeviceService pushDeviceService;
    private final LoggingService loggingService;
    private final Clock clock;

    /**
     * Checks the receipts of the tickets that are due.
     */
    @Scheduled(fixedDelayString = PushConstant.RECEIPT_POLL_INTERVAL,
            initialDelayString = PushConstant.RECEIPT_POLL_INTERVAL)
    public void checkReceipts() {
        if (!expoPushClient.isEnabled()) {
            return;
        }
        Instant now = Instant.now(clock);
        int expired = pushTicketTracker.expire(now);
        Map<String, String> due = pushTicketTracker.due(now);
        if (due.isEmpty()) {
            return;
        }

        List<String> ticketIds = new ArrayList<>(due.keySet());
        Set<String> deadTokens = new LinkedHashSet<>();
        int received = 0;

        for (int start = 0; start < ticketIds.size(); start += PushConstant.EXPO_RECEIPT_CHUNK_SIZE) {
            List<String> chunk = ticketIds.subList(start,
                    Math.min(start + PushConstant.EXPO_RECEIPT_CHUNK_SIZE, ticketIds.size()));
            Map<String, ExpoPushReceipt> receipts;
            try {
                receipts = expoPushClient.getReceipts(chunk);
            } catch (RuntimeException exception) {
                loggingService.error(LOG_PREFIX, LogLayer.SCHEDULER, RECEIPT_CHUNK_FAILED, exception,
                        ticketIds.size() - start);
                break;
            }
            List<String> resolved = new ArrayList<>();
            for (String ticketId : chunk) {
                ExpoPushReceipt receipt = receipts.get(ticketId);
                if (receipt == null) {
                    continue;
                }
                resolved.add(ticketId);
                if (PushConstant.EXPO_STATUS_ERROR.equals(receipt.status())) {
                    String error = receipt.details() == null ? null : receipt.details().error();
                    if (PushConstant.EXPO_ERROR_DEVICE_NOT_REGISTERED.equals(error)) {
                        deadTokens.add(due.get(ticketId));
                    } else {
                        loggingService.warn(LOG_PREFIX, LogLayer.SCHEDULER, RECEIPT_ERROR, ticketId, error);
                    }
                }
            }
            received += resolved.size();
            pushTicketTracker.forget(resolved);
        }

        removeDeadTokens(deadTokens);
        loggingService.info(LOG_PREFIX, LogLayer.SCHEDULER, RECEIPTS_CHECKED, due.size(), received, deadTokens.size(),
                expired);
    }

    private void removeDeadTokens(Set<String> deadTokens) {
        if (deadTokens.isEmpty()) {
            return;
        }
        try {
            pushDeviceService.removeTokens(new ArrayList<>(deadTokens));
        } catch (RuntimeException exception) {
            // The receipts are already forgotten; the token's next push reports it again.
            loggingService.error(LOG_PREFIX, LogLayer.SCHEDULER, TOKEN_REMOVAL_FAILED, exception, deadTokens.size());
        }
    }
}
