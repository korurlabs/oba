package com.korur.oba.apps.kazan.cyberscore.service.impl;

// 2. Third-party
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.entity.Check;
import com.korur.oba.apps.kazan.cyberscore.enums.CheckCategory;
import com.korur.oba.apps.kazan.cyberscore.enums.CheckOutcome;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreLevel;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreCalculation;

class CyberscoreCalculateServiceImplUnitTest {

    private final CyberscoreCalculateServiceImpl service = new CyberscoreCalculateServiceImpl();

    private static Check check(int weight, CheckOutcome outcome, CheckCategory category) {
        return Check.builder().weight(weight).outcome(outcome).category(category).build();
    }

    @Test
    void calculate_noChecks_isUnknownAndNotScorable() {
        CyberscoreCalculation result = service.calculate(List.of());

        assertThat(result.scorable()).isFalse();
        assertThat(result.scorePercentage()).isZero();
        assertThat(result.scoreLevel()).isEqualTo(CyberscoreLevel.UNKNOWN);
        assertThat(result.breakdownByCategory()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = CheckOutcome.class, names = {"NO_LICENSE", "NOT_APPLICABLE"})
    void calculate_onlyUnscoredOutcomes_isUnknown(CheckOutcome outcome) {
        CyberscoreCalculation result = service.calculate(List.of(check(10, outcome, CheckCategory.DEVICES)));

        assertThat(result.scorable()).isFalse();
        assertThat(result.scoreLevel()).isEqualTo(CyberscoreLevel.UNKNOWN);
        assertThat(result.breakdownByCategory()).isEmpty();
    }

    @Test
    void calculate_weightsAndFactors_weighsEachOutcome() {
        // achieved 10 + 10 * 0.5 + 0 + 0 = 15 of 40, which is 37.5 and rounds up to 38.
        CyberscoreCalculation result = service.calculate(List.of(
                check(10, CheckOutcome.PASS, null),
                check(10, CheckOutcome.PARTIAL, null),
                check(10, CheckOutcome.FAIL, null),
                check(10, CheckOutcome.TODO, null)));

        assertThat(result.scorable()).isTrue();
        assertThat(result.scorePercentage()).isEqualTo(38);
        assertThat(result.scoreLevel()).isEqualTo(CyberscoreLevel.HIGH);
    }

    @Test
    void calculate_unscoredOutcomes_neitherRaiseNorLowerTheScore() {
        CyberscoreCalculation result = service.calculate(List.of(
                check(10, CheckOutcome.PASS, null),
                check(90, CheckOutcome.NO_LICENSE, null),
                check(90, CheckOutcome.NOT_APPLICABLE, null)));

        assertThat(result.scorePercentage()).isEqualTo(100);
        assertThat(result.scoreLevel()).isEqualTo(CyberscoreLevel.MINIMAL);
    }

    @Test
    void calculate_todoOutcome_countsAsFailure() {
        CyberscoreCalculation result = service.calculate(List.of(
                check(1, CheckOutcome.PASS, null), check(1, CheckOutcome.TODO, null)));

        assertThat(result.scorePercentage()).isEqualTo(50);
    }

    @Test
    void calculate_halfPercentage_roundsUp() {
        // 2 * 0.5 of 8 is 12.5.
        CyberscoreCalculation result = service.calculate(List.of(
                check(2, CheckOutcome.PARTIAL, null), check(6, CheckOutcome.FAIL, null)));

        assertThat(result.scorePercentage()).isEqualTo(13);
        assertThat(result.scoreLevel()).isEqualTo(CyberscoreLevel.CRITICAL);
    }

    @Test
    void calculate_categories_giveOnePercentagePerCategoryInDeclarationOrder() {
        List<Check> checks = new ArrayList<>(List.of(
                check(3, CheckOutcome.PASS, CheckCategory.GDPR_AVG),
                check(1, CheckOutcome.FAIL, CheckCategory.GDPR_AVG),
                check(5, CheckOutcome.PASS, CheckCategory.NETWORK_OFFICE),
                check(5, CheckOutcome.NO_LICENSE, CheckCategory.DEVICES)));

        CyberscoreCalculation result = service.calculate(checks);

        // The category with only unscored outcomes has no entry; GDPR_AVG is declared after NETWORK_OFFICE.
        assertThat(result.breakdownByCategory()).containsExactly(
                java.util.Map.entry("NETWORK_OFFICE", 100), java.util.Map.entry("GDPR_AVG", 75));
        assertThat(result.scorePercentage()).isEqualTo(89);
    }

    @Test
    void calculate_uncategorizedCheck_countsOnlyTowardsTheOverallScore() {
        CyberscoreCalculation result = service.calculate(List.of(
                check(10, CheckOutcome.FAIL, null), check(10, CheckOutcome.PASS, CheckCategory.DEVICES)));

        assertThat(result.scorePercentage()).isEqualTo(50);
        assertThat(result.breakdownByCategory()).containsOnly(java.util.Map.entry("DEVICES", 100));
    }
}
