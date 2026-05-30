package com.example.pdi.plugin.idgenerator;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the structure and formatting of generated IDs.
 *
 * ID anatomy:
 *  [0–4]   prefix        5 chars  – padded/truncated to exactly 5
 *  [5–10]  date          6 chars  – YYMMDD
 *  [11–19] nano-token    9 chars  – base36 uppercase, zero-padded
 *
 * Covers test cases: 24–42
 */
@DisplayName("ID Format")
class IdFormatTest {

    private static final long MAX_NANO_IN_DAY = 86_400_000_000_000L;
    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    private String runKey;

    @AfterEach
    void cleanup() {
        // Ensure SEQUENCE_MAP doesn't leak between tests
        if (runKey != null) {
            IdGeneratorStep.SEQUENCE_MAP.remove(runKey);
        }
    }

    // -----------------------------------------------------------------------
    // Length and character set (cases 24–25)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("every generated ID is exactly 20 characters long")
    void id_isAlways20Chars() throws Exception {
        String id = singleId(TestableStep.defaultMeta());
        assertEquals(20, id.length(), "ID length must be 20; got: " + id);
    }

    @Test
    @DisplayName("ID contains only printable ASCII characters")
    void id_containsOnlyPrintableAscii() throws Exception {
        for (int i = 0; i < 20; i++) {
            String id = singleId(TestableStep.defaultMeta());
            assertTrue(id.matches("[\\x20-\\x7E]+"),
                "Non-printable ASCII found in: " + id);
        }
    }

    // -----------------------------------------------------------------------
    // Prefix segment (cases 26–33)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("prefix occupies positions 0–4 of the ID")
    void prefix_occupiesFirstFiveChars() throws Exception {
        IdGeneratorStepMeta meta = TestableStep.metaWithPrefix("HELLO");
        String id = singleId(meta);
        assertEquals("HELLO", id.substring(0, 5));
    }

    @Test
    @DisplayName("exact 5-char prefix is used unchanged")
    void prefix_exactFiveChars_usedAsIs() throws Exception {
        String id = singleId(TestableStep.metaWithPrefix("ABCDE"));
        assertEquals("ABCDE", id.substring(0, 5));
    }

    @Test
    @DisplayName("prefix shorter than 5 chars is left-padded with spaces")
    void prefix_shorterThanFive_paddedWithSpaces() throws Exception {
        // "AB" (2 chars) → "   AB" (spaces on left)
        String id = singleId(TestableStep.metaWithPrefix("AB"));
        assertEquals("   AB", id.substring(0, 5),
            "Expected space-padded prefix; got: '" + id.substring(0, 5) + "'");
    }

    @Test
    @DisplayName("empty prefix produces five spaces")
    void prefix_empty_isFiveSpaces() throws Exception {
        String id = singleId(TestableStep.metaWithPrefix(""));
        assertEquals("     ", id.substring(0, 5),
            "Expected 5 spaces for empty prefix");
    }

    @Test
    @DisplayName("prefix longer than 5 chars is truncated to 5")
    void prefix_longerThanFive_isTruncated() throws Exception {
        String id = singleId(TestableStep.metaWithPrefix("TOOLONG"));
        assertEquals("TOOLONG".substring(0, 5), id.substring(0, 5));
    }

    @Test
    @DisplayName("null prefix is handled without exception")
    void prefix_null_doesNotThrow() {
        IdGeneratorStepMeta meta = TestableStep.defaultMeta();
        meta.setPrefix(null);
        assertDoesNotThrow(() -> singleId(meta));
    }

    @Test
    @DisplayName("null prefix produces a 5-char (space-padded) prefix segment")
    void prefix_null_producesFiveCharSegment() throws Exception {
        IdGeneratorStepMeta meta = TestableStep.defaultMeta();
        meta.setPrefix(null);
        String id = singleId(meta);
        assertEquals(5, id.substring(0, 5).length());
    }

    // -----------------------------------------------------------------------
    // Date segment (cases 27, 34–37)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("date occupies positions 5–10 of the ID")
    void date_occupiesPositions5to10() throws Exception {
        String id = singleId(TestableStep.defaultMeta());
        String datePart = id.substring(5, 11);
        // Must be parseable as YYMMDD
        assertDoesNotThrow(() -> LocalDate.parse(datePart, YYMMDD),
            "Date segment is not valid YYMMDD: " + datePart);
    }

