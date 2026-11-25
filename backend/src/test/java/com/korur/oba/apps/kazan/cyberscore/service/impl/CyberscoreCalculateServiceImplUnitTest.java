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
import com.korur.oba.apps.kazan.cyberscore.dto.derived.CyberscoreCalculation;
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreCheck;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckCategory;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckOutcome;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreLevel;

class CyberscoreCalculateServiceImplUnitTest {

    private final CyberscoreCalculateServiceImpl service = new CyberscoreCalculateServiceImpl();

    private static CyberscoreCheck check(int weight, CyberscoreCheckOutcome outcome, CyberscoreCheckCategory category) {
        return CyberscoreCheck.builder().weight(weight).outcome(outcome).category(category).build();
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
    @EnumSource(value = CyberscoreCheckOutcome.class, names = {"NO_LICENSE", "NOT_APPLICABLE"})
    void calculate_onlyUnscoredOutcomes_isUnknown(CyberscoreCheckOutcome outcome) {
        CyberscoreCalculation result = service.calculate(List.of(check(10, outcome, CyberscoreCheckCategory.DEVICES)));

        assertThat(result.scorable()).isFalse();
        assertThat(result.scoreLevel()).isEqualTo(CyberscoreLevel.UNKNOWN);
        assertThat(result.breakdownByCategory()).isEmpty();
    }

    @Test
    void calculate_weightsAndFactors_weighsEachOutcome() {
        // achieved 10 + 10 * 0.5 + 0 + 0 = 15 of 40, which is 37.5 and rounds up to 38.
        CyberscoreCalculation result = service.calculate(List.of(
                check(10, CyberscoreCheckOutcome.PASS, null),
                check(10, CyberscoreCheckOutcome.PARTIAL, null),
                check(10, CyberscoreCheckOutcome.FAIL, null),
                check(10, CyberscoreCheckOutcome.TODO, null)));

        assertThat(result.scorable()).isTrue();
        assertThat(result.scorePercentage()).isEqualTo(38);
        assertThat(result.scoreLevel()).isEqualTo(CyberscoreLevel.HIGH);
    }

    @Test
    void calculate_unscoredOutcomes_neitherRaiseNorLowerTheScore() {
        CyberscoreCalculation result = service.calculate(List.of(
                check(10, CyberscoreCheckOutcome.PASS, null),
                check(90, CyberscoreCheckOutcome.NO_LICENSE, null),
                check(90, CyberscoreCheckOutcome.NOT_APPLICABLE, null)));

        assertThat(result.scorePercentage()).isEqualTo(100);
        assertThat(result.scoreLevel()).isEqualTo(CyberscoreLevel.MINIMAL);
    }

    @Test
    void calculate_todoOutcome_countsAsFailure() {
        CyberscoreCalculation result = service.calculate(List.of(
                check(1, CyberscoreCheckOutcome.PASS, null), check(1, CyberscoreCheckOutcome.TODO, null)));

        assertThat(result.scorePercentage()).isEqualTo(50);
    }

    @Test
    void calculate_halfPercentage_roundsUp() {
        // 2 * 0.5 of 8 is 12.5.
        CyberscoreCalculation result = service.calculate(List.of(
                check(2, CyberscoreCheckOutcome.PARTIAL, null), check(6, CyberscoreCheckOutcome.FAIL, null)));

        assertThat(result.scorePercentage()).isEqualTo(13);
        assertThat(result.scoreLevel()).isEqualTo(CyberscoreLevel.CRITICAL);
    }

    @Test
    void calculate_categories_giveOnePercentagePerCategoryInDeclarationOrder() {
        List<CyberscoreCheck> checks = new ArrayList<>(List.of(
                check(3, CyberscoreCheckOutcome.PASS, CyberscoreCheckCategory.COMPLIANCE),
                check(1, CyberscoreCheckOutcome.FAIL, CyberscoreCheckCategory.COMPLIANCE),
                check(5, CyberscoreCheckOutcome.PASS, CyberscoreCheckCategory.IDENTITY),
                check(5, CyberscoreCheckOutcome.NO_LICENSE, CyberscoreCheckCategory.DEVICES)));

        CyberscoreCalculation result = service.calculate(checks);

        // The category with only unscored outcomes has no entry; COMPLIANCE is declared after IDENTITY.
        assertThat(result.breakdownByCategory()).containsExactly(
                java.util.Map.entry("IDENTITY", 100), java.util.Map.entry("COMPLIANCE", 75));
        assertThat(result.scorePercentage()).isEqualTo(89);
    }

    @Test
    void calculate_uncategorizedCheck_countsOnlyTowardsTheOverallScore() {
        CyberscoreCalculation result = service.calculate(List.of(
                check(10, CyberscoreCheckOutcome.FAIL, null), check(10, CyberscoreCheckOutcome.PASS, CyberscoreCheckCategory.DEVICES)));

        assertThat(result.scorePercentage()).isEqualTo(50);
        assertThat(result.breakdownByCategory()).containsOnly(java.util.Map.entry("DEVICES", 100));
    }
}
