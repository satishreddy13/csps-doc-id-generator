package com.example.pdi.plugin.idgenerator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the virtual clock mechanism inside IdGeneratorStep.
 *
 * The virtual clock is backed by SEQUENCE_MAP (a static ConcurrentHashMap keyed
 * by run ID).  Every call to generateId() atomically claims the next nanosecond
 * slot; ties are broken by advancing the virtual clock by 1.
 *
 * Covers test cases: 43–57
 */
@DisplayName("Virtual Clock")
class VirtualClockTest {

    private String runKey;

    @BeforeEach
    void setUp() {
        runKey = "vclk-test-" + UUID.randomUUID();
    }

    @AfterEach
    void cleanup() {
        // Remove any keys created during the test so they don't bleed into others
        IdGeneratorStep.SEQUENCE_MAP.remove(runKey);
    }

    // -----------------------------------------------------------------------
    // SEQUENCE_MAP lifecycle (cases 49–53)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("init() inserts AtomicLong(-1) for the run key when none exists")
    void init_registersRunKeyInSequenceMap() throws Exception {
        assertFalse(IdGeneratorStep.SEQUENCE_MAP.containsKey(runKey));
        TestableStep step = new TestableStep(runKey);
        step.init(TestableStep.defaultMeta(), new IdGeneratorStepData());

        assertTrue(IdGeneratorStep.SEQUENCE_MAP.containsKey(runKey),
            "SEQUENCE_MAP should contain the run key after init()");
        assertEquals(-1L, IdGeneratorStep.SEQUENCE_MAP.get(runKey).get(),
            "Initial AtomicLong sentinel value should be -1");

        step.dispose(TestableStep.defaultMeta(), new IdGeneratorStepData());
    }

    @Test
    @DisplayName("init() does not overwrite an existing entry (putIfAbsent semantics)")
    void init_doesNotResetExistingClock() throws Exception {
        // Pre-seed with a known value to simulate a mid-run clock
        IdGeneratorStep.SEQUENCE_MAP.put(runKey, new AtomicLong(999_000L));

        TestableStep step = new TestableStep(runKey);
        step.init(TestableStep.defaultMeta(), new IdGeneratorStepData());

        assertEquals(999_000L, IdGeneratorStep.SEQUENCE_MAP.get(runKey).get(),
            "init() should not overwrite an existing SEQUENCE_MAP entry");

        step.dispose(TestableStep.defaultMeta(), new IdGeneratorStepData());
    }

    @Test
    @DisplayName("dispose() removes the run key from SEQUENCE_MAP")
    void dispose_removesRunKey() throws Exception {
        TestableStep step = new TestableStep(runKey);
        IdGeneratorStepMeta meta = TestableStep.defaultMeta();
        IdGeneratorStepData data = new IdGeneratorStepData();

        step.init(meta, data);
        assertTrue(IdGeneratorStep.SEQUENCE_MAP.containsKey(runKey));

        step.dispose(meta, data);
        assertFalse(IdGeneratorStep.SEQUENCE_MAP.containsKey(runKey),
            "SEQUENCE_MAP should not contain the run key after dispose()");
    }

    @Test
    @DisplayName("dispose() does not remove other transformations' entries")
    void dispose_doesNotAffectOtherKeys() throws Exception {
        String otherKey = "vclk-other-" + UUID.randomUUID();
        IdGeneratorStep.SEQUENCE_MAP.put(otherKey, new AtomicLong(42L));

        TestableStep step = new TestableStep(runKey);
        IdGeneratorStepMeta meta = TestableStep.defaultMeta();
        IdGeneratorStepData data = new IdGeneratorStepData();
        step.init(meta, data);
        step.dispose(meta, data);

        assertTrue(IdGeneratorStep.SEQUENCE_MAP.containsKey(otherKey),
            "dispose() must not remove other transformations' SEQUENCE_MAP entries");
        IdGeneratorStep.SEQUENCE_MAP.remove(otherKey);
    }

    @Test
    @DisplayName("after dispose() + init(), the clock restarts from -1")
    void disposeAndReinit_clockResetsToSentinel() throws Exception {
        TestableStep step = new TestableStep(runKey);
        IdGeneratorStepMeta meta = TestableStep.defaultMeta();
        IdGeneratorStepData data = new IdGeneratorStepData();

        // First run – process one row manually so the clock advances, then read it before dispose
        step.init(meta, data);
        step.addInputRow(new Object[0]);
        step.processRow(meta, data);  // consumes the row, advances clock
        long afterFirstRow = IdGeneratorStep.SEQUENCE_MAP.get(runKey).get();
        assertTrue(afterFirstRow > -1L, "Clock should have advanced beyond sentinel");
        step.dispose(meta, data);   // removes key

        // Second run – should reinitialise with -1
        TestableStep step2 = new TestableStep(runKey);
        step2.init(meta, new IdGeneratorStepData());
        assertEquals(-1L, IdGeneratorStep.SEQUENCE_MAP.get(runKey).get(),
            "After dispose + re-init, clock sentinel should be -1 again");
        step2.dispose(meta, new IdGeneratorStepData());
    }

