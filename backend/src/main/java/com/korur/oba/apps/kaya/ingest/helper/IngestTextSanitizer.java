package com.korur.oba.apps.kaya.ingest.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
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
 * Makes the text of a recorder batch storable before any step stores it. PostgreSQL holds no
 * U+0000 in text, varchar or jsonb and refuses the whole statement that carries one, so a single
 * such character anywhere in a batch failed the batch, and the recorder sent it again on every
 * retry. The batch is accepted rather than refused, because a refused batch would stop the session
 * from recording just the same.
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
 * The walk reads every component of every record, every list element and every map key and value
 * of the batch, so a field that a later change adds is covered too, as long as the request types
 * stay records, lists, maps and strings.
 *
 * The walk copies nothing that holds no U+0000. A batch without U+0000, the usual case, is read
 * once and returned as received. In a batch with U+0000 only the changed strings and the records,
 * lists and maps that hold them are new, and everything else is shared with the received batch. A
 * batch is up to 8 MiB of JSON, and a copy of all of it would multiply the memory one request
 * needs.
 */
@Component
@RequiredArgsConstructor
public class IngestTextSanitizer {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(IngestTextSanitizer.class);

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
        IngestBatchRequest storable = (IngestBatchRequest) storable(textOnly);
        if (storable != textOnly) {
            loggingService.info(LOG_PREFIX, LogLayer.HELPER, IngestTextSanitizerLogConstant.NUL_REPLACED,
                    request.sessionId(), principal.machineId());
        }
        return storable;
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
     * The value with U+FFFD for U+0000 in every text it holds, map keys included; the value itself
     * when it holds none. Numbers, booleans, times and enums hold no text and are returned as they
     * are.
     */
    private Object storable(Object value) {
        if (value instanceof String text) {
            return storableText(text);
        }
        if (value instanceof Record record) {
            return storableRecord(record);
        }
        if (value instanceof List<?> list) {
            return storableList(list);
        }
        if (value instanceof Map<?, ?> map) {
            return storableMap(map);
        }
        return value;
    }

    /**
     * The record rebuilt with its storable components once one of them changed; the record itself
     * otherwise.
     */
    private Record storableRecord(Record record) {
        RecordComponent[] components = record.getClass().getRecordComponents();
        Object[] values = null;
        for (int index = 0; index < components.length; index++) {
            Object value = componentValue(record, components[index]);
            Object storableValue = storable(value);
            if (values == null && storableValue != value) {
                values = new Object[components.length];
                for (int earlier = 0; earlier < index; earlier++) {
                    values[earlier] = componentValue(record, components[earlier]);
                }
            }
            if (values != null) {
                values[index] = storableValue;
            }
        }
        return values == null ? record : newRecord(record, components, values);
    }

    /**
     * The list with its storable elements, in their order, once one of them changed; the list
     * itself otherwise.
     */
    private List<?> storableList(List<?> list) {
        List<Object> copy = null;
        int index = 0;
        for (Object element : list) {
            Object storableElement = storable(element);
            if (copy == null && storableElement != element) {
                copy = new ArrayList<>(list.size());
                copy.addAll(list.subList(0, index));
            }
            if (copy != null) {
                copy.add(storableElement);
            }
            index++;
        }
        return copy == null ? list : copy;
    }

    /**
     * The map with its storable keys and values, in their order, once one of them changed; the map
     * itself otherwise. A key that becomes equal to another key keeps the later value, as a JSON
     * object that names a key twice does.
     */
    private Map<?, ?> storableMap(Map<?, ?> map) {
        StorableEntries entries = new StorableEntries(map);
        map.forEach(entries);
        return entries.copy == null ? map : entries.copy;
    }

    /**
     * Takes the entries of one map in their order and starts a copy at the first entry that
     * changed. The map is read with forEach because a LinkedHashMap, the type the mapper reads an
     * event object into, answers entrySet with a view that it keeps for as long as the map lives;
     * for a batch of many small objects those views alone would add a large share to the memory
     * of the parsed batch.
     */
    private final class StorableEntries implements BiConsumer<Object, Object> {

        private final Map<?, ?> map;
        private Map<Object, Object> copy;
        private int index;

        private StorableEntries(Map<?, ?> map) {
            this.map = map;
        }

        @Override
        public void accept(Object key, Object value) {
            Object storableKey = storable(key);
            Object storableValue = storable(value);
            if (copy == null && (storableKey != key || storableValue != value)) {
                copy = firstEntries(map, index);
            }
            if (copy != null) {
                copy.put(storableKey, storableValue);
            }
            index++;
        }
    }

    /**
     * A new map that holds the first count entries of the map, in their order, with room for all.
     */
    private static Map<Object, Object> firstEntries(Map<?, ?> map, int count) {
        Map<Object, Object> first = LinkedHashMap.newLinkedHashMap(map.size());
        Iterator<? extends Map.Entry<?, ?>> entries = map.entrySet().iterator();
        for (int index = 0; index < count; index++) {
            Map.Entry<?, ?> entry = entries.next();
            first.put(entry.getKey(), entry.getValue());
        }
        return first;
    }

    private Object componentValue(Record record, RecordComponent component) {
        try {
            return component.getAccessor().invoke(record);
        } catch (ReflectiveOperationException e) {
            // The request types are public records, whose public accessors only return a field.
            loggingService.error(LOG_PREFIX, LogLayer.HELPER, IngestTextSanitizerLogConstant.RECORD_READ_FAILED, e,
                    component.getName(), record.getClass().getSimpleName());
            throw new IllegalStateException(IngestTextSanitizerLogConstant.RECORD_READ_FAILED, e);
        }
    }

    private Record newRecord(Record record, RecordComponent[] components, Object[] values) {
        Class<?>[] types = new Class<?>[components.length];
        for (int index = 0; index < components.length; index++) {
            types[index] = components[index].getType();
        }
        try {
            return (Record) record.getClass().getDeclaredConstructor(types).newInstance(values);
        } catch (ReflectiveOperationException e) {
            // The request types are public records, whose public canonical constructors only assign fields.
            loggingService.error(LOG_PREFIX, LogLayer.HELPER, IngestTextSanitizerLogConstant.RECORD_COPY_FAILED, e,
                    record.getClass().getSimpleName());
            throw new IllegalStateException(IngestTextSanitizerLogConstant.RECORD_COPY_FAILED, e);
        }
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
