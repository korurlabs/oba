package com.korur.oba.connect.azure.scheduler;

// 1. Framework-level
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.constant.scheduler.AzureConnectionCheckSchedulerLogConstant;
import com.korur.oba.connect.azure.service.AzureConnectionCheckService;
import com.korur.oba.connect.azure.service.AzureConnectionQueryService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.time.BusinessCalendar;

/**
 * Re-checks every CONNECTED and FAILING Azure connection once a day, so a trust an organization
 * revoked in Azure shows up without anyone pressing Verify.
 *
 * It only decides when checking happens and walks the connections; it reads no Azure data and
 * writes no row. It calls {@link AzureConnectionCheckService#checkConnection} once per connection
 * and catches each connection's failure, so one connection with an Azure error or a broken row
 * cannot stop the check of the others.
 */
@Component
@RequiredArgsConstructor
public class AzureConnectionCheckScheduler {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AzureConnectionCheckScheduler.class);

    private final AzureConnectionQueryService connectionQueryService;
    private final AzureConnectionCheckService connectionCheckService;
    private final LoggingService loggingService;

    /**
     * Checks every checkable connection, each in its own transaction.
     */
    @Scheduled(cron = "${scheduling.azure-connection-check.cron:0 30 4 * * *}", zone = BusinessCalendar.SCHEDULER_ZONE_ID)
    public void checkConnections() {
        List<UUID> connectionIds = connectionQueryService.listCheckableConnectionIds();
        if (connectionIds.isEmpty()) {
            return;
        }

        loggingService.info(LOG_PREFIX, LogLayer.SCHEDULER, AzureConnectionCheckSchedulerLogConstant.CHECK_RUN_STARTED,
                connectionIds.size());

        int failed = 0;
        for (UUID connectionId : connectionIds) {
            try {
                connectionCheckService.checkConnection(connectionId);
            } catch (RuntimeException exception) {
                // An Azure refusal is already stored by the check; what arrives here is unexpected,
                // so it is logged with its stack trace and the next connection is checked.
                failed++;
                loggingService.error(LOG_PREFIX, LogLayer.SCHEDULER,
                        AzureConnectionCheckSchedulerLogConstant.CHECK_CONNECTION_FAILED, exception, connectionId);
            }
        }

        loggingService.info(LOG_PREFIX, LogLayer.SCHEDULER,
                AzureConnectionCheckSchedulerLogConstant.CHECK_RUN_COMPLETED, connectionIds.size(), failed);
    }
}
