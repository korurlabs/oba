package com.korur.oba.apps.kazan.cyberscore.dto.derived;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Achieved and achievable weight of a set of scorable checks, summed by
 * {@link com.korur.oba.apps.kazan.cyberscore.service.CyberscoreCalculateService}.
 *
 * Immutable: adding a check returns a new tally, so one tally per category and
 * one overall can be kept in a map without sharing state.
 *
 * @param achievedWeight the sum of weight times outcome factor
 * @param possibleWeight the sum of the weights
 */
public record CyberscoreWeightTally(double achievedWeight, int possibleWeight) {

    /**
     * The tally of no check.
     */
    public static final CyberscoreWeightTally EMPTY = new CyberscoreWeightTally(0.0, 0);

    /**
     * Returns this tally with one more check added.
     *
     * @param weight        the check's weight
     * @param outcomeFactor the factor of the check's outcome, from 0.0 to 1.0
     * @return the new tally
     */
    public CyberscoreWeightTally add(int weight, double outcomeFactor) {
        return new CyberscoreWeightTally(achievedWeight + weight * outcomeFactor, possibleWeight + weight);
    }

    /**
     * Whether at least one check with a weight was added.
     */
    public boolean hasWeight() {
        return possibleWeight > 0;
    }

    /**
     * The achieved weight as a rounded percentage of the possible weight; 0 for
     * an empty tally.
     */
    public int toPercentage() {
        if (possibleWeight == 0) {
            return 0;
        }
        return (int) Math.round(achievedWeight / possibleWeight * 100.0);
    }
}
