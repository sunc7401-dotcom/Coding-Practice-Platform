package com.yupi.yuojbackendquestionservice.config;

import com.yupi.yuojbackendcommon.constant.JudgeRabbitMqConstant;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitPublisherConfig {

    @Bean
    public DirectExchange judgeCodeExchange() {
        return new DirectExchange(JudgeRabbitMqConstant.CODE_EXCHANGE, true, false);
    }
}
