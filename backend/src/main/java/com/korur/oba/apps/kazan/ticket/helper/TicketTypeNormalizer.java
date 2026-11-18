package com.korur.oba.apps.kazan.ticket.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kazan.ticket.constant.service.TicketServiceLogConstant;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketType;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Single enforcement point for the TASK type alias.
 *
 * TASK was renamed to FEATURE, and the database enum no longer holds TASK, so a
 * ticket saved with TASK would fail at the database. Older CLIs, agent scripts and
 * AI suggestions still send TASK, so instead of rejecting the write, every ticket
 * write path calls this component immediately before persisting and the type is
 * converted to FEATURE.
 *
 * @see com.korur.oba.apps.kazan.ticket.service.impl.TicketServiceImpl
 * @see TicketStatusNormalizer
 */
@Component
@RequiredArgsConstructor
public class TicketTypeNormalizer {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(TicketTypeNormalizer.class);

    private final LoggingService loggingService;

    /**
     * Converts the deprecated TASK type to FEATURE.
     *
     * The conversion is applied in place on the passed entity, so the caller must
     * invoke this before saving. Any other type is left unchanged.
     *
     * @param ticket the ticket to normalize, ignored when null
     */
    @SuppressWarnings("deprecation")
    public void normalizeType(Ticket ticket) {
        if (ticket == null || ticket.getType() != TicketType.TASK) {
            return;
        }

        TicketType currentType = ticket.getType();
        ticket.setType(TicketType.FEATURE);

        loggingService.info(LOG_PREFIX, LogLayer.HELPER,
                TicketServiceLogConstant.TICKET_TYPE_CONVERTED_FROM_DEPRECATED_ALIAS,
                ticket.getKey(), currentType, TicketType.FEATURE);
    }
}
