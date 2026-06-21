package com.cooperativesolutionism.nmsci.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpgradePreflightCliTest {

    private static UpgradePreflightCli.Snapshot snapshot(long pending, long total, long maxConfirm) {
        return new UpgradePreflightCli.Snapshot(pending, total, maxConfirm, 100L);
    }

    @Test
    void parsesOptionsWithDefaults() {
        UpgradePreflightCli.Options options = UpgradePreflightCli.parse(new String[]{});

        assertThat(options.dbUrl).isNull();
        assertThat(options.dbUser).isNull();
        assertThat(options.settleSeconds).isZero();
        assertThat(options.help).isFalse();
    }

    @Test
    void parsesAllOptions() {
        UpgradePreflightCli.Options options = UpgradePreflightCli.parse(
                new String[]{"--db-url", "jdbc:postgresql://localhost:5432/nmsci", "--db-user", "postgres", "--settle-seconds", "15"});

        assertThat(options.dbUrl).isEqualTo("jdbc:postgresql://localhost:5432/nmsci");
        assertThat(options.dbUser).isEqualTo("postgres");
        assertThat(options.settleSeconds).isEqualTo(15);
    }

    @Test
    void rejectsUnknownOptionAndMissingValueAndBadSettle() {
        assertThatThrownBy(() -> UpgradePreflightCli.parse(new String[]{"--nope"}))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> UpgradePreflightCli.parse(new String[]{"--db-url"}))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> UpgradePreflightCli.parse(new String[]{"--settle-seconds", "-1"}))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void noGoWhenPendingMessagesExist() {
        UpgradePreflightCli.Result result = UpgradePreflightCli.evaluate(snapshot(3, 25, 7L), null);

        assertThat(result.go()).isFalse();
        assertThat(result.message()).contains("3");
    }

    @Test
    void goWhenDrainedAndNoStabilityWindow() {
        UpgradePreflightCli.Result result = UpgradePreflightCli.evaluate(snapshot(0, 25, 7L), null);

        assertThat(result.go()).isTrue();
    }

    @Test
    void goWhenDrainedAndStableAcrossWindow() {
        UpgradePreflightCli.Result result =
                UpgradePreflightCli.evaluate(snapshot(0, 25, 7L), snapshot(0, 25, 7L));

        assertThat(result.go()).isTrue();
    }

    @Test
    void noGoWhenNewPendingAppearsInWindow() {
        UpgradePreflightCli.Result result =
                UpgradePreflightCli.evaluate(snapshot(0, 25, 7L), snapshot(1, 26, 9L));

        assertThat(result.go()).isFalse();
    }

    @Test
    void noGoWhenTotalGrowsInWindowEvenIfDrained() {
        UpgradePreflightCli.Result result =
                UpgradePreflightCli.evaluate(snapshot(0, 25, 7L), snapshot(0, 26, 9L));

        assertThat(result.go()).isFalse();
        assertThat(result.message()).contains("稳定窗");
    }
}
