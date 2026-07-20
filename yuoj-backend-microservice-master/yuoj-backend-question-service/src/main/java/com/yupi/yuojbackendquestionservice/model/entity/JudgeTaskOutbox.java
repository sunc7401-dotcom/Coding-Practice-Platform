package com.yupi.yuojbackendquestionservice.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Transactional outbox event for a judge task.
 */
@Data
@TableName("judge_task_outbox")
public class JudgeTaskOutbox implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long aggregateId;

    private String eventType;

    private String exchangeName;

    private String routingKey;

    private String payload;

    /** 0 pending, 1 sending, 2 sent, 3 publish failed permanently. */
    private Integer status;

    private Integer retryCount;

    private Date nextRetryTime;

    private String lastError;

    private Date createTime;

    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
