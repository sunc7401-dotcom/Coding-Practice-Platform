package com.yupi.yuojbackendjudgeservice.judge;

import com.yupi.yuojbackendmodel.model.entity.QuestionSubmit;

/**
 * 判题服务
 */
public interface JudgeService {

    /**
     * 判题
     * @param questionSubmitId
     * @return
     */
    QuestionSubmit doJudge(long questionSubmitId);

    /**
     * Persists a terminal system error after all delayed retries are exhausted.
     */
    boolean markJudgeFailed(long questionSubmitId, String reason);
}
