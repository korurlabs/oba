package com.korur.oba.apps.kaya.support;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

// 3. Java SDK
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// 4. Internal

/**
 * The recorded runs of the frontend (projects/oba/frontend/apps/kaya/fixtures/runs). The backend derives
 * the stored subagents and skill uses from the same events the session page reads, so its tests
 * replay these runs.
 */
public final class RunFixtures {

    /** Every run directory the frontend records. */
    public static final List<String> RUN_NAMES = List.of("plain", "plan-waiting", "implement-running",
            "validate-blocked", "review-critical", "pull-request", "session-end", "deliver-full");

    /** A handoff file of a run, by its path below handoffs/. */
    public record HandoffFile(String path, String content) {
    }

    private static final Path RUNS = Path.of("..", "frontend", "apps", "kaya", "fixtures", "runs");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RunFixtures() {
    }

    /** The events of a run with a seq from 1, as the recorder's queue numbers them. */
    public static List<Map<String, Object>> events(String run) {
        try {
            List<Map<String, Object>> events = new ArrayList<>();
            long seq = 1;
            for (String line : Files.readAllLines(RUNS.resolve(run).resolve("events.jsonl"), StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                Map<String, Object> event = MAPPER.readValue(line, new TypeReference<LinkedHashMap<String, Object>>() {
                });
                event.put("seq", seq++);
                events.add(event);
            }
            return events;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every handoff snapshot a run holds, by its path below handoffs/. */
    public static List<HandoffFile> handoffs(String run) {
        Path root = RUNS.resolve(run).resolve("handoffs");
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            List<HandoffFile> result = new ArrayList<>();
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                result.add(new HandoffFile(relative, Files.readString(file, StandardCharsets.UTF_8)));
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
