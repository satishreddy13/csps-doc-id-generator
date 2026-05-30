package com.example.pdi.plugin.idgenerator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests that guarantee no two IDs are ever the same, under sequential and
 * concurrent load, within a single transformation and across multiple ones.
 *
 * Covers test cases: 58–67
 */
@DisplayName("ID Uniqueness")
class IdUniquenessTest {

    // -----------------------------------------------------------------------
    // Sequential uniqueness (cases 64–65)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("100 sequential IDs are all distinct")
    void sequential_100_IdsAreAllUnique() throws Exception {
        assertNoDuplicates(generateSequential(100));
    }

    @Test
    @DisplayName("10 000 sequential IDs are all distinct")
    void sequential_10000_IdsAreAllUnique() throws Exception {
        assertNoDuplicates(generateSequential(10_000));
    }

    // -----------------------------------------------------------------------
    // Concurrent uniqueness (cases 58–60)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("4 threads × 2 500 IDs each = 10 000 unique IDs (shared run key)")
    void concurrent_4threads_10000IdsTotal_allUnique() throws Exception {
        final int threads    = 4;
        final int perThread  = 2_500;
        final String runKey  = "concurrent-test-" + UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<List<String>>> tasks = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            tasks.add(() -> {
                // Each thread gets its own step instance but shares the same run key
                TestableStep step = new TestableStep(runKey);
                return step.generateIds(TestableStep.defaultMeta(), perThread);
            });
        }

        List<Future<List<String>>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        List<String> allIds = new ArrayList<>(threads * perThread);
        for (Future<List<String>> f : futures) {
            allIds.addAll(f.get());
        }

        assertEquals(threads * perThread, allIds.size(),
            "Expected " + (threads * perThread) + " IDs total");
        assertNoDuplicates(allIds);

        // Cleanup any lingering SEQUENCE_MAP entries
        IdGeneratorStep.SEQUENCE_MAP.remove(runKey);
    }

    // -----------------------------------------------------------------------
    // Multi-transformation independence (cases 57, 67)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("two transformations with different run keys generate non-overlapping IDs")
    void twoTransformations_haveIndependentClocks_noOverlap() throws Exception {
        String keyA = "trans-a-" + UUID.randomUUID();
        String keyB = "trans-b-" + UUID.randomUUID();

        TestableStep stepA = new TestableStep(keyA);
        TestableStep stepB = new TestableStep(keyB);

        List<String> idsA = stepA.generateIds(TestableStep.defaultMeta(), 500);
        List<String> idsB = stepB.generateIds(TestableStep.defaultMeta(), 500);

        Set<String> combined = new HashSet<>();
        combined.addAll(idsA);
        int sizeAfterA = combined.size();
        combined.addAll(idsB);

        // If there were duplicates across the two sets, combined would be smaller
        assertEquals(sizeAfterA + idsB.size(), combined.size(),
            "IDs from different transformations must not overlap");
    }

    @Test
    @DisplayName("IDs generated with different prefix configs are distinguishable by prefix segment")
    void differentPrefixes_IDsAreLexicallyDistinguishable() throws Exception {
        TestableStep step1 = new TestableStep(UUID.randomUUID().toString());
        TestableStep step2 = new TestableStep(UUID.randomUUID().toString());

        List<String> ids1 = step1.generateIds(TestableStep.metaWithPrefix("PRFX1"), 100);
        List<String> ids2 = step2.generateIds(TestableStep.metaWithPrefix("PRFX2"), 100);

        for (String id : ids1) {
            assertEquals("PRFX1", id.substring(0, 5));
        }
        for (String id : ids2) {
            assertEquals("PRFX2", id.substring(0, 5));
        }
    }

    // -----------------------------------------------------------------------
    // Row processing completeness (cases 62–64)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("zero input rows produce zero output rows without error")
    void zeroRows_producesZeroOutputs() throws Exception {
        TestableStep step = new TestableStep(UUID.randomUUID().toString());
        List<String> ids = step.generateIds(TestableStep.defaultMeta(), 0);
        assertTrue(ids.isEmpty(), "Zero input should yield zero output");
    }

    @Test
    @DisplayName("N input rows produce exactly N output rows")
    void nRows_producesExactlyNOutputs() throws Exception {
        int n = 250;
        TestableStep step = new TestableStep(UUID.randomUUID().toString());
        List<String> ids = step.generateIds(TestableStep.defaultMeta(), n);
        assertEquals(n, ids.size(), "Expected exactly " + n + " output rows");
    }

    @Test
    @DisplayName("the generated ID is appended as the last field in each output row")
    void outputRow_idIsLastField() throws Exception {
        TestableStep step = new TestableStep(UUID.randomUUID().toString());
        IdGeneratorStepMeta meta = TestableStep.metaWithPrefix("LAST0");
        // addInputRow with a non-empty input so we can verify appending
        step.addInputRow("existing_value");
        IdGeneratorStepData data = new IdGeneratorStepData();
        step.init(meta, data);
        step.processRow(meta, data);
        step.dispose(meta, data);

        Object[] outputRow = step.getOutputRows().get(0);
        String lastField = (String) outputRow[outputRow.length - 1];
        assertTrue(lastField.startsWith("LAST0"),
            "Last field should be the generated ID starting with prefix LAST0; got: " + lastField);
    }

    // -----------------------------------------------------------------------
    // Helper assertions
    // -----------------------------------------------------------------------

    private static void assertNoDuplicates(List<String> ids) {
        Set<String> seen = new HashSet<>(ids.size());
        for (String id : ids) {
            assertTrue(seen.add(id),
                "Duplicate ID detected: " + id);
        }
        assertEquals(ids.size(), seen.size(),
            "Expected " + ids.size() + " unique IDs");
    }

    private static List<String> generateSequential(int count) throws Exception {
        TestableStep step = new TestableStep(UUID.randomUUID().toString());
        return step.generateIds(TestableStep.defaultMeta(), count);
    }
}
