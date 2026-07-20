package com.yupi.yuojbackendjudgeservice.rabbitmq;

import com.yupi.yuojbackendcommon.constant.JudgeRabbitMqConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JudgeRabbitMqConfig {

    private static final int[] RETRY_DELAYS_MS = {5000, 10000, 20000};

    @Bean
    public DirectExchange judgeCodeExchange() {
        return new DirectExchange(JudgeRabbitMqConstant.CODE_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange judgeRetryExchange() {
        return new DirectExchange(JudgeRabbitMqConstant.RETRY_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange judgeDeadExchange() {
        return new DirectExchange(JudgeRabbitMqConstant.DEAD_EXCHANGE, true, false);
    }

    @Bean
    public Queue judgeCodeQueue() {
        return QueueBuilder.durable(JudgeRabbitMqConstant.CODE_QUEUE)
                .deadLetterExchange(JudgeRabbitMqConstant.DEAD_EXCHANGE)
                .deadLetterRoutingKey(JudgeRabbitMqConstant.DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding judgeCodeBinding(@Qualifier("judgeCodeQueue") Queue judgeCodeQueue,
                                    @Qualifier("judgeCodeExchange") DirectExchange judgeCodeExchange) {
        return BindingBuilder.bind(judgeCodeQueue)
                .to(judgeCodeExchange)
                .with(JudgeRabbitMqConstant.CODE_ROUTING_KEY);
    }

    @Bean
    public Queue judgeRetryQueue1() {
        return retryQueue(1, RETRY_DELAYS_MS[0]);
    }

    @Bean
    public Queue judgeRetryQueue2() {
        return retryQueue(2, RETRY_DELAYS_MS[1]);
    }

    @Bean
    public Queue judgeRetryQueue3() {
        return retryQueue(3, RETRY_DELAYS_MS[2]);
    }

    @Bean
    public Binding judgeRetryBinding1(@Qualifier("judgeRetryQueue1") Queue judgeRetryQueue1,
                                      @Qualifier("judgeRetryExchange") DirectExchange judgeRetryExchange) {
        return retryBinding(judgeRetryQueue1, judgeRetryExchange, 1);
    }

    @Bean
    public Binding judgeRetryBinding2(@Qualifier("judgeRetryQueue2") Queue judgeRetryQueue2,
                                      @Qualifier("judgeRetryExchange") DirectExchange judgeRetryExchange) {
        return retryBinding(judgeRetryQueue2, judgeRetryExchange, 2);
    }

    @Bean
    public Binding judgeRetryBinding3(@Qualifier("judgeRetryQueue3") Queue judgeRetryQueue3,
                                      @Qualifier("judgeRetryExchange") DirectExchange judgeRetryExchange) {
        return retryBinding(judgeRetryQueue3, judgeRetryExchange, 3);
    }

    @Bean
    public Queue judgeDeadQueue() {
        return QueueBuilder.durable(JudgeRabbitMqConstant.DEAD_QUEUE).build();
    }

    @Bean
    public Binding judgeDeadBinding(@Qualifier("judgeDeadQueue") Queue judgeDeadQueue,
                                    @Qualifier("judgeDeadExchange") DirectExchange judgeDeadExchange) {
        return BindingBuilder.bind(judgeDeadQueue)
                .to(judgeDeadExchange)
                .with(JudgeRabbitMqConstant.DEAD_ROUTING_KEY);
    }

    private Queue retryQueue(int retryCount, int ttlMs) {
        return QueueBuilder.durable(JudgeRabbitMqConstant.retryQueue(retryCount))
                .ttl(ttlMs)
                .deadLetterExchange(JudgeRabbitMqConstant.CODE_EXCHANGE)
                .deadLetterRoutingKey(JudgeRabbitMqConstant.CODE_ROUTING_KEY)
                .build();
    }

    private Binding retryBinding(Queue queue, DirectExchange retryExchange, int retryCount) {
        return BindingBuilder.bind(queue)
                .to(retryExchange)
                .with(JudgeRabbitMqConstant.retryRoutingKey(retryCount));
    }
}
