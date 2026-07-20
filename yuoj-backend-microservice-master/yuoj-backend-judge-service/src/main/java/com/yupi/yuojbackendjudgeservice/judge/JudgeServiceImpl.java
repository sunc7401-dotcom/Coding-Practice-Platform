package com.yupi.yuojbackendjudgeservice.judge;

import cn.hutool.json.JSONUtil;
import com.yupi.yuojbackendcommon.common.ErrorCode;
import com.yupi.yuojbackendcommon.exception.BusinessException;
import com.yupi.yuojbackendjudgeservice.judge.codesandbox.CodeSandbox;
import com.yupi.yuojbackendjudgeservice.judge.codesandbox.CodeSandboxFactory;
import com.yupi.yuojbackendjudgeservice.judge.codesandbox.CodeSandboxProxy;
import com.yupi.yuojbackendjudgeservice.judge.strategy.JudgeContext;
import com.yupi.yuojbackendmodel.model.codesandbox.ExecuteCodeRequest;
import com.yupi.yuojbackendmodel.model.codesandbox.ExecuteCodeResponse;
import com.yupi.yuojbackendmodel.model.codesandbox.JudgeInfo;
import com.yupi.yuojbackendmodel.model.dto.question.JudgeCase;
import com.yupi.yuojbackendmodel.model.entity.Question;
import com.yupi.yuojbackendmodel.model.entity.QuestionSubmit;
import com.yupi.yuojbackendmodel.model.enums.QuestionSubmitStatusEnum;
import com.yupi.yuojbackendmodel.model.enums.JudgeInfoMessageEnum;
import com.yupi.yuojbackendserviceclient.service.QuestionFeignClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JudgeServiceImpl implements JudgeService {

    @Resource
    private QuestionFeignClient questionFeignClient;

    @Resource
    private JudgeManager judgeManager;

    @Value("${codesandbox.type:example}")
    private String type;


    @Override
    public QuestionSubmit doJudge(long questionSubmitId) {
        // 1）传入题目的提交 id，获取到对应的题目、提交信息（包含代码、编程语言等）
        QuestionSubmit questionSubmit = questionFeignClient.getQuestionSubmitById(questionSubmitId);
        if (questionSubmit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提交信息不存在");
        }
        Integer status = questionSubmit.getStatus();
        if (QuestionSubmitStatusEnum.SUCCEED.getValue().equals(status)
                || QuestionSubmitStatusEnum.FAILED.getValue().equals(status)) {
            return questionSubmit;
        }
        // A conditional UPDATE (WAITING -> RUNNING) is the idempotency gate.
        boolean claimed = questionFeignClient.claimQuestionSubmit(questionSubmitId);
        if (!claimed) {
            QuestionSubmit latest = questionFeignClient.getQuestionSubmitById(questionSubmitId);
            if (latest != null && (QuestionSubmitStatusEnum.SUCCEED.getValue().equals(latest.getStatus())
                    || QuestionSubmitStatusEnum.FAILED.getValue().equals(latest.getStatus()))) {
                return latest;
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "判题任务已被其他消费者处理");
        }

        try {
            Long questionId = questionSubmit.getQuestionId();
            Question question = questionFeignClient.getQuestionById(questionId);
            if (question == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目不存在");
            }
            CodeSandbox codeSandbox = CodeSandboxFactory.newInstance(type);
            codeSandbox = new CodeSandboxProxy(codeSandbox);
            String language = questionSubmit.getLanguage();
            String code = questionSubmit.getCode();
            String judgeCaseStr = question.getJudgeCase();
            List<JudgeCase> judgeCaseList = JSONUtil.toList(judgeCaseStr, JudgeCase.class);
            List<String> inputList = judgeCaseList.stream().map(JudgeCase::getInput).collect(Collectors.toList());
            ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                    .code(code)
                    .language(language)
                    .inputList(inputList)
                    .build();
            ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
            List<String> outputList = executeCodeResponse.getOutputList();
            JudgeContext judgeContext = new JudgeContext();
            judgeContext.setJudgeInfo(executeCodeResponse.getJudgeInfo());
            judgeContext.setInputList(inputList);
            judgeContext.setOutputList(outputList);
            judgeContext.setJudgeCaseList(judgeCaseList);
            judgeContext.setQuestion(question);
            judgeContext.setQuestionSubmit(questionSubmit);
            JudgeInfo judgeInfo = judgeManager.doJudge(judgeContext);

            QuestionSubmit questionSubmitUpdate = new QuestionSubmit();
            questionSubmitUpdate.setId(questionSubmitId);
            questionSubmitUpdate.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
            boolean updated = questionFeignClient.completeQuestionSubmit(questionSubmitUpdate);
            if (!updated) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "判题结果状态更新失败");
            }
            return questionFeignClient.getQuestionSubmitById(questionSubmitId);
        } catch (RuntimeException e) {
            try {
                questionFeignClient.releaseQuestionSubmit(questionSubmitId);
            } catch (Exception releaseException) {
                e.addSuppressed(releaseException);
            }
            throw e;
        }
    }

    @Override
    public boolean markJudgeFailed(long questionSubmitId, String reason) {
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage(JudgeInfoMessageEnum.SYSTEM_ERROR.getValue()
                + (reason == null ? "" : ": " + reason));
        QuestionSubmit update = new QuestionSubmit();
        update.setId(questionSubmitId);
        update.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        return questionFeignClient.failQuestionSubmit(update);
    }
}
