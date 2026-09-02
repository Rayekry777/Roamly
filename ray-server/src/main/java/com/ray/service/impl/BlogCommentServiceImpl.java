package com.ray.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.entity.BlogComment;
import com.ray.mapper.BlogCommentMapper;
import com.ray.service.BlogCommentService;
import org.springframework.stereotype.Service;

/** 笔记评论持久化实现，HTTP 能力尚未开放。 */
@Service
public class BlogCommentServiceImpl extends ServiceImpl<BlogCommentMapper, BlogComment> implements BlogCommentService {}
