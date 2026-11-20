package interview.guide.common.async.controller;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.async.dto.DeadLetterResponse;
import interview.guide.common.async.model.DeadLetterStatus;
import interview.guide.common.async.service.AsyncDeadLetterService;
import interview.guide.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/async/dead-letter")
@RequiredArgsConstructor
@Slf4j
public class AsyncDeadLetterController {

    private final AsyncDeadLetterService deadLetterService;

    /** 列表：status 为空返回全部，否则按状态过滤（返回 VO，不含 payload 大字段） */
    @GetMapping
    @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 30)
    public Result<List<DeadLetterResponse>> list(
            @RequestParam(required = false) DeadLetterStatus status) {
        return Result.success(deadLetterService.listResponses(status));
    }

    /** 重投：把死信原样重新投递回对应 Stream */
    @PostMapping("/{id}/replay")
    @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 10)
    public Result<Void> replay(@PathVariable Long id) {
        deadLetterService.replay(id);
        return Result.success();
    }

    /** 丢弃：标记 RESOLVED，不再重投 */
    @PostMapping("/{id}/discard")
    @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 10)
    public Result<Void> discard(@PathVariable Long id) {
        deadLetterService.discard(id);
        return Result.success();
    }
}
