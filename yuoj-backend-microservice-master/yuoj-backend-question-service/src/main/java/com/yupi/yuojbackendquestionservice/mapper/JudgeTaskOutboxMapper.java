package com.yupi.yuojbackendquestionservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yupi.yuojbackendquestionservice.model.entity.JudgeTaskOutbox;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

public interface JudgeTaskOutboxMapper extends BaseMapper<JudgeTaskOutbox> {

    @Select("SELECT * FROM judge_task_outbox " +
            "WHERE (status = 0 AND nextRetryTime <= NOW()) " +
            "OR (status = 1 AND updateTime < #{staleBefore}) " +
            "ORDER BY id LIMIT #{limit}")
    List<JudgeTaskOutbox> selectPublishable(@Param("staleBefore") Date staleBefore,
                                            @Param("limit") int limit);

    @Update("UPDATE judge_task_outbox SET status = 1, updateTime = NOW() " +
            "WHERE id = #{id} AND ((status = 0 AND nextRetryTime <= NOW()) " +
            "OR (status = 1 AND updateTime < #{staleBefore}))")
    int claimForPublish(@Param("id") long id, @Param("staleBefore") Date staleBefore);

    @Update("UPDATE judge_task_outbox SET status = 2, lastError = NULL, updateTime = NOW() " +
            "WHERE id = #{id} AND status = 1")
    int markSent(@Param("id") long id);

    @Update("UPDATE judge_task_outbox SET status = #{status}, retryCount = retryCount + 1, " +
            "nextRetryTime = #{nextRetryTime}, lastError = #{lastError}, updateTime = NOW() " +
            "WHERE id = #{id} AND status = 1")
    int markPublishFailed(@Param("id") long id,
                          @Param("status") int status,
                          @Param("nextRetryTime") Date nextRetryTime,
                          @Param("lastError") String lastError);
}
