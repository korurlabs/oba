package com.korur.oba.apps.kaya.repository.helper;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

// 2. Third-party

// 3. Java SDK
import java.time.Duration;

// 4. Internal

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class RemoteUrlHelperUnitTest {

    @Nested
    class Normalize {

        @ParameterizedTest
        @CsvSource({
                "https://github.com/senseylabs/village, github.com/senseylabs/village",
                "https://github.com/senseylabs/village.git, github.com/senseylabs/village",
                "https://GitHub.com/SenseyLabs/Village.GIT, github.com/senseylabs/village",
                "http://github.com/senseylabs/village/, github.com/senseylabs/village",
                "'  https://github.com/senseylabs/village  ', github.com/senseylabs/village",
                "git@github.com:senseylabs/village.git, github.com/senseylabs/village",
                "git@GitHub.com:SenseyLabs/Village, github.com/senseylabs/village",
                "github.com:senseylabs/village, github.com/senseylabs/village",
                "ssh://git@github.com/senseylabs/village.git, github.com/senseylabs/village",
                "ssh://git@github.com:2222/senseylabs/village.git, github.com/senseylabs/village",
                "git://github.com/senseylabs/village.git, github.com/senseylabs/village",
                "https://github.com/senseylabs/village?tab=readme#top, github.com/senseylabs/village",
                "https://gitlab.com/g/sub/repo.git, gitlab.com/g/sub/repo"
        })
        void acceptedForm_returnsHostOwnerName(String remoteUrl, String expected) {
            assertEquals(expected, RemoteUrlHelper.normalize(remoteUrl));
        }

        @ParameterizedTest
        @CsvSource({
                "https://user:s3cret@github.com/senseylabs/village.git",
                "https://ghp_token123@github.com/senseylabs/village",
                "ssh://deploy:pw@github.com:22/senseylabs/village"
        })
        void credentials_areDroppedFromTheResult(String remoteUrl) {
            assertEquals("github.com/senseylabs/village", RemoteUrlHelper.normalize(remoteUrl));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {
                "",
                "   ",
                "https://github.com/village",
                "https://github.com",
                "https://github.com/",
                "https://github.com/owner/../repo",
                "https://github.com/./repo",
                "https://github.com/owner//repo",
                "git@github.com:village",
                "village",
                "javascript:alert(1)",
                "file:///home/user/repo",
                "ftp://github.com/owner/repo",
                "https://github.com/owner/ repo"
        })
        void unacceptedForm_returnsNull(String remoteUrl) {
            assertNull(RemoteUrlHelper.normalize(remoteUrl));
        }

        @Test
        void result_longerThan500Characters_returnsNull() {
            String longName = "a".repeat(500);

            assertNull(RemoteUrlHelper.normalize("https://github.com/owner/" + longName));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "https://github.com:443?tab=readme",
                "https://github.com?tab=readme",
                "ssh://git@github.com#top"
        })
        void queryOrFragmentWithoutPath_returnsNull(String remoteUrl) {
            assertNull(RemoteUrlHelper.normalize(remoteUrl));
        }

        // A remote comes from a client, so a crafted value must not make matching take time that
        // grows with the square of its length: a host of repeated "!#" followed by a space used to
        // send the matcher through every split between host and fragment.
        @ParameterizedTest
        @ValueSource(strings = {"ssh://", "https://", "git://user@"})
        void adversarialHostWithFragmentCharacters_returnsNullQuickly(String prefix) {
            String remoteUrl = prefix + "!#".repeat(50_000) + " x";

            assertTimeoutPreemptively(Duration.ofSeconds(1),
                    () -> assertNull(RemoteUrlHelper.normalize(remoteUrl)));
        }

        @Test
        void result_exactly500Characters_isKept() {
            String prefix = "github.com/owner/";
            String name = "a".repeat(500 - prefix.length());

            assertEquals(prefix + name, RemoteUrlHelper.normalize("https://" + prefix + name));
        }
    }

    @Nested
    class DisplayName {

        @Test
        void givenName_isTrimmed() {
            assertEquals("Village", RemoteUrlHelper.displayName("  Village ", "github.com/senseylabs/other"));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        void missingName_usesLastPathSegment(String name) {
            assertEquals("village", RemoteUrlHelper.displayName(name, "github.com/senseylabs/village"));
        }

        @Test
        void longName_isCutTo100Characters() {
            String result = RemoteUrlHelper.displayName("n".repeat(150), "github.com/senseylabs/village");

            assertEquals(100, result.length());
        }
    }
}
