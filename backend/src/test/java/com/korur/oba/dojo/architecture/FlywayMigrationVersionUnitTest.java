package com.korur.oba.dojo.architecture;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 2. Third-party

// 3. Java SDK
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// 4. Internal

/**
 * Guards the rule that every versioned Flyway migration has a version of its own. Two files
 * with one version merge cleanly in Git, but Flyway then refuses to start ("Found more than one
 * migration with version"), so the application cannot boot in any environment. This test runs
 * with the unit tests, without a database, so a pull request that adds a clashing version fails
 * long before an image is built.
 */
class FlywayMigrationVersionUnitTest {

    private static final Path MIGRATIONS = Paths.get("src/main/resources/db/migration");
    private static final Pattern VERSIONED = Pattern.compile("^V([0-9]+(?:[._][0-9]+)*)__.+\\.sql$");

    /**
     * Normalises a version the way Flyway compares it: the parts are numbers, and trailing zero
     * parts do not count, so 1_2_108, 1.2.108 and 1_2_108_0 are one version.
     */
    private static String normalise(String version) {
        List<BigInteger> parts = new ArrayList<>();
        for (String part : version.split("[._]")) {
            parts.add(new BigInteger(part));
        }
        while (parts.size() > 1 && parts.get(parts.size() - 1).signum() == 0) {
            parts.remove(parts.size() - 1);
        }
        List<String> rendered = new ArrayList<>();
        for (BigInteger part : parts) {
            rendered.add(part.toString());
        }
        return String.join(".", rendered);
    }

    private static Map<String, List<String>> duplicateVersions(Path directory) throws IOException {
        Map<String, List<String>> byVersion = new TreeMap<>();
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String name = file.getFileName().toString();
                Matcher matcher = VERSIONED.matcher(name);
                if (matcher.matches()) {
                    byVersion.computeIfAbsent(normalise(matcher.group(1)), key -> new ArrayList<>()).add(name);
                }
            }
        }
        byVersion.values().removeIf(names -> names.size() < 2);
        return byVersion;
    }

    @Test
    void migrationDirectory_allVersionedMigrations_haveUniqueVersions() throws IOException {
        assertTrue(Files.isDirectory(MIGRATIONS), "migration directory must exist: " + MIGRATIONS);

        Map<String, List<String>> duplicates = duplicateVersions(MIGRATIONS);

        assertTrue(duplicates.isEmpty(),
                "Flyway versions must be unique; renumber the migration not yet applied in production: " + duplicates);
    }

    @Test
    void duplicateVersions_sameVersionSpelledDifferently_reportsBothFiles(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("V1_2_108__Kaizen_Change.sql"), "SELECT 1;");
        Files.writeString(directory.resolve("V1.2.108.0__Otak_Change.sql"), "SELECT 1;");
        Files.writeString(directory.resolve("V1_2_109__Other_Change.sql"), "SELECT 1;");
        Files.writeString(directory.resolve("R__Repeatable_View.sql"), "SELECT 1;");

        Map<String, List<String>> duplicates = duplicateVersions(directory);

        assertEquals(Map.of("1.2.108", List.of("V1.2.108.0__Otak_Change.sql", "V1_2_108__Kaizen_Change.sql")),
                duplicates);
    }
}
