package com.korur.oba.apps.kazan.cyberscore.enums;

// 2. Third-party
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckCategoryUnitTest {

    @Test
    void values_declarationOrder_matchesDisplayOrder() {
        assertThat(CheckCategory.values()).containsExactly(
                CheckCategory.IDENTITY,
                CheckCategory.OFFICE,
                CheckCategory.NETWORK_EDGE,
                CheckCategory.CLOUD_PLATFORM,
                CheckCategory.APPLICATIONS,
                CheckCategory.DATA,
                CheckCategory.DEVICES,
                CheckCategory.DEVELOPMENT_SUPPLY_CHAIN,
                CheckCategory.DETECTION_RESPONSE,
                CheckCategory.COMPLIANCE);
    }
}
