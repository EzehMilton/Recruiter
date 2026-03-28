package com.recruiter.screening;

/**
 * One category's contribution to the weighted total score.
 *
 * @param label            display name, e.g. "Required Skills"
 * @param score            raw match percentage 0-100
 * @param effectiveWeight  weight after redistribution (0 when the job description lacks this category)
 * @param contribution     points this category added to the total score
 */
public record CategoryScore(
        String label,
        double score,
        int effectiveWeight,
        double contribution
) {
}