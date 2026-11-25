package com.korur.oba.apps.kazan.cyberscore.enums;

// 2. Third-party
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CyberscoreCheckCategoryUnitTest {

    @Test
    void values_declarationOrder_matchesDisplayOrder() {
        assertThat(CyberscoreCheckCategory.values()).containsExactly(
                CyberscoreCheckCategory.IDENTITY,
                CyberscoreCheckCategory.OFFICE,
                CyberscoreCheckCategory.NETWORK_EDGE,
                CyberscoreCheckCategory.CLOUD_PLATFORM,
                CyberscoreCheckCategory.APPLICATIONS,
                CyberscoreCheckCategory.DATA,
                CyberscoreCheckCategory.DEVICES,
                CyberscoreCheckCategory.DEVELOPMENT_SUPPLY_CHAIN,
                CyberscoreCheckCategory.DETECTION_RESPONSE,
                CyberscoreCheckCategory.COMPLIANCE);
    }
}
