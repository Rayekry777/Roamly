package com.ray.controller;


import com.ray.dto.Result;
import com.ray.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/follow")
@Tag(name = "关注关系")
public class FollowController {

    @Resource
    private IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    @Operation(summary = "关注或取消关注用户", operationId = "followUser")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    @GetMapping("/or/not/{id}")
    @Operation(summary = "查询关注状态", operationId = "checkFollow")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    @GetMapping("/common/{id}")
    @Operation(summary = "查询共同关注", operationId = "listCommonFollows")
    public Result followCommons(@PathVariable("id") Long id){
        return followService.followCommons(id);
    }
}