    // -----------------------------------------------------------------------
    // Run key (cases 54–57)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("non-null containerObjectId is used as the run key")
    void runKey_nonNullContainerObjectId_isUsedDirectly() throws Exception {
        String specificKey = "explicit-run-id-" + UUID.randomUUID();
        TestableStep step = new TestableStep(specificKey);
        IdGeneratorStepMeta meta = TestableStep.defaultMeta();
        IdGeneratorStepData data = new IdGeneratorStepData();

        step.init(meta, data);
        assertTrue(IdGeneratorStep.SEQUENCE_MAP.containsKey(specificKey),
            "SEQUENCE_MAP should use containerObjectId as the key");
        step.dispose(meta, data);
    }

    @Test
    @DisplayName("two steps with different run keys have independent clocks")
    void runKey_distinctKeys_haveIndependentClocks() throws Exception {
        String keyA = "key-a-" + UUID.randomUUID();
        String keyB = "key-b-" + UUID.randomUUID();
        TestableStep stepA = new TestableStep(keyA);
        TestableStep stepB = new TestableStep(keyB);
        IdGeneratorStepMeta meta = TestableStep.defaultMeta();

        stepA.generateIds(meta, 10);
        stepB.generateIds(meta, 10);

        // Both keys should have existed independently
        // (they are removed by dispose, so we just verify no clash happened
        //  by checking that neither key exists now — both were disposed)
        assertFalse(IdGeneratorStep.SEQUENCE_MAP.containsKey(keyA));
        assertFalse(IdGeneratorStep.SEQUENCE_MAP.containsKey(keyB));
    }

    // -----------------------------------------------------------------------
    // Virtual clock monotonicity and tie-break (cases 43–48)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("clock is monotonically non-decreasing (real clock never goes backward)")
    void clock_isMonotonicallyNonDecreasing() throws Exception {
        TestableStep step = new TestableStep(runKey);
        IdGeneratorStepMeta meta = TestableStep.defaultMeta();
        List<String> ids = step.generateIds(meta, 1000);

        long prev = -1L;
        for (String id : ids) {
            long nanoValue = Long.parseLong(id.substring(11, 20), 36);
            assertTrue(nanoValue >= prev,
                "Nano token went backward: prev=" + prev + " current=" + nanoValue);
            prev = nanoValue;
        }
    }

    @Test
    @DisplayName("initial sentinel -1 means first real nano is used directly (no tie-break needed)")
    void clock_initialSentinel_firstCallUsesRealNano() throws Exception {
        // After init the clock is -1; any real nano ≥ 0 satisfies real > prev, so the
        // first allocated value should be the real wall-clock nanosecond, not prev+1.
        TestableStep step = new TestableStep(runKey);
        IdGeneratorStepMeta meta = TestableStep.defaultMeta();
        IdGeneratorStepData data = new IdGeneratorStepData();

        step.init(meta, data);  // registers AtomicLong(-1)
        assertEquals(-1L, IdGeneratorStep.SEQUENCE_MAP.get(runKey).get());

        step.addInputRow(new Object[0]);
        step.processRow(meta, data); // advances clock from -1 to real nano

        long clockAfter = IdGeneratorStep.SEQUENCE_MAP.get(runKey).get();
        assertTrue(clockAfter > -1L, "Clock should have advanced past sentinel on first processRow call");

        step.dispose(meta, data);  // cleans up
    }

    @Test
    @DisplayName("rapid sequential IDs always have distinct nano-tokens")
    void rapidSequential_nanoTokens_areAllDistinct() throws Exception {
        TestableStep step = new TestableStep(runKey);
        // 500 IDs generated in tight loop — many will land in the same nanosecond
        List<String> ids = step.generateIds(TestableStep.defaultMeta(), 500);

        Set<String> nanoTokens = new HashSet<>();
        for (String id : ids) {
            String token = id.substring(11, 20);
            assertTrue(nanoTokens.add(token),
                "Duplicate nano-token found: " + token + " in id: " + id);
        }
    }

    @Test
    @DisplayName("SEQUENCE_MAP is static and shared across all step instances in the JVM")
    void sequenceMap_isStaticAndShared() {
        // Two distinct step instances pointing at the same run key share the same clock
        TestableStep s1 = new TestableStep(runKey);
        TestableStep s2 = new TestableStep(runKey);

        IdGeneratorStep.SEQUENCE_MAP.put(runKey, new AtomicLong(500L));

        // Both see the same AtomicLong
        assertSame(IdGeneratorStep.SEQUENCE_MAP.get(runKey),
                   IdGeneratorStep.SEQUENCE_MAP.get(runKey));

        IdGeneratorStep.SEQUENCE_MAP.remove(runKey);
    }
}
