package com.ray.controller;

import com.ray.dto.CommentCreateDTO;
import com.ray.result.CursorPageResult;
import com.ray.result.Result;
import com.ray.service.PostCommentService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.CommentThreadVO;
import com.ray.vo.CommentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供动态根评论、追加回复和评论点赞接口。 */
@Validated
@RestController
@RequestMapping("/v1")
@Tag(name = "动态评论")
public class PostCommentController {
    private final PostCommentService commentService;

    public PostCommentController(PostCommentService commentService) {
        this.commentService = commentService;
    }

    /** 查询动态评论串。 */
    @GetMapping("/posts/{postId}/comments")
    @SecurityRequirements
    @Operation(summary = "查询动态评论", operationId = "listPostComments")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<CursorPageResult<CommentThreadVO>> listComments(
            @Parameter(description = "动态 ID", required = true) @PathVariable @NotBlank String postId,
            @Parameter(description = "根评论排序", schema = @Schema(allowableValues = {"HOT", "LATEST"}))
                    @RequestParam(defaultValue = "HOT") String sort,
            @Parameter(description = "服务端排序游标") @RequestParam(required = false) Long cursor,
            @Parameter(description = "同排序值偏移量") @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int offset,
            @Parameter(description = "每页条数，1 到 20") @RequestParam(defaultValue = "10") @Min(1) @Max(20) int size) {
        return Result.ok(commentService.listThreads(IdUtils.parse(postId, "postId"), sort, cursor, offset, size));
    }

    /** 创建动态根评论。 */
    @PostMapping("/posts/{postId}/comments")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "发布动态评论", operationId = "createPostComment")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "创建成功", useReturnTypeSchema = true))
    public ResponseEntity<Result<CommentVO>> createComment(
            @Parameter(description = "动态 ID", required = true) @PathVariable String postId,
            @Valid @RequestBody CommentCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(commentService.createRoot(IdUtils.parse(postId, "postId"), dto.content())));
    }

    /** 查询指定评论讨论下的追加回复。 */
    @GetMapping("/comments/{commentId}/replies")
    @SecurityRequirements
    @Operation(summary = "查询评论回复", operationId = "listCommentReplies")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true))
    public Result<CursorPageResult<CommentVO>> listReplies(
            @Parameter(description = "评论 ID", required = true) @PathVariable String commentId,
            @Parameter(description = "创建时间游标") @RequestParam(required = false) Long cursor,
            @Parameter(description = "同创建时间偏移量") @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int offset,
            @Parameter(description = "每页条数，1 到 50") @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(commentService.listReplies(IdUtils.parse(commentId, "commentId"), cursor, offset, size));
    }

    /** 创建对指定评论的追加回复。 */
    @PostMapping("/comments/{commentId}/replies")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "回复评论", operationId = "createCommentReply")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "创建成功", useReturnTypeSchema = true))
    public ResponseEntity<Result<CommentVO>> createReply(
            @Parameter(description = "直接回复目标评论 ID", required = true) @PathVariable String commentId,
            @Valid @RequestBody CommentCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(commentService.createReply(IdUtils.parse(commentId, "commentId"), dto.content())));
    }

    /** 删除当前用户创建的评论。 */
    @DeleteMapping("/comments/{commentId}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "删除评论", operationId = "deleteComment")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "删除成功"))
    public ResponseEntity<Void> delete(
            @Parameter(description = "评论 ID", required = true) @PathVariable String commentId) {
        commentService.delete(IdUtils.parse(commentId, "commentId"));
        return ResponseEntity.noContent().build();
    }

    /** 幂等点赞评论。 */
    @PutMapping("/comments/{commentId}/like")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "点赞评论", operationId = "likeComment")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "点赞成功"))
    public ResponseEntity<Void> like(
            @Parameter(description = "评论 ID", required = true) @PathVariable String commentId) {
        commentService.like(IdUtils.parse(commentId, "commentId"));
        return ResponseEntity.noContent().build();
    }

    /** 幂等取消评论点赞。 */
    @DeleteMapping("/comments/{commentId}/like")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "取消评论点赞", operationId = "unlikeComment")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "取消成功"))
    public ResponseEntity<Void> unlike(
            @Parameter(description = "评论 ID", required = true) @PathVariable String commentId) {
        commentService.unlike(IdUtils.parse(commentId, "commentId"));
        return ResponseEntity.noContent().build();
    }
}
