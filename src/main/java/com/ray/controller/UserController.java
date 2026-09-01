package com.ray.controller;


import cn.hutool.core.bean.BeanUtil;
import com.ray.dto.LoginFormDTO;
import com.ray.dto.Result;
import com.ray.dto.UserDTO;
import com.ray.entity.User;
import com.ray.entity.UserInfo;
import com.ray.service.IUserInfoService;
import com.ray.service.IUserService;
import com.ray.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


@Slf4j
@RestController
@RequestMapping("/user")
@Tag(name = "用户管理")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    @Operation(summary = "发送短信验证码", operationId = "sendUserCode")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", operationId = "loginUser")
    public Result login(@Valid @RequestBody LoginFormDTO loginForm, HttpSession session){
        // 实现登录功能
        return userService.login(loginForm, session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", operationId = "logoutUser")
    public Result logout(HttpServletRequest request){
        String token = request.getHeader("authorization");
        userService.logout(token);
        return Result.ok();
    }

    @GetMapping("/me")
    @Operation(summary = "查询当前用户", operationId = "getCurrentUser")
    public Result me(){
        // 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    @Operation(summary = "查询用户资料", operationId = "getUserInfo")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询用户基础信息", operationId = "getUserById")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }

    @PostMapping("/sign")
    @Operation(summary = "用户签到", operationId = "signIn")
    public Result sign(){
        return userService.sign();
    }

    @GetMapping("/sign/count")
    @Operation(summary = "查询签到次数", operationId = "getSignCount")
    public Result signCount(){
        return userService.signCount();
    }
}
