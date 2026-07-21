package com.yupi.yuojcodesandbox.utils;

import cn.hutool.core.util.StrUtil;
import com.yupi.yuojcodesandbox.model.ExecuteMessage;
import org.springframework.util.StopWatch;

import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * 进程工具类
 */
public class ProcessUtils {

    public static final int DEFAULT_MAX_OUTPUT_BYTES = 1024 * 1024;

    public static final String TIME_LIMIT_EXCEEDED = "Time Limit Exceeded";

    public static final String OUTPUT_LIMIT_EXCEEDED = "Output Limit Exceeded";

    /**
     * 执行进程并获取信息
     *
     * @param runProcess
     * @param opName
     * @return
     */
    public static ExecuteMessage runProcessAndGetMessage(Process runProcess, String opName) {
        return runProcessAndGetMessage(runProcess, opName, 0L, DEFAULT_MAX_OUTPUT_BYTES);
    }

    /**
     * Executes a process while draining stdout/stderr concurrently. The process is
     * forcibly terminated as soon as it exceeds either the time or output limit.
     */
    public static ExecuteMessage runProcessAndGetMessage(Process runProcess, String opName,
                                                         long timeoutMs, int maxOutputBytes) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        LimitedOutputCollector outputCollector = new LimitedOutputCollector(maxOutputBytes);
        Thread stdoutReader = startReader(runProcess, runProcess.getInputStream(), outputCollector,
                false, opName + "-stdout");
        Thread stderrReader = startReader(runProcess, runProcess.getErrorStream(), outputCollector,
                true, opName + "-stderr");
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        try {
            boolean completed;
            if (timeoutMs > 0) {
                completed = runProcess.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            } else {
                runProcess.waitFor();
                completed = true;
            }
            if (!completed) {
                destroyProcess(runProcess);
                executeMessage.setErrorMessage(TIME_LIMIT_EXCEEDED);
            }
            joinReader(stdoutReader);
            joinReader(stderrReader);
            if (outputCollector.isLimitExceeded()) {
                executeMessage.setErrorMessage(OUTPUT_LIMIT_EXCEEDED);
            } else if (executeMessage.getErrorMessage() == null && !outputCollector.getStderr().isEmpty()) {
                executeMessage.setErrorMessage(outputCollector.getStderr());
            }
            executeMessage.setMessage(outputCollector.getStdout());
            executeMessage.setExitValue(safeExitValue(runProcess));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            destroyProcess(runProcess);
            executeMessage.setErrorMessage("Process interrupted");
        } finally {
            if (stopWatch.isRunning()) {
                stopWatch.stop();
            }
            executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
        }
        return executeMessage;
    }

    private static Thread startReader(Process process, InputStream inputStream,
                                      LimitedOutputCollector collector, boolean errorStream,
                                      String threadName) {
        Thread reader = new Thread(() -> {
            byte[] buffer = new byte[4096];
            try (InputStream stream = inputStream) {
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    byte[] payload = new byte[read];
                    System.arraycopy(buffer, 0, payload, 0, read);
                    if (!collector.append(payload, errorStream)) {
                        destroyProcess(process);
                        break;
                    }
                }
            } catch (IOException ignored) {
                // Stream closure is expected when a timed-out or noisy process is killed.
            }
        }, threadName);
        reader.setDaemon(true);
        reader.start();
        return reader;
    }

    private static void joinReader(Thread reader) throws InterruptedException {
        reader.join(1000L);
    }

    private static int safeExitValue(Process process) {
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException e) {
            return -1;
        }
    }

    private static void destroyProcess(Process process) {
        if (!process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(200L, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(1L, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    /**
     * 执行交互式进程并获取信息
     *
     * @param runProcess
     * @param args
     * @return
     */
    public static ExecuteMessage runInteractProcessAndGetMessage(Process runProcess, String args) {
        ExecuteMessage executeMessage = new ExecuteMessage();

        try {
            // 向控制台输入程序
            OutputStream outputStream = runProcess.getOutputStream();
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
            String[] s = args.split(" ");
            String join = StrUtil.join("\n", s) + "\n";
            outputStreamWriter.write(join);
            // 相当于按了回车，执行输入的发送
            outputStreamWriter.flush();

            // 分批获取进程的正常输出
            InputStream inputStream = runProcess.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder compileOutputStringBuilder = new StringBuilder();
            // 逐行读取
            String compileOutputLine;
            while ((compileOutputLine = bufferedReader.readLine()) != null) {
                compileOutputStringBuilder.append(compileOutputLine);
            }
            executeMessage.setMessage(compileOutputStringBuilder.toString());
            // 记得资源的释放，否则会卡死
            outputStreamWriter.close();
            outputStream.close();
            inputStream.close();
            runProcess.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return executeMessage;
    }
}
