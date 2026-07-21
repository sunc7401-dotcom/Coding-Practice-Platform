package com.yupi.yuojcodesandbox.utils;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitedOutputCollectorTest {

    @Test
    void shouldEnforceCombinedStdoutAndStderrLimit() {
        LimitedOutputCollector collector = new LimitedOutputCollector(10);

        assertTrue(collector.append("123456".getBytes(StandardCharsets.UTF_8), false));
        assertFalse(collector.append("abcdef".getBytes(StandardCharsets.UTF_8), true));

        assertTrue(collector.isLimitExceeded());
        assertEquals(10, collector.getCollectedBytes());
        assertEquals("123456", collector.getStdout());
        assertEquals("abcd", collector.getStderr());
    }
}
