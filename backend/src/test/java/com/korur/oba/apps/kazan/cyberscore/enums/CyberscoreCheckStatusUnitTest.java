package com.korur.oba.apps.kazan.cyberscore.enums;

// 2. Third-party
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CyberscoreCheckStatusUnitTest {

    @ParameterizedTest
    @CsvSource({
            "TODO,NOT_STARTED", "NOT_APPLICABLE,NOT_APPLICABLE", "PASS,DONE", "PARTIAL,DONE", "FAIL,DONE",
            "NO_LICENSE,DONE"
    })
    void fromOutcome_everyOutcome_returnsTheStatusItImplies(CyberscoreCheckOutcome outcome,
                                                            CyberscoreCheckStatus expected) {
        assertThat(CyberscoreCheckStatus.fromOutcome(outcome)).isEqualTo(expected);
    }
}
