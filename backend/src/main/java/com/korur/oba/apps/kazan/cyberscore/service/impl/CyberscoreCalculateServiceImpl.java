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
import com.korur.oba.apps.kazan.cyberscore.dto.derived.CyberscoreCalculation;
import com.korur.oba.apps.kazan.cyberscore.dto.derived.CyberscoreWeightTally;
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreCheck;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckCategory;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreLevel;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreCalculateService;

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
    public CyberscoreCalculation calculate(List<CyberscoreCheck> checks) {
        CyberscoreWeightTally overall = CyberscoreWeightTally.EMPTY;
        Map<CyberscoreCheckCategory, CyberscoreWeightTally> byCategory = new EnumMap<>(CyberscoreCheckCategory.class);

        for (CyberscoreCheck check : checks) {
            OptionalDouble factor = check.getOutcome().scoreFactor();
            if (factor.isEmpty()) {
                continue;
            }
            double outcomeFactor = factor.getAsDouble();
            int weight = check.getWeight();

            overall = overall.add(weight, outcomeFactor);
            CyberscoreCheckCategory category = check.getCategory();
            if (category != null) {
                CyberscoreWeightTally categoryTally = byCategory.get(category);
                if (categoryTally == null) {
                    categoryTally = CyberscoreWeightTally.EMPTY;
                }
                byCategory.put(category, categoryTally.add(weight, outcomeFactor));
            }
        }

        boolean scorable = overall.hasWeight();
        int scorePercentage = scorable ? overall.toPercentage() : 0;
        CyberscoreLevel scoreLevel = scorable
                ? CyberscoreLevel.fromPercentage(scorePercentage)
                : CyberscoreLevel.UNKNOWN;

        // The enum map iterates in declaration order, which is the display order of the categories.
        Map<String, Integer> breakdownByCategory = new LinkedHashMap<>();
        for (Map.Entry<CyberscoreCheckCategory, CyberscoreWeightTally> entry : byCategory.entrySet()) {
            breakdownByCategory.put(entry.getKey().name(), entry.getValue().toPercentage());
        }

        return new CyberscoreCalculation(scorable, scorePercentage, scoreLevel, breakdownByCategory);
    }
}
