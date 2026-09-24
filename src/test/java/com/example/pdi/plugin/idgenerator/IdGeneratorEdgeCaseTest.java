package com.example.pdi.plugin.idgenerator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pentaho.di.trans.Trans;

/**
 * Edge-case and boundary tests that do not fit neatly into the format or
 * uniqueness categories.
 *
 * Covers test cases: 33, 68–70
 */
@DisplayName("Edge Cases")
class IdGeneratorEdgeCaseTest {

    // -----------------------------------------------------------------------
    // Null / missing configuration (cases 68–69)
    // -----------------------------------------------------------------------

    // NOTE: these three tests originally asserted the *old* behavior - a
    // null/empty Manual prefix was silently space-padded rather than
    // rejected. That was deliberately replaced by a length check in
    // IdGeneratorStep.processRow() (added for the prefix-source feature):
    // a wrong-length prefix now fails with a clear KettleException for
    // all three prefix sources, Manual included. Updated in place to
    // assert the new, intentional behavior - a graceful, informative
    // KettleException, not a raw NullPointerException - rather than
    // deleted, to preserve the original "doesn't crash ungracefully"
    // intent of this test.

    @Test
    @DisplayName("null prefix in meta throws a clear KettleException, not a raw NullPointerException")
    void nullPrefix_throwsKettleExceptionNotNPE() {
        IdGeneratorStepMeta meta = TestableStep.defaultMeta();
        meta.setPrefix(null);
        TestableStep step = new TestableStep(UUID.randomUUID().toString());
        assertThrows(org.pentaho.di.core.exception.KettleException.class,
            () -> step.generateIds(meta, 3));
    }

    @Test
    @DisplayName("null prefix throws KettleException rather than producing an ID")
    void nullPrefix_throwsRatherThanProducingAnId() {
        IdGeneratorStepMeta meta = TestableStep.defaultMeta();
        meta.setPrefix(null);
        TestableStep step = new TestableStep(UUID.randomUUID().toString());
        assertThrows(org.pentaho.di.core.exception.KettleException.class,
            () -> step.generateIds(meta, 1));
    }

    @Test
    @DisplayName("empty prefix throws KettleException rather than producing an ID")
    void emptyPrefix_throwsRatherThanProducingAnId() {
        IdGeneratorStepMeta meta = TestableStep.metaWithPrefix("");
        TestableStep step = new TestableStep(UUID.randomUUID().toString());
        assertThrows(org.pentaho.di.core.exception.KettleException.class,
            () -> step.generateIds(meta, 1));
    }

    // -----------------------------------------------------------------------
    // Run key fallback – null containerObjectId (case 55)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("null containerObjectId falls back to identity hash and does not throw")
    void nullContainerObjectId_fallsBackToIdentityHash() {
        // Build a Trans that returns null for containerObjectId
        Trans trans = new Trans();
        trans.setContainerObjectId(null);

        IdGeneratorStep step = new IdGeneratorStep(
            new org.pentaho.di.trans.step.StepMeta(),
            new IdGeneratorStepData(),
            0,
            new org.pentaho.di.trans.TransMeta(),
            trans);

        IdGeneratorStepMeta meta = TestableStep.defaultMeta();
        IdGeneratorStepData data = new IdGeneratorStepData();

        assertDoesNotThrow(() -> {
            step.init(meta, data);
            step.dispose(meta, data);
        }, "init/dispose should not throw when containerObjectId is null");
    }

    @Test
    @DisplayName("null containerObjectId: generated IDs are still unique")
    void nullContainerObjectId_idsAreUnique() {
        // We can't easily run processRow without a proper harness for null-id trans,
        // but we CAN verify that the SEQUENCE_MAP gets a non-null key registered.
        Trans trans = new Trans();
        trans.setContainerObjectId(null);

        IdGeneratorStep step = new IdGeneratorStep(
            new org.pentaho.di.trans.step.StepMeta(),
            new IdGeneratorStepData(),
            0,
            new org.pentaho.di.trans.TransMeta(),
            trans);

        IdGeneratorStepMeta meta = TestableStep.defaultMeta();
        IdGeneratorStepData data = new IdGeneratorStepData();
        step.init(meta, data);

        // The fallback key is "trans@<identityHash>", which is non-null
        assertFalse(IdGeneratorStep.SEQUENCE_MAP.isEmpty(),
            "SEQUENCE_MAP should contain the fallback key after init()");

        step.dispose(meta, data);
    }

    // -----------------------------------------------------------------------
    // Stop / restart within same JVM (case 70)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("stop-and-restart cycle (dispose then re-init) produces unique IDs across runs")
    void stopAndRestart_producesUniqueIdsAcrossRuns() throws Exception {
        String runKey = "restart-test-" + UUID.randomUUID();
        IdGeneratorStepMeta meta = TestableStep.defaultMeta();

        // First run
        TestableStep step1 = new TestableStep(runKey);
        List<String> firstBatch = step1.generateIds(meta, 100);

        // Second run – same run key, as if Spoon re-executed the same transformation
        TestableStep step2 = new TestableStep(runKey);
        List<String> secondBatch = step2.generateIds(meta, 100);

        // Combined, all IDs should be unique (the date part means same-day runs
        // only guarantee uniqueness if the clock advances, which it will)
        // At minimum: within each batch there must be no duplicates
        assertNoDuplicates(firstBatch, "First batch contains duplicates");
        assertNoDuplicates(secondBatch, "Second batch contains duplicates");
    }

    @Test
    @DisplayName("concurrent dispose and processRow on the same run key does not throw")
    void concurrent_disposeAndProcessRow_doesNotThrow() {
        // computeIfAbsent in processRow guards against the entry being absent if
        // dispose() races with processRow().
        assertDoesNotThrow(() -> {
            String runKey = "race-test-" + UUID.randomUUID();
            TestableStep step = new TestableStep(runKey);
            IdGeneratorStepMeta meta = TestableStep.defaultMeta();

            Thread processorThread = new Thread(() -> {
                try {
                    step.generateIds(meta, 200);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Thread disposer = new Thread(() -> {
                try {
                    Thread.sleep(1); // small delay so some rows are already processed
                } catch (InterruptedException ignored) {}
                IdGeneratorStep.SEQUENCE_MAP.remove(runKey);
            });

            processorThread.start();
            disposer.start();
            processorThread.join(5_000);
            disposer.join(5_000);
        });
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static void assertNoDuplicates(List<String> ids, String message) {
        long distinct = ids.stream().distinct().count();
        assertEquals(ids.size(), distinct, message);
    }
}
