package com.korur.oba.apps.kazan.cyberscore.enums;

// 2. Third-party
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CyberscoreLevelUnitTest {

    @ParameterizedTest
    @CsvSource({
            "0,CRITICAL", "20,CRITICAL", "21,HIGH", "40,HIGH", "41,MEDIUM", "60,MEDIUM",
            "61,LOW", "80,LOW", "81,MINIMAL", "100,MINIMAL"
    })
    void fromPercentage_boundary_returnsBand(int percentage, CyberscoreLevel expected) {
        assertThat(CyberscoreLevel.fromPercentage(percentage)).isEqualTo(expected);
    }

    @Test
    void fromPercentage_everyPercentage_neverReturnsUnknown() {
        for (int percentage = 0; percentage <= 100; percentage++) {
            assertThat(CyberscoreLevel.fromPercentage(percentage)).isNotEqualTo(CyberscoreLevel.UNKNOWN);
        }
    }
}
