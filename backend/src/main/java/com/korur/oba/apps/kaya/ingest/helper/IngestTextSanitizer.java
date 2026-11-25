package com.korur.oba.apps.kaya.ingest.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.ingest.constant.IngestConstant;
import com.korur.oba.apps.kaya.ingest.constant.helper.IngestTextSanitizerLogConstant;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestBatchRequest;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestHandoffRequest;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestPlanRequest;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;

/**
 * Makes the text of a recorder batch storable before any other step reads the batch. PostgreSQL
 * holds no U+0000 in text, varchar or jsonb and refuses the whole statement that carries one, so a
 * single such character anywhere in a batch failed the batch, and the recorder sent it again on
 * every retry. The batch is accepted rather than refused, because a refused batch would stop the
 * session from recording just the same.
 *
 * A handoff or plan snapshot whose content holds U+0000 is a binary file the recorder read as
 * text, such as an image in a handoff folder. It is left out with a warning that names the
 * session, the path and the size, and the rest of the batch is stored. Replacing the character
 * instead would store the file as text that means nothing. The batch response has no field that
 * lists what was left out, so the warning is the record of it.
 *
 * In every other text U+0000 becomes U+FFFD, the replacement character, which keeps the event or
 * field and shows where the character was. That covers the summary, the machine and account
 * fields, and the keys and values of the recorder's free-form events, which are stored as jsonb.
 * The replacement runs over the JSON form of the batch, which holds every text of the request
 * whatever record, list or map it sits in, so a field that a later change adds is covered too. A
 * batch without U+0000, the usual case, is returned unchanged.
 */
@Component
@RequiredArgsConstructor
public class IngestTextSanitizer {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(IngestTextSanitizer.class);

    private final ObjectMapper objectMapper;
    private final LoggingService loggingService;

    /**
     * The batch as Kaya can store it: without its binary snapshots, and with U+FFFD for U+0000 in
     * every other text.
     *
     * @param principal the machine token that sent the batch, named in the log
     * @param request   the batch as received
     * @return the batch to ingest; the received batch itself when it holds no U+0000
     */
    public IngestBatchRequest sanitize(MachinePrincipal principal, IngestBatchRequest request) {
        IngestBatchRequest textOnly = withoutBinarySnapshots(principal, request);
        JsonNode received = objectMapper.valueToTree(textOnly);
        JsonNode storable = storable(received);
        if (storable == received) {
            return textOnly;
        }
        loggingService.info(LOG_PREFIX, LogLayer.HELPER, IngestTextSanitizerLogConstant.NUL_REPLACED,
                request.sessionId(), principal.machineId());
        try {
            return objectMapper.treeToValue(storable, IngestBatchRequest.class);
        } catch (JsonProcessingException e) {
            // The tree was written from a batch, so this means the mapper cannot read what it wrote.
            loggingService.error(LOG_PREFIX, LogLayer.HELPER, IngestTextSanitizerLogConstant.BATCH_REBUILD_FAILED, e,
                    request.sessionId());
            throw new IllegalStateException(IngestTextSanitizerLogConstant.BATCH_REBUILD_FAILED, e);
        }
    }

    /**
     * The batch without the handoff and plan snapshots whose content holds U+0000, each one logged.
     */
    private IngestBatchRequest withoutBinarySnapshots(MachinePrincipal principal, IngestBatchRequest request) {
        String sessionId = request.sessionId();
        List<IngestHandoffRequest> handoffs = textSnapshots(request.handoffs(), IngestHandoffRequest::content,
                handoff -> loggingService.warn(LOG_PREFIX, LogLayer.HELPER, IngestTextSanitizerLogConstant.HANDOFF_SKIPPED,
                        handoff.path(), sessionId, principal.machineId(), byteCount(handoff.content())));
        List<IngestPlanRequest> plans = textSnapshots(request.plans(), IngestPlanRequest::content,
                plan -> loggingService.warn(LOG_PREFIX, LogLayer.HELPER, IngestTextSanitizerLogConstant.PLAN_SKIPPED,
                        plan.kind(), plan.path(), sessionId, principal.machineId(), byteCount(plan.content())));
        if (handoffs == request.handoffs() && plans == request.plans()) {
            return request;
        }
        return new IngestBatchRequest(sessionId, request.repository(), request.machine(), request.summary(),
                request.events(), handoffs, plans, request.usage());
    }

    /**
     * The snapshots whose content is text, in their order, or the list itself when every one is.
     * Each snapshot left out is passed to skip.
     */
    private static <T> List<T> textSnapshots(List<T> snapshots, Function<T, String> contentOf, Consumer<T> skip) {
        if (snapshots == null || snapshots.stream().map(contentOf).noneMatch(IngestTextSanitizer::holdsNul)) {
            return snapshots;
        }
        List<T> text = new ArrayList<>(snapshots.size());
        for (T snapshot : snapshots) {
            if (holdsNul(contentOf.apply(snapshot))) {
                skip.accept(snapshot);
            } else {
                text.add(snapshot);
            }
        }
        return text;
    }

    /**
     * The node with U+0000 replaced in every text and field name it holds; the node itself when it
     * holds none.
     */
    private JsonNode storable(JsonNode node) {
        if (node.isTextual()) {
            String text = node.textValue();
            String storableText = storableText(text);
            return storableText == text ? node : TextNode.valueOf(storableText);
        }
        if (node.isArray()) {
            ArrayNode copy = objectMapper.createArrayNode();
            boolean changed = false;
            for (JsonNode element : node) {
                JsonNode storableElement = storable(element);
                changed |= storableElement != element;
                copy.add(storableElement);
            }
            return changed ? copy : node;
        }
        if (node.isObject()) {
            ObjectNode copy = objectMapper.createObjectNode();
            boolean changed = false;
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String name = storableText(field.getKey());
                JsonNode value = storable(field.getValue());
                changed |= name != field.getKey() || value != field.getValue();
                copy.set(name, value);
            }
            return changed ? copy : node;
        }
        return node;
    }

    /**
     * The text with U+FFFD for U+0000; the text itself when it holds none.
     */
    private static String storableText(String text) {
        return holdsNul(text) ? text.replace(IngestConstant.NUL, IngestConstant.NUL_REPLACEMENT) : text;
    }

    private static boolean holdsNul(String text) {
        return text != null && text.indexOf(IngestConstant.NUL) >= 0;
    }

    private static int byteCount(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }
}
