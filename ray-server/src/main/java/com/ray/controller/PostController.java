package com.ray.controller;

import com.ray.dto.PostCreateDTO;
import com.ray.dto.PostUpdateDTO;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.PostService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.IdVO;
import com.ray.vo.PostCardVO;
import com.ray.vo.PostDetailVO;
import com.ray.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供统一动态、作者维护和点赞接口。 */
@Validated
@RestController
@Tag(name = "统一动态")
public class PostController {
    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping("/v1/posts")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "发布动态", operationId = "createPost")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "201",
                    description = "创建成功",
                    useReturnTypeSchema = true))
    public ResponseEntity<Result<IdVO>> createPost(@Valid @RequestBody PostCreateDTO request) {
        Long postId = postService.createPost(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(new IdVO(IdUtils.format(postId))));
    }

    @GetMapping("/v1/posts/{postId}")
    @SecurityRequirements
    @Operation(summary = "查询动态详情", operationId = "getPost")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    useReturnTypeSchema = true))
    public Result<PostDetailVO> getPost(
            @Parameter(description = "动态 ID", required = true) @PathVariable String postId) {
        return Result.ok(postService.getPost(IdUtils.parse(postId, "postId")));
    }

    @PutMapping("/v1/posts/{postId}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "更新动态", operationId = "updatePost")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "200",
                    description = "更新成功",
                    useReturnTypeSchema = true))
    public Result<PostDetailVO> updatePost(
            @Parameter(description = "动态 ID", required = true) @PathVariable String postId,
            @Valid @RequestBody PostUpdateDTO request) {
        return Result.ok(postService.updatePost(IdUtils.parse(postId, "postId"), request));
    }

    @DeleteMapping("/v1/posts/{postId}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "删除动态", operationId = "deletePost")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "删除成功"))
    public ResponseEntity<Void> deletePost(
            @Parameter(description = "动态 ID", required = true) @PathVariable String postId) {
        postService.deletePost(IdUtils.parse(postId, "postId"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/v1/users/me/posts")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "查询我的动态", operationId = "listMyPosts")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    useReturnTypeSchema = true))
    public Result<PageResult<PostCardVO>> listMyPosts(
            @Parameter(description = "页码，从 1 开始")
                    @RequestParam(defaultValue = "1")
                    @Min(1)
                    int page,
            @Parameter(description = "每页条数，1 到 100")
                    @RequestParam(defaultValue = "10")
                    @Min(1)
                    @Max(100)
                    int size) {
        return Result.ok(postService.listMyPosts(page, size));
    }

    @GetMapping("/v1/users/{userId}/posts")
    @SecurityRequirements
    @Operation(summary = "查询用户动态", operationId = "listUserPosts")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    useReturnTypeSchema = true))
    public Result<PageResult<PostCardVO>> listUserPosts(
            @Parameter(description = "用户 ID", required = true) @PathVariable String userId,
            @Parameter(description = "页码，从 1 开始")
                    @RequestParam(defaultValue = "1")
                    @Min(1)
                    int page,
            @Parameter(description = "每页条数，1 到 100")
                    @RequestParam(defaultValue = "10")
                    @Min(1)
                    @Max(100)
                    int size) {
        return Result.ok(postService.listUserPosts(IdUtils.parse(userId, "userId"), page, size));
    }

    @PutMapping("/v1/posts/{postId}/like")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "点赞动态", operationId = "likePost")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "点赞成功"))
    public ResponseEntity<Void> likePost(
            @Parameter(description = "动态 ID", required = true) @PathVariable String postId) {
        postService.likePost(IdUtils.parse(postId, "postId"));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/v1/posts/{postId}/like")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "取消动态点赞", operationId = "unlikePost")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "取消成功"))
    public ResponseEntity<Void> unlikePost(
            @Parameter(description = "动态 ID", required = true) @PathVariable String postId) {
        postService.unlikePost(IdUtils.parse(postId, "postId"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/v1/posts/{postId}/likes")
    @SecurityRequirements
    @Operation(summary = "查询动态点赞用户", operationId = "listPostLikes")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    useReturnTypeSchema = true))
    public Result<PageResult<UserVO>> listPostLikes(
            @Parameter(description = "动态 ID", required = true) @PathVariable String postId,
            @Parameter(description = "页码，从 1 开始")
                    @RequestParam(defaultValue = "1")
                    @Min(1)
                    int page,
            @Parameter(description = "每页条数，1 到 100")
                    @RequestParam(defaultValue = "10")
                    @Min(1)
                    @Max(100)
                    int size) {
        return Result.ok(postService.listLikes(IdUtils.parse(postId, "postId"), page, size));
    }
}
