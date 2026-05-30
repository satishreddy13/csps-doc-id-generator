package com.example.pdi.plugin.idgenerator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Performance tests: throughput and zero-duplicate guarantees at scale.
 *
 * These tests are excluded from the default Maven build because they take
 * several seconds to run.  To include them:
 *
 *   mvn test -Dperformance.test.exclude=nothing
 *
 * Or run individually from the IDE.
 *
 * Covers test cases: 65 (at scale)
 */
@Tag("performance")
@DisplayName("Performance")
class IdGeneratorPerformanceTest {

    private static final double MIN_ROWS_PER_SEC = 20_000.0; // conservative floor

    @Test
    @DisplayName("50 000 rows: zero duplicates and throughput ≥ 20 000 rows/sec")
    void performance_50000rows_noDuplicates_meetsMinThroughput() throws Exception {
        runPerformanceTest(50_000);
    }

    @Test
    @DisplayName("100 000 rows: zero duplicates and throughput ≥ 20 000 rows/sec")
    void performance_100000rows_noDuplicates_meetsMinThroughput() throws Exception {
        runPerformanceTest(100_000);
    }

    // -----------------------------------------------------------------------

    private void runPerformanceTest(int rowCount) throws Exception {
        TestableStep step = new TestableStep(UUID.randomUUID().toString());
        IdGeneratorStepMeta meta = TestableStep.metaWithPrefix("PERF1");

        long startMs = System.currentTimeMillis();
        List<String> ids = step.generateIds(meta, rowCount);
        long elapsedMs = System.currentTimeMillis() - startMs;

        // No duplicates
        Set<String> unique = new HashSet<>(ids);
        assertEquals(rowCount, unique.size(),
            "Duplicate IDs detected in " + rowCount + "-row run");

        // Throughput
        double rowsPerSec = rowCount / (elapsedMs / 1000.0);
        System.out.printf("[Performance] %,d rows | elapsed %d ms | throughput %,.0f rows/sec%n",
            rowCount, elapsedMs, rowsPerSec);

        assertTrue(rowsPerSec >= MIN_ROWS_PER_SEC,
            String.format("Throughput %.0f rows/sec is below the %.0f floor for %d rows",
                rowsPerSec, MIN_ROWS_PER_SEC, rowCount));
    }
}
