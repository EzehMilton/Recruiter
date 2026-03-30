package com.recruiter.support;

import com.recruiter.ai.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BatchMetricsFormatterTest {

    @Test
    void formatsTokenUsageWithThousandsSeparatorAndEstimatedCost() {
        String display = BatchMetricsFormatter.formatTokenUsage(
                new TokenUsage(1_890, 450, 2_340),
                0.0009
        );

        assertThat(display).isEqualTo("2,340 tokens (~$0.0009)");
    }

    @Test
    void formatsApproximateCostAcrossThresholds() {
        assertThat(BatchMetricsFormatter.formatApproxCost(0.023)).isEqualTo("~$0.02");
        assertThat(BatchMetricsFormatter.formatApproxCost(0.0042)).isEqualTo("~$0.004");
        assertThat(BatchMetricsFormatter.formatApproxCost(0.0004)).isEqualTo("~$0.0004");
    }

    @Test
    void formatsDurationForMillisecondsAndSeconds() {
        assertThat(BatchMetricsFormatter.formatDuration(845L)).isEqualTo("845ms");
        assertThat(BatchMetricsFormatter.formatDuration(8_786L)).isEqualTo("8.8s");
        assertThat(BatchMetricsFormatter.formatDuration(null)).isNull();
    }
}
