package com.ray.service.impl;

import com.ray.entity.BlogComments;
import com.ray.mapper.BlogCommentsMapper;
import com.ray.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
