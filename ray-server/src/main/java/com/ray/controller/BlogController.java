package com.ray.controller;

import com.ray.dto.CreateBlogDTO;
import com.ray.result.CursorPageResult;
import com.ray.result.PageResult;
import com.ray.result.Result;
import com.ray.service.BlogService;
import com.ray.service.CurrentUserProvider;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.BlogVO;
import com.ray.vo.IdVO;
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
import java.util.List;
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

@Validated
@RestController
@Tag(name = "探店笔记")
public class BlogController {
    private final BlogService service;
    private final CurrentUserProvider currentUser;

    public BlogController(BlogService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/v1/blogs")
    @SecurityRequirements
    @Operation(summary = "查询热门笔记", operationId = "listBlogs")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<PageResult<BlogVO>> list(
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页条数，1 到 100") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Result.ok(service.listBlogs(page, size));
    }

    @PostMapping("/v1/blogs")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "发布探店笔记", operationId = "createBlog")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "创建成功", useReturnTypeSchema = true))
    public ResponseEntity<Result<IdVO>> create(@Valid @RequestBody CreateBlogDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(new IdVO(IdUtils.format(service.createBlog(request)))));
    }

    @GetMapping("/v1/blogs/{blogId}")
    @SecurityRequirements
    @Operation(summary = "查询笔记详情", operationId = "getBlog")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<BlogVO> get(@Parameter(description = "笔记 ID") @PathVariable String blogId) {
        return Result.ok(service.getBlog(IdUtils.parse(blogId, "blogId")));
    }

    @GetMapping("/v1/blogs/{blogId}/likes")
    @SecurityRequirements
    @Operation(summary = "查询点赞用户", operationId = "listBlogLikes")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<List<UserVO>> likes(@Parameter(description = "笔记 ID") @PathVariable String blogId) {
        return Result.ok(service.listLikes(IdUtils.parse(blogId, "blogId")));
    }

    @PutMapping("/v1/blogs/{blogId}/like")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "点赞笔记", operationId = "likeBlog")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "点赞成功", useReturnTypeSchema = true))
    public Result<Void> like(@Parameter(description = "笔记 ID") @PathVariable String blogId) {
        service.like(IdUtils.parse(blogId, "blogId"));
        return Result.ok(null);
    }

    @DeleteMapping("/v1/blogs/{blogId}/like")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "取消点赞", operationId = "unlikeBlog")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "取消成功"))
    public ResponseEntity<Void> unlike(@Parameter(description = "笔记 ID") @PathVariable String blogId) {
        service.unlike(IdUtils.parse(blogId, "blogId"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/v1/users/me/blogs")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "查询我的笔记", operationId = "listMyBlogs")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<PageResult<BlogVO>> mine(
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页条数，1 到 100") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Result.ok(service.listUserBlogs(currentUser.requireUserId(), page, size));
    }

    @GetMapping("/v1/users/{userId}/blogs")
    @SecurityRequirements
    @Operation(summary = "查询用户笔记", operationId = "listUserBlogs")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<PageResult<BlogVO>> byUser(
            @Parameter(description = "用户 ID") @PathVariable String userId,
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页条数，1 到 100") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Result.ok(service.listUserBlogs(IdUtils.parse(userId, "userId"), page, size));
    }

    @GetMapping("/v1/feeds/following")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "查询关注流", operationId = "listFollowingFeed")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<CursorPageResult<BlogVO>> feed(
            @Parameter(description = "上一页返回的时间游标") @RequestParam(defaultValue = "9223372036854775807") long cursor,
            @Parameter(description = "同时间戳偏移量") @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return Result.ok(service.listFollowingFeed(cursor, offset));
    }
}
