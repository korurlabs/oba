package com.korur.oba.apps.kaya.ingest.helper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 2. Third-party

// 3. Java SDK
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.ingest.constant.helper.IngestTextSanitizerLogConstant;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestAccountRequest;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestBatchRequest;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestConfigReleaseRequest;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestHandoffRequest;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestMachineRequest;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestPlanRequest;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestRepositoryRequest;
import com.korur.oba.apps.kaya.ingest.dto.request.IngestSummaryRequest;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.machine.dto.request.MachineUsageSnapshotRequest;
import com.korur.oba.apps.kaya.machine.dto.request.MachineUsageWindowRequest;
import com.korur.oba.apps.kaya.plan.enums.PlanKind;
import com.korur.oba.apps.kaya.plan.enums.PlanVersionSource;
import com.korur.oba.apps.kaya.session.enums.SessionDeliveredBy;
import com.korur.oba.apps.kaya.session.enums.SessionReleaseState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IngestTextSanitizerUnitTest {

    private static final String SESSION_ID = "session-1";
    private static final Instant CAPTURED_AT = Instant.parse("2026-10-20T09:15:00Z");
    // The start of an image file as the recorder reads it: bytes that are no UTF-8 arrive as U+FFFD,
    // and the zero bytes arrive as U+0000.
    private static final String IMAGE_CONTENT = "\uFFFDPNG\r\n\u001A\n\u0000\u0000\u0000\rIHDR";
    private static final int IMAGE_CONTENT_BYTES = 18;
    private static final String NOTE_CONTENT = "# Export\n\n**Status:** PARTIAL\n";

    @Mock
    private LoggingService loggingService;

    private IngestTextSanitizer sanitizer;
    private MachinePrincipal principal;

    @BeforeEach
    void setUp() {
        sanitizer = new IngestTextSanitizer(loggingService);
        principal = new MachinePrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "laptop");
    }

    private static IngestSummaryRequest summary(String firstPrompt, List<String> features) {
        return new IngestSummaryRequest("Export the board", null, firstPrompt, CAPTURED_AT, CAPTURED_AT, false, "/repo",
                "feature/export", null, null, features, null, 3L, "claude-opus-5-5", "ada@example.com",
                new IngestConfigReleaseRequest(7, "ab12", SessionReleaseState.FRESH, SessionDeliveredBy.LAUNCHER));
    }

    private static Map<String, Object> event(Object command) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("seq", 1);
        event.put("at", CAPTURED_AT.toString());
        event.put("event", "PostToolUse");
        event.put("tool_input", Map.of("command", command));
        return event;
    }

    private static IngestBatchRequest batch(IngestSummaryRequest summary, List<Map<String, Object>> events,
            List<IngestHandoffRequest> handoffs, List<IngestPlanRequest> plans) {
        IngestMachineRequest machine = new IngestMachineRequest("laptop", "2.3.1",
                new IngestAccountRequest("ada@example.com", "Example", null, "stripe_subscription", null, "premium"));
        MachineUsageSnapshotRequest usage = new MachineUsageSnapshotRequest(CAPTURED_AT,
                new MachineUsageWindowRequest(new BigDecimal("23.50"), 1_792_764_000L), null);
        return new IngestBatchRequest(SESSION_ID, new IngestRepositoryRequest("git@github.com:example/app.git", "app"),
                machine, summary, events, handoffs, plans, usage);
    }

    @Nested
    class WithoutNul {

        @Test
        void batchWithoutNul_isReturnedAsReceived() {
            IngestBatchRequest request = batch(summary("Export the board", List.of("export")),
                    List.of(event("git status")),
                    List.of(new IngestHandoffRequest("export/backend.md", NOTE_CONTENT, CAPTURED_AT)),
                    List.of(new IngestPlanRequest(PlanKind.SESSION, PlanVersionSource.MESSAGE, null, NOTE_CONTENT,
                            CAPTURED_AT)));

            assertSame(request, sanitizer.sanitize(principal, request));
            verify(loggingService, never()).warn(any(), any(), any(String.class), any(Object[].class));
        }
    }

    @Nested
    class BinarySnapshots {

        @Test
        void handoffHoldingNul_isLeftOutWithAWarningAndTheOtherHandoffIsKept() {
            IngestHandoffRequest note = new IngestHandoffRequest("export/backend.md", NOTE_CONTENT, CAPTURED_AT);
            IngestBatchRequest request = batch(summary("Export the board", null), List.of(event("git status")),
                    List.of(new IngestHandoffRequest("export/screen.png", IMAGE_CONTENT, CAPTURED_AT), note), null);

            IngestBatchRequest sanitized = sanitizer.sanitize(principal, request);

            assertEquals(List.of(note), sanitized.handoffs());
            assertSame(request.events(), sanitized.events());
            assertSame(request.summary(), sanitized.summary());
            verify(loggingService).warn(any(), eq(LogLayer.HELPER), eq(IngestTextSanitizerLogConstant.HANDOFF_SKIPPED),
                    eq("export/screen.png"), eq(SESSION_ID), eq(principal.machineId()), eq(IMAGE_CONTENT_BYTES));
        }

        @Test
        void planHoldingNul_isLeftOutWithAWarning() {
            IngestBatchRequest request = batch(summary("Export the board", null), List.of(event("git status")), null,
                    List.of(new IngestPlanRequest(PlanKind.SPEC, PlanVersionSource.FILE, "docs/plans/export.md",
                            IMAGE_CONTENT, CAPTURED_AT)));

            IngestBatchRequest sanitized = sanitizer.sanitize(principal, request);

            assertEquals(List.of(), sanitized.plans());
            verify(loggingService).warn(any(), eq(LogLayer.HELPER), eq(IngestTextSanitizerLogConstant.PLAN_SKIPPED),
                    eq(PlanKind.SPEC), eq("docs/plans/export.md"), eq(SESSION_ID), eq(principal.machineId()),
                    eq(IMAGE_CONTENT_BYTES));
        }
    }

    @Nested
    class OtherText {

        @Test
        void nulInFreeText_becomesTheReplacementCharacterAndEverythingElseIsKept() {
            Map<String, Object> event = event("printf 'a\u0000b'");
            event.put("tool_response", Map.of("std\u0000out", List.of("line\u0000one", 2)));
            IngestBatchRequest request = batch(summary("Export\u0000 the board", List.of("ex\u0000port")),
                    List.of(event), List.of(new IngestHandoffRequest("export/backend.md", NOTE_CONTENT, CAPTURED_AT)),
                    List.of(new IngestPlanRequest(PlanKind.SPEC, PlanVersionSource.FILE, "docs/plans/export.md",
                            NOTE_CONTENT, CAPTURED_AT)));

            IngestBatchRequest sanitized = sanitizer.sanitize(principal, request);

            assertEquals("Export\uFFFD the board", sanitized.summary().firstPrompt());
            assertEquals(List.of("ex\uFFFDport"), sanitized.summary().features());
            Map<String, Object> storedEvent = sanitized.events().get(0);
            assertEquals(Map.of("command", "printf 'a\uFFFDb'"), storedEvent.get("tool_input"));
            assertEquals(Map.of("std\uFFFDout", List.of("line\uFFFDone", 2)), storedEvent.get("tool_response"));
            assertEquals(1, storedEvent.get("seq"));
            assertEquals(CAPTURED_AT.toString(), storedEvent.get("at"));
            assertEquals(request.summary().startedAt(), sanitized.summary().startedAt());
            assertEquals(request.summary().configRelease(), sanitized.summary().configRelease());
            assertEquals(request.machine(), sanitized.machine());
            assertEquals(request.repository(), sanitized.repository());
            assertEquals(0, request.usage().fiveHour().usedPercentage()
                    .compareTo(sanitized.usage().fiveHour().usedPercentage()));
            assertEquals(request.usage().capturedAt(), sanitized.usage().capturedAt());
            assertEquals(request.handoffs(), sanitized.handoffs());
            assertEquals(request.plans(), sanitized.plans());
            verify(loggingService).info(any(), eq(LogLayer.HELPER), eq(IngestTextSanitizerLogConstant.NUL_REPLACED),
                    eq(SESSION_ID), eq(principal.machineId()));
        }

        @Test
        void nulInTheMachineAndAccountFields_becomesTheReplacementCharacter() {
            IngestBatchRequest received = batch(summary("Export the board", null), List.of(event("git status")), null,
                    null);
            IngestBatchRequest request = new IngestBatchRequest(SESSION_ID, received.repository(),
                    new IngestMachineRequest("lap\u0000top", "2.3\u0000.1",
                            new IngestAccountRequest("ada@example.com", "Exa\u0000mple", null, null, null, null)),
                    received.summary(), received.events(), null, null, null);

            IngestBatchRequest sanitized = sanitizer.sanitize(principal, request);

            assertEquals(new IngestMachineRequest("lap\uFFFDtop", "2.3\uFFFD.1",
                    new IngestAccountRequest("ada@example.com", "Exa\uFFFDmple", null, null, null, null)),
                    sanitized.machine());
        }

        @Test
        void nulInOneEvent_copiesOnlyWhatHoldsItAndSharesTheRestWithTheReceivedBatch() {
            Map<String, Object> quiet = event("git status");
            Map<String, Object> response = Map.of("lines", List.of("on branch main", "nothing to commit"));
            Map<String, Object> loud = event("printf 'a\u0000b'");
            loud.put("tool_response", response);
            IngestBatchRequest request = batch(summary("Export the board", List.of("export")), List.of(quiet, loud),
                    List.of(new IngestHandoffRequest("export/backend.md", NOTE_CONTENT, CAPTURED_AT)), null);

            IngestBatchRequest sanitized = sanitizer.sanitize(principal, request);

            Map<String, Object> storedLoud = sanitized.events().get(1);
            assertEquals(Map.of("command", "printf 'a\uFFFDb'"), storedLoud.get("tool_input"));
            assertEquals(List.copyOf(loud.keySet()), List.copyOf(storedLoud.keySet()));
            assertSame(quiet, sanitized.events().get(0));
            assertSame(response, storedLoud.get("tool_response"));
            assertSame(request.summary(), sanitized.summary());
            assertSame(request.machine(), sanitized.machine());
            assertSame(request.repository(), sanitized.repository());
            assertSame(request.handoffs(), sanitized.handoffs());
            assertSame(request.usage(), sanitized.usage());
            assertEquals(Map.of("command", "printf 'a\u0000b'"), loud.get("tool_input"));
        }
    }

    @Nested
    class Coverage {

        // Types that hold no text, which the walk returns as they are.
        private static final Set<Class<?>> TEXTLESS_TYPES = Set.of(Instant.class, Boolean.class, Integer.class,
                Long.class, BigDecimal.class);

        /**
         * The walk reads records, lists, maps and strings. A field of another type that holds text,
         * such as a class that is no record, would reach PostgreSQL with its U+0000 and fail the batch,
         * so such a type fails this test instead. Object is the type of event values, which the
         * mapper reads as maps, lists, strings, numbers and booleans.
         */
        @Test
        void everyTypeTheBatchHolds_isOneTheWalkReadsOrOneWithoutText() {
            List<String> unread = new ArrayList<>();

            collectUnread(IngestBatchRequest.class, "batch", unread);

            assertEquals(List.of(), unread);
        }

        private void collectUnread(Type type, String path, List<String> unread) {
            if (type instanceof ParameterizedType parameterized) {
                Type raw = parameterized.getRawType();
                if (raw != List.class && raw != Map.class) {
                    unread.add(path + ": " + type.getTypeName());
                    return;
                }
                for (Type argument : parameterized.getActualTypeArguments()) {
                    collectUnread(argument, path + "[]", unread);
                }
                return;
            }
            if (!(type instanceof Class<?> javaClass)) {
                unread.add(path + ": " + type.getTypeName());
                return;
            }
            if (javaClass.isRecord()) {
                for (RecordComponent component : javaClass.getRecordComponents()) {
                    collectUnread(component.getGenericType(), path + "." + component.getName(), unread);
                }
                return;
            }
            boolean read = javaClass == String.class || javaClass == Object.class;
            boolean textless = javaClass.isPrimitive() || javaClass.isEnum() || TEXTLESS_TYPES.contains(javaClass);
            if (!read && !textless) {
                unread.add(path + ": " + javaClass.getName());
            }
        }
    }
}