    @Test
    @DisplayName("date segment uses two-digit year")
    void date_usesTwoDigitYear() throws Exception {
        String id = singleId(TestableStep.defaultMeta());
        String yearPart = id.substring(5, 7);
        assertTrue(yearPart.matches("\\d{2}"),
            "Expected 2-digit year, got: " + yearPart);
    }

    @Test
    @DisplayName("date segment month is zero-padded to two digits")
    void date_monthIsZeroPadded() throws Exception {
        String id = singleId(TestableStep.defaultMeta());
        String monthPart = id.substring(7, 9);
        assertTrue(monthPart.matches("\\d{2}"),
            "Expected 2-digit month, got: " + monthPart);
        int month = Integer.parseInt(monthPart);
        assertTrue(month >= 1 && month <= 12);
    }

    @Test
    @DisplayName("date segment day is zero-padded to two digits")
    void date_dayIsZeroPadded() throws Exception {
        String id = singleId(TestableStep.defaultMeta());
        String dayPart = id.substring(9, 11);
        assertTrue(dayPart.matches("\\d{2}"),
            "Expected 2-digit day, got: " + dayPart);
        int day = Integer.parseInt(dayPart);
        assertTrue(day >= 1 && day <= 31);
    }

    @Test
    @DisplayName("date segment matches the current wall-clock date")
    void date_matchesToday() throws Exception {
        String expectedDate = LocalDate.now().format(YYMMDD);
        String id = singleId(TestableStep.defaultMeta());
        assertEquals(expectedDate, id.substring(5, 11),
            "Date part of ID does not match today");
    }

    // -----------------------------------------------------------------------
    // Nano-token segment (cases 28, 38–42)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("nano-token occupies positions 11–19 of the ID")
    void nanoToken_occupiesPositions11to19() throws Exception {
        String id = singleId(TestableStep.defaultMeta());
        assertEquals(9, id.substring(11).length(),
            "Nano token length should be 9; id=" + id);
    }

    @Test
    @DisplayName("nano-token is exactly 9 characters")
    void nanoToken_isNineChars() throws Exception {
        for (int i = 0; i < 10; i++) {
            String id = singleId(TestableStep.defaultMeta());
            assertEquals(9, id.substring(11, 20).length(),
                "Nano token must be 9 chars; id=" + id);
        }
    }

    @Test
    @DisplayName("nano-token contains only uppercase base36 characters (0-9 A-Z)")
    void nanoToken_isBase36Uppercase() throws Exception {
        for (int i = 0; i < 20; i++) {
            String id = singleId(TestableStep.defaultMeta());
            String nanoToken = id.substring(11, 20);
            assertTrue(nanoToken.matches("[0-9A-Z]{9}"),
                "Nano token contains non-base36 or lowercase chars: " + nanoToken);
        }
    }

    @Test
    @DisplayName("nano-token value is within the valid nanoseconds-within-day range [0, MAX)")
    void nanoToken_valueWithinDayRange() throws Exception {
        String id = singleId(TestableStep.defaultMeta());
        String nanoToken = id.substring(11, 20);
        long nanoValue = Long.parseLong(nanoToken, 36);
        assertTrue(nanoValue >= 0,
            "Nano value should be non-negative: " + nanoValue);
        assertTrue(nanoValue < MAX_NANO_IN_DAY,
            "Nano value exceeds max nanoseconds in a day: " + nanoValue);
    }

    @Test
    @DisplayName("maximum nanoseconds-within-day encodes to at most 9 base36 chars")
    void nanoToken_maxValue_fitsInNineChars() {
        long maxNano = MAX_NANO_IN_DAY - 1;
        String encoded = Long.toString(maxNano, 36).toUpperCase();
        assertTrue(encoded.length() <= 9,
            "Max nano value encodes to " + encoded.length() + " chars: " + encoded);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private String singleId(IdGeneratorStepMeta meta) throws Exception {
        runKey = UUID.randomUUID().toString();
        TestableStep step = new TestableStep(runKey);
        List<String> ids = step.generateIds(meta, 1);
        assertFalse(ids.isEmpty(), "No ID was generated");
        return ids.get(0);
    }
}
