package com.korur.oba.apps.kaya.support;


// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base of the session read tests: records sessions through the ingest endpoint, the way the recorder
 * does, so every read is checked against rows the real write path produced.
 */
public abstract class AbstractSessionIT extends AbstractIngestIT {

    /** Records a session of events 1 to lastSeq; the customizer edits the batch before it is sent. */
    protected String ingestSession(Machine machine, String remote, long lastSeq, Consumer<Map<String, Object>> customizer)
            throws Exception {
        String session = sessionId();
        Map<String, Object> batch = batch(machine, session, remote, events(1, lastSeq));
        customizer.accept(batch);
        send(machine.token(), batch).andExpect(status().isOk());
        return session;
    }

    protected String ingestSession(Machine machine, String remote, long lastSeq) throws Exception {
        return ingestSession(machine, remote, lastSeq, batch -> {
        });
    }

    protected static Map<String, Object> handoff(String path, String content, String capturedAt) {
        return Map.of("path", path, "content", content, "capturedAt", capturedAt);
    }

    protected static void withHandoffs(Map<String, Object> batch, Map<String, Object>... handoffs) {
        batch.put("handoffs", List.of(handoffs));
    }
}
