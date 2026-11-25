package com.korur.oba.apps.kazan.cyberscore.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;

// 2. Third-party

// 3. Java SDK
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.entity.Check;
import com.korur.oba.apps.kazan.cyberscore.enums.CheckCategory;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreLevel;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreCalculateService;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreCalculation;

/**
 * Implements the weighted cyberscore formula.
 *
 * A check counts with its weight times its outcome factor; outcomes without a
 * factor (no license, not applicable) are left out of both the achieved and the
 * possible weight, so they neither raise nor lower the score.
 */
@Service
public class CyberscoreCalculateServiceImpl implements CyberscoreCalculateService {

    @Override
    public CyberscoreCalculation calculate(List<Check> checks) {
        ScoreAccumulator overall = new ScoreAccumulator();
        Map<CheckCategory, ScoreAccumulator> byCategory = new EnumMap<>(CheckCategory.class);

        for (Check check : checks) {
            OptionalDouble factor = check.getOutcome().scoreFactor();
            if (factor.isEmpty()) {
                continue;
            }
            double outcomeFactor = factor.getAsDouble();
            int weight = check.getWeight();

            overall.add(weight, outcomeFactor);
            CheckCategory category = check.getCategory();
            if (category != null) {
                byCategory.computeIfAbsent(category, key -> new ScoreAccumulator()).add(weight, outcomeFactor);
            }
        }

        boolean scorable = overall.possibleWeight > 0;
        int scorePercentage = scorable ? overall.toPercentage() : 0;
        CyberscoreLevel scoreLevel = scorable ? CyberscoreLevel.fromPercentage(scorePercentage) : CyberscoreLevel.UNKNOWN;

        // The enum map iterates in declaration order, which is the display order of the categories.
        Map<String, Integer> breakdownByCategory = new LinkedHashMap<>();
        for (Map.Entry<CheckCategory, ScoreAccumulator> entry : byCategory.entrySet()) {
            breakdownByCategory.put(entry.getKey().name(), entry.getValue().toPercentage());
        }

        return new CyberscoreCalculation(scorable, scorePercentage, scoreLevel, breakdownByCategory);
    }

    /**
     * Accumulates the achieved and the achievable weight of a set of checks.
     */
    private static final class ScoreAccumulator {

        private double achievedWeight = 0.0;
        private int possibleWeight = 0;

        private void add(int weight, double outcomeFactor) {
            this.possibleWeight += weight;
            this.achievedWeight += weight * outcomeFactor;
        }

        private int toPercentage() {
            if (possibleWeight == 0) {
                return 0;
            }
            return (int) Math.round(achievedWeight / possibleWeight * 100.0);
        }
    }
}
