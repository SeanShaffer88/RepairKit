/*
 * RepairKit - an all-in-one Java-based Windows repair and maintenance toolkit.
 * Copyright (C) 2026 Foulest (https://github.com/Foulest)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.foulest.repairkit.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RepairReport}.
 * <p>
 * The completion dialog and the closing sound depend entirely on
 * {@link RepairReport#isFullySuccessful()}, so its edges matter: a cancelled
 * run and a run that never started must not be reported as a success.
 *
 * @author Foulest
 */
class RepairReportTest {

    private static final RepairTask TASK = new RepairTask("disk", "task.disk.title",
            "task.disk.description", RiskLevel.SAFE, true, token -> StepResult.ok(0));

    @Test
    void allOkIsSuccessful() {
        assertTrue(report(List.of(result(StepStatus.OK)), false).isFullySuccessful());
    }

    @Test
    void skippedCountsAsSuccessful() {
        assertTrue(report(List.of(result(StepStatus.OK), result(StepStatus.SKIPPED)),
                false).isFullySuccessful());
    }

    @Test
    void partialOrFailedIsNotSuccessful() {
        assertFalse(report(List.of(result(StepStatus.PARTIAL)), false).isFullySuccessful());
        assertFalse(report(List.of(result(StepStatus.FAILED)), false).isFullySuccessful());
    }

    @Test
    void cancelledRunIsNotSuccessfulEvenWhenEveryTaskWasOk() {
        assertFalse(report(List.of(result(StepStatus.OK)), true).isFullySuccessful());
    }

    @Test
    void emptyRunIsNotSuccessful() {
        assertFalse(report(List.of(), false).isFullySuccessful());
    }

    @Test
    void countsEachStatus() {
        RepairReport report = report(List.of(
                result(StepStatus.OK),
                result(StepStatus.OK),
                result(StepStatus.PARTIAL),
                result(StepStatus.CANCELLED)), true);

        assertEquals(2, report.count(StepStatus.OK));
        assertEquals(1, report.count(StepStatus.PARTIAL));
        assertEquals(0, report.count(StepStatus.FAILED));
        assertEquals(1, report.count(StepStatus.CANCELLED));
    }

    @Test
    void restartIsRequiredWhenAnyTaskAsksForIt() {
        RepairReport withRestart = report(List.of(
                result(StepStatus.OK),
                new TaskResult(TASK, StepResult.ok("done", true), Duration.ZERO)), false);

        assertTrue(withRestart.requiresRestart());
        assertFalse(report(List.of(result(StepStatus.OK)), false).requiresRestart());
    }

    private static TaskResult result(StepStatus status) {
        return new TaskResult(TASK, new StepResult(status, status.name(), 0, false),
                Duration.ZERO);
    }

    private static RepairReport report(List<TaskResult> results, boolean cancelled) {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        return new RepairReport(results, start, start.plusSeconds(5), cancelled, null,
                Path.of("repairkit.log"));
    }
}
