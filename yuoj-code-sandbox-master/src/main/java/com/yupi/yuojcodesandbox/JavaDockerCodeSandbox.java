package com.yupi.yuojcodesandbox;

import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.yupi.yuojcodesandbox.model.ExecuteMessage;
import com.yupi.yuojcodesandbox.utils.LimitedOutputCollector;
import com.yupi.yuojcodesandbox.utils.ProcessUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {

    private static final long TIME_OUT = 5L;

    private static final long MAX_PIDS = 64L;

    private static final int MAX_OUTPUT_BYTES = 1024 * 1024;

    private static final Boolean FIRST_INIT = true;

//    public static void main(String[] args) {
//        JavaDockerCodeSandbox javaNativeCodeSandbox = new JavaDockerCodeSandbox();
//        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
//        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
//        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
////        String code = ResourceUtil.readStr("testCode/unsafeCode/RunFileError.java", StandardCharsets.UTF_8);
////        String code = ResourceUtil.readStr("testCode/simpleCompute/Main.java", StandardCharsets.UTF_8);
//        executeCodeRequest.setCode(code);
//        executeCodeRequest.setLanguage("java");
//        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
//        System.out.println(executeCodeResponse);
//    }

    /**
     * 3、创建容器，把文件复制到容器内
     * @param userCodeFile
     * @param inputList
     * @return
     */
    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        // 获取默认的 Docker Client，适用于linux
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
//       windows连接docker
//        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
//                .withDockerHost("tcp://localhost:2375")
//                .build();
//
//        DockerHttpClient httpClient = new JerseyDockerHttpClient.Builder()
//                .dockerHost(config.getDockerHost())
//                .build();
//
//        DockerClient dockerClient = DockerClientBuilder.getInstance(config)
//                .withDockerHttpClient(httpClient)
//                .build();

        // 拉取镜像
//        String image = "openjdk:8-alpine";
        String image = "eclipse-temurin:8-jdk-alpine";

        if (FIRST_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
        }

        System.out.println("下载完成");

        // 创建容器

        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = createHostConfig(userCodeParentPath);
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withReadonlyRootfs(true)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(false)
                .withCmd("sh", "-c", "while true; do sleep 3600; done")
                .exec();
        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();

        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();

        try {
            // docker exec keen_blackwell java -cp /app Main 1 3
            // 执行命令并获取结果
            List<ExecuteMessage> executeMessageList = new ArrayList<>();
            for (String inputArgs : inputList) {
                StopWatch stopWatch = new StopWatch();
                String[] inputArgsArray = inputArgs.split(" ");
                String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
                ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                        .withCmd(cmdArray)
                        .withAttachStderr(true)
                        .withAttachStdin(true)
                        .withAttachStdout(true)
                        .exec();
                System.out.println("创建执行命令：" + execCreateCmdResponse);

                ExecuteMessage executeMessage = new ExecuteMessage();
                LimitedOutputCollector outputCollector = new LimitedOutputCollector(MAX_OUTPUT_BYTES);
                AtomicBoolean outputLimitExceeded = new AtomicBoolean(false);
                String execId = execCreateCmdResponse.getId();
                ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                    @Override
                    public void onNext(Frame frame) {
                        StreamType streamType = frame.getStreamType();
                        boolean errorStream = StreamType.STDERR.equals(streamType);
                        if (!outputCollector.append(frame.getPayload(), errorStream)
                                && outputLimitExceeded.compareAndSet(false, true)) {
                            killContainerQuietly(dockerClient, containerId, "output limit exceeded");
                        }
                        super.onNext(frame);
                    }
                };

                final long[] maxMemory = {0L};
                CountDownLatch memoryLatch = new CountDownLatch(1);

                // 获取占用的内存
                StatsCmd statsCmd = dockerClient.statsCmd(containerId);
                ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {

                    private Closeable closeable;

                    @Override
                    public void onNext(Statistics statistics) {
                        System.out.println("内存占用：" + statistics.getMemoryStats().getUsage());
                        maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
                        memoryLatch.countDown();
                    }

                    @Override
                    public void close() throws IOException {
                        if (closeable != null) {
                            closeable.close();
                        }
                    }

                    @Override
                    public void onStart(Closeable closeable) {
                        this.closeable = closeable;
                    }

                    @Override
                    public void onError(Throwable throwable) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
                boolean timedOut = false;
                try {
                    memoryLatch.await(200, TimeUnit.MILLISECONDS);
                    stopWatch.start();
                    boolean completed = dockerClient.execStartCmd(execId)
                            .exec(execStartResultCallback)
                            .awaitCompletion(TIME_OUT, TimeUnit.SECONDS);
                    if (!completed && !outputLimitExceeded.get()) {
                        timedOut = true;
                        killContainerQuietly(dockerClient, containerId, "time limit exceeded");
                        execStartResultCallback.awaitCompletion(1L, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    killContainerQuietly(dockerClient, containerId, "judge thread interrupted");
                    throw new RuntimeException(e);
                } finally {
                    if (stopWatch.isRunning()) {
                        stopWatch.stop();
                    }
                    closeQuietly(statisticsResultCallback);
                    statsCmd.close();
                    closeQuietly(execStartResultCallback);
                }
                executeMessage.setMessage(outputCollector.getStdout().trim());
                if (outputLimitExceeded.get()) {
                    executeMessage.setErrorMessage(ProcessUtils.OUTPUT_LIMIT_EXCEEDED);
                } else if (timedOut) {
                    executeMessage.setErrorMessage(ProcessUtils.TIME_LIMIT_EXCEEDED);
                } else if (!outputCollector.getStderr().trim().isEmpty()) {
                    executeMessage.setErrorMessage(outputCollector.getStderr().trim());
                }
                executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
                executeMessage.setMemory(maxMemory[0]/1024/1024);
                executeMessageList.add(executeMessage);
                if (timedOut || outputLimitExceeded.get()) {
                    break;
                }
            }
            return executeMessageList;
        } finally {
            try {
                dockerClient.stopContainerCmd(containerId).exec();
            } catch (Exception e) {
                System.out.println("停止容器失败：" + e.getMessage());
            }
            try {
                dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            } catch (Exception e) {
                System.out.println("删除容器失败：" + e.getMessage());
            }
            try {
                dockerClient.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    HostConfig createHostConfig(String userCodeParentPath) {
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(100 * 1000 * 1000L);
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        hostConfig.withPidsLimit(MAX_PIDS);
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        return hostConfig;
    }

    private void killContainerQuietly(DockerClient dockerClient, String containerId, String reason) {
        try {
            dockerClient.killContainerCmd(containerId).exec();
        } catch (Exception e) {
            System.out.println("终止容器失败（" + reason + "）：" + e.getMessage());
        }
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }
}


