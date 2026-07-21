package com.yupi.yuojcodesandbox.utils;

import com.yupi.yuojcodesandbox.model.ExecuteMessage;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessUtilsTest {

    @Test
    void shouldForciblyTerminateTimedOutProcess() throws Exception {
        Process process = startHelper("sleep");

        ExecuteMessage result = ProcessUtils.runProcessAndGetMessage(process, "timeout-test", 200L, 1024);

        assertEquals(ProcessUtils.TIME_LIMIT_EXCEEDED, result.getErrorMessage());
        assertFalse(process.isAlive());
        assertTrue(result.getTime() < 3000L);
    }

    @Test
    void shouldTerminateProcessAsSoonAsOutputLimitIsExceeded() throws Exception {
        Process process = startHelper("flood");

        ExecuteMessage result = ProcessUtils.runProcessAndGetMessage(process, "output-test", 5000L, 1024);

        assertEquals(ProcessUtils.OUTPUT_LIMIT_EXCEEDED, result.getErrorMessage());
        assertFalse(process.isAlive());
        assertTrue(result.getMessage().getBytes(StandardCharsets.UTF_8).length <= 1024);
    }

    private Process startHelper(String mode) throws Exception {
        String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        String javaPath = new File(new File(System.getProperty("java.home"), "bin"), executable)
                .getAbsolutePath();
        return new ProcessBuilder(javaPath, "-cp", System.getProperty("java.class.path"),
                ProcessTestProgram.class.getName(), mode).start();
    }
}
