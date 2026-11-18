package com.korur.oba.apps.otak.relation.enums;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

class CountryUnitTest {

    @Test
    void values_equalTheIsoAlpha2CodesOfTheJdkInAlphabeticalOrder() {
        // Arrange
        List<String> isoCodes = Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2).stream()
                .sorted()
                .toList();

        // Act
        List<String> codes = Arrays.stream(Country.values()).map(Country::name).toList();

        // Assert
        assertThat(codes).containsExactlyElementsOf(isoCodes);
    }
}
