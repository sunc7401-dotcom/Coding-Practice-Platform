package com.yupi.yuojbackendquestionservice.controller.inner;

import com.yupi.yuojbackendmodel.model.entity.Question;
import com.yupi.yuojbackendmodel.model.entity.QuestionSubmit;
import com.yupi.yuojbackendquestionservice.service.QuestionService;
import com.yupi.yuojbackendquestionservice.service.QuestionSubmitService;
import com.yupi.yuojbackendserviceclient.service.QuestionFeignClient;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 该服务仅内部调用，不是给前端的
 */
@RestController
@RequestMapping("/inner")
public class QuestionInnerController implements QuestionFeignClient {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionSubmitService questionSubmitService;

    @GetMapping("/get/id")
    @Override
    public Question getQuestionById(@RequestParam("questionId") long questionId) {
        return questionService.getById(questionId);
    }

    @GetMapping("/question_submit/get/id")
    @Override
    public QuestionSubmit getQuestionSubmitById(@RequestParam("questionId") long questionSubmitId) {
        return questionSubmitService.getById(questionSubmitId);
    }

    @PostMapping("/question_submit/update")
    @Override
    public boolean updateQuestionSubmitById(@RequestBody QuestionSubmit questionSubmit) {
        return questionSubmitService.updateById(questionSubmit);
    }

    @PostMapping("/question_submit/claim")
    @Override
    public boolean claimQuestionSubmit(@RequestParam("questionSubmitId") long questionSubmitId) {
        return questionSubmitService.claimQuestionSubmit(questionSubmitId);
    }

    @PostMapping("/question_submit/release")
    @Override
    public boolean releaseQuestionSubmit(@RequestParam("questionSubmitId") long questionSubmitId) {
        return questionSubmitService.releaseQuestionSubmit(questionSubmitId);
    }

    @PostMapping("/question_submit/complete")
    @Override
    public boolean completeQuestionSubmit(@RequestBody QuestionSubmit questionSubmit) {
        return questionSubmitService.completeQuestionSubmit(questionSubmit.getId(), questionSubmit.getJudgeInfo());
    }

    @PostMapping("/question_submit/fail")
    @Override
    public boolean failQuestionSubmit(@RequestBody QuestionSubmit questionSubmit) {
        return questionSubmitService.failQuestionSubmit(questionSubmit.getId(), questionSubmit.getJudgeInfo());
    }

}
