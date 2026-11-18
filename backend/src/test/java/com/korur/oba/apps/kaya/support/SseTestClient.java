package com.korur.oba.apps.kaya.support;

// 3. Java SDK
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A real HTTP client for a server-sent event stream: it opens the connection on its own thread,
 * parses the event frames as they arrive and keeps them for the test to inspect. The server under
 * test is started on a random port, so the request crosses the servlet container and Spring
 * Security's filter chain the way a browser's does.
 */
public final class SseTestClient implements AutoCloseable {

    /** One parsed frame: the event name and its data line. */
    public record Frame(String name, String data) {
    }

    private final List<Frame> frames = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicBoolean ended = new AtomicBoolean();
    private final Thread reader;
    private volatile InputStream body;

    private SseTestClient(HttpRequest request) {
        HttpClient client = HttpClient.newHttpClient();
        this.reader = new Thread(() -> read(client, request), "sse-test-client");
        this.reader.setDaemon(true);
    }

    /** Opens GET path on the local server with the headers given and starts reading. */
    public static SseTestClient open(int port, String path, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "text/event-stream").GET();
        headers.forEach(builder::header);
        SseTestClient client = new SseTestClient(builder.build());
        client.reader.start();
        return client;
    }

    private void read(HttpClient client, HttpRequest request) {
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            contentType.set(response.headers().firstValue("Content-Type").orElse(null));
            body = response.body();
            status.set(response.statusCode());
            if (response.statusCode() != 200) {
                return;
            }
            BufferedReader lines = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
            String name = null;
            String line;
            while ((line = lines.readLine()) != null) {
                if (line.startsWith("event:")) {
                    name = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    frames.add(new Frame(name, line.substring("data:".length()).trim()));
                    name = null;
                }
            }
        } catch (IOException | InterruptedException e) {
            if (!ended.get()) {
                failure.set(e);
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            ended.set(true);
        }
    }

    public List<Frame> frames() {
        return frames;
    }

    public List<Frame> framesNamed(String name) {
        return frames.stream().filter(frame -> name.equals(frame.name())).toList();
    }

    /** The HTTP status once the response head has arrived, else 0. */
    public int status() {
        return status.get();
    }

    public String contentType() {
        return contentType.get();
    }

    /** True once the server closed the stream or the connection failed. */
    public boolean ended() {
        return ended.get();
    }

    /** The error that cut the connection, or null when it ended by itself or is still open. */
    public Throwable failure() {
        return failure.get();
    }

    @Override
    public void close() {
        ended.set(true);
        try {
            if (body != null) {
                body.close();
            }
        } catch (IOException e) {
            // rule-10-no-op -- reason: closing a stream the test is done with; a failure to close the socket changes no result.
        }
        reader.interrupt();
    }
}
