package com.yupi.yuojbackendcommon.constant;

/**
 * RabbitMQ topology used by the asynchronous judge pipeline.
 */
public final class JudgeRabbitMqConstant {

    public static final String CODE_EXCHANGE = "judge.code.exchange";

    public static final String CODE_QUEUE = "judge.code.queue";

    public static final String CODE_ROUTING_KEY = "judge.code.execute";

    public static final String RETRY_EXCHANGE = "judge.retry.exchange";

    public static final String RETRY_QUEUE_PREFIX = "judge.retry.queue.";

    public static final String RETRY_ROUTING_KEY_PREFIX = "judge.retry.";

    public static final String DEAD_EXCHANGE = "judge.dead.exchange";

    public static final String DEAD_QUEUE = "judge.dead.queue";

    public static final String DEAD_ROUTING_KEY = "judge.dead";

    public static final String RETRY_COUNT_HEADER = "x-judge-retry-count";

    public static final int MAX_RETRY_COUNT = 3;

    private JudgeRabbitMqConstant() {
    }

    public static String retryQueue(int retryCount) {
        return RETRY_QUEUE_PREFIX + retryCount;
    }

    public static String retryRoutingKey(int retryCount) {
        return RETRY_ROUTING_KEY_PREFIX + retryCount;
    }
}
