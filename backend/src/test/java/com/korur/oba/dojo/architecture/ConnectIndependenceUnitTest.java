package com.korur.oba.dojo.architecture;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 2. Third-party

// 3. Java SDK
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

// 4. Internal

/**
 * Guards the rule that the {@code connect} package knows no app: no main or test file under
 * it may mention a class of {@code com.korur.oba.apps}, in an import, a static import or a
 * Javadoc reference.
 */
class ConnectIndependenceUnitTest {

    private static final String FORBIDDEN = "com.korur.oba.apps.";
    private static final Path MAIN_CONNECT = Paths.get("src/main/java/com/korur/oba/connect");
    private static final Path TEST_CONNECT = Paths.get("src/test/java/com/korur/oba/connect");

    private static List<String> violations(Path root) throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int index = 0; index < lines.size(); index++) {
                    if (lines.get(index).contains(FORBIDDEN)) {
                        found.add(file + ":" + (index + 1));
                    }
                }
            }
        }
        return found;
    }

    @Test
    void connectSources_mainAndTest_referenceNoApp() throws IOException {
        assertTrue(Files.isDirectory(MAIN_CONNECT), "main connect directory must exist: " + MAIN_CONNECT);
        assertTrue(Files.isDirectory(TEST_CONNECT), "test connect directory must exist: " + TEST_CONNECT);

        List<String> found = new ArrayList<>(violations(MAIN_CONNECT));
        found.addAll(violations(TEST_CONNECT));

        assertTrue(found.isEmpty(), "connect must not reference " + FORBIDDEN + " but does in: " + found);
    }

    @Test
    void violations_importStaticImportAndJavadoc_namesFileAndLine(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("Offender.java");
        Files.write(file, List.of(
                "package x;",
                "import com.korur.oba.apps.kaizen.Foo;",
                "import static com.korur.oba.apps.kaizen.Foo.bar;",
                "/** See {@link com.korur.oba.apps.kaizen.Foo}. */",
                "class Offender { }"));

        List<String> found = violations(directory);

        assertEquals(List.of(file + ":2", file + ":3", file + ":4"), found);
    }

    @Test
    void violations_cleanFile_returnsEmpty(@TempDir Path directory) throws IOException {
        Files.write(directory.resolve("Clean.java"), List.of("package x;", "import com.korur.oba.dojo.Foo;"));

        assertTrue(violations(directory).isEmpty());
    }
}
