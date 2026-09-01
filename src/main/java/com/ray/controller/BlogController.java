package com.ray.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.dto.Result;
import com.ray.dto.UserDTO;
import com.ray.entity.Blog;
import com.ray.service.IBlogService;
import com.ray.utils.SystemConstants;
import com.ray.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/blog")
@Tag(name = "探店笔记")
public class BlogController {

    @Resource
    private IBlogService blogService;

    @PostMapping
    @Operation(summary = "发布探店笔记", operationId = "createBlog")
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    @Operation(summary = "点赞或取消点赞", operationId = "likeBlog")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    @Operation(summary = "查询我的笔记", operationId = "listMyBlogs")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    @Operation(summary = "查询热门笔记", operationId = "listHotBlogs")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询笔记详情", operationId = "getBlogById")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }

    @GetMapping("/likes/{id}")
    @Operation(summary = "查询笔记点赞用户", operationId = "listBlogLikes")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        return blogService.queryBlogLikes(id);
    }

    @GetMapping("/of/user")
    @Operation(summary = "查询用户笔记", operationId = "listUserBlogs")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/of/follow")
    @Operation(summary = "查询关注流笔记", operationId = "listFollowBlogs")
    public Result queryBlogOfFollow(
            @RequestParam("lastId") Long max, @RequestParam(value = "offset", defaultValue = "0") Integer offset){
        return blogService.queryBlogOfFollow(max, offset);
    }
}
