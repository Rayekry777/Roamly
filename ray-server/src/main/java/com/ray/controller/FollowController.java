package com.ray.controller;

import com.ray.result.Result;
import com.ray.service.FollowService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.FollowStatusVO;
import com.ray.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
@Tag(name = "关注")
@SecurityRequirement(name = "BearerAuth")
public class FollowController {
    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PutMapping("/me/following/{userId}")
    @Operation(summary = "关注用户", operationId = "followUser")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "关注成功", useReturnTypeSchema = true))
    public Result<Void> follow(@Parameter(description = "目标用户 ID") @PathVariable String userId) {
        followService.follow(IdUtils.parse(userId, "userId"));
        return Result.ok(null);
    }

    @DeleteMapping("/me/following/{userId}")
    @Operation(summary = "取消关注用户", operationId = "unfollowUser")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "取消成功"))
    public ResponseEntity<Void> unfollow(@Parameter(description = "目标用户 ID") @PathVariable String userId) {
        followService.unfollow(IdUtils.parse(userId, "userId"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/following/{userId}")
    @Operation(summary = "查询关注状态", operationId = "getFollowStatus")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<FollowStatusVO> status(@Parameter(description = "目标用户 ID") @PathVariable String userId) {
        return Result.ok(new FollowStatusVO(followService.isFollowing(IdUtils.parse(userId, "userId"))));
    }

    @GetMapping("/{userId}/common-following")
    @Operation(summary = "查询共同关注", operationId = "listCommonFollowing")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<List<UserVO>> common(@Parameter(description = "对比用户 ID") @PathVariable String userId) {
        return Result.ok(followService.listCommonFollowing(IdUtils.parse(userId, "userId")));
    }
}
