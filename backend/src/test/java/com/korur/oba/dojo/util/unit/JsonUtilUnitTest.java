package com.korur.oba.dojo.util.unit;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.dojo.util.JsonUtil;

/**
 * Pins the wire form of an instant: ISO text with Z out, Z or an offset in, no naive value.
 */
class JsonUtilUnitTest {

    record Stamp(Instant at) {
    }

    @Nested
    class JsonUtilWrites {

        @Test
        void toJson_instant_writesIsoTextWithZ() {
            String json = JsonUtil.toJson(new Stamp(Instant.parse("2026-11-04T10:12:00Z")));

            assertThat(json).isEqualTo("{\"at\":\"2026-11-04T10:12:00Z\"}");
        }

        @Test
        void fromJson_zAndOffset_readSameMoment() {
            Stamp z = JsonUtil.fromJson("{\"at\":\"2026-11-04T10:12:00Z\"}", Stamp.class);
            Stamp offset = JsonUtil.fromJson("{\"at\":\"2026-11-04T13:12:00+03:00\"}", Stamp.class);

            assertThat(offset.at()).isEqualTo(z.at());
        }
    }

    @Nested
    class SpringBootMapperReads {

        // Mirrors the Spring Boot default mapper the REST API binds request bodies with.
        private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        @Test
        void readInstant_withZ_returnsUtcMoment() throws Exception {
            Stamp stamp = mapper.readValue("{\"at\":\"2026-11-04T10:00:00Z\"}", Stamp.class);

            assertThat(stamp.at()).isEqualTo(Instant.parse("2026-11-04T10:00:00Z"));
        }

        @Test
        void readInstant_withOffset_normalisesToUtc() throws Exception {
            Stamp stamp = mapper.readValue("{\"at\":\"2026-11-04T10:00:00+03:00\"}", Stamp.class);

            assertThat(stamp.at()).isEqualTo(Instant.parse("2026-11-04T07:00:00Z"));
        }

        @Test
        void readInstant_naiveValue_isRejected() {
            assertThatThrownBy(() -> mapper.readValue("{\"at\":\"2026-11-04T10:00:00\"}", Stamp.class))
                    .isInstanceOf(InvalidFormatException.class);
        }

        @Test
        void writeInstant_writesIsoTextWithZ() throws Exception {
            assertThat(mapper.writeValueAsString(new Stamp(Instant.parse("2026-11-04T10:00:00Z"))))
                    .isEqualTo("{\"at\":\"2026-11-04T10:00:00Z\"}");
        }
    }
}
