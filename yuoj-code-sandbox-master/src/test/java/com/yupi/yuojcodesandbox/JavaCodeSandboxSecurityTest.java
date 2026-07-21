package com.yupi.yuojcodesandbox;

import com.github.dockerjava.api.model.HostConfig;
import com.yupi.yuojcodesandbox.model.ExecuteCodeRequest;
import com.yupi.yuojcodesandbox.model.ExecuteMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaCodeSandboxSecurityTest {

    @TempDir
    Path tempDir;

    @Test
    void dockerHostConfigShouldLimitProcessCount() {
        JavaDockerCodeSandbox sandbox = new JavaDockerCodeSandbox();

        HostConfig hostConfig = sandbox.createHostConfig(tempDir.toString());

        assertEquals(64L, hostConfig.getPidsLimit());
        assertEquals(1L, hostConfig.getCpuCount());
        assertEquals(100_000_000L, hostConfig.getMemory());
    }

    @Test
    void shouldDeleteTemporaryDirectoryWhenCompilationFails() {
        FailingCompileSandbox sandbox = new FailingCompileSandbox();
        ExecuteCodeRequest request = new ExecuteCodeRequest();
        request.setCode("public class Main {}");

        assertThrows(RuntimeException.class, () -> sandbox.executeCode(request));
        assertFalse(sandbox.userCodeFile.getParentFile().exists());
    }

    private static class FailingCompileSandbox extends JavaCodeSandboxTemplate {

        private File userCodeFile;

        @Override
        public ExecuteMessage compileFile(File userCodeFile) {
            this.userCodeFile = userCodeFile;
            throw new RuntimeException("simulated compile failure");
        }
    }
}
