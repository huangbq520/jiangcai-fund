package com.jiangcai.fund.controller;

import com.jiangcai.fund.dto.AuthResponse;
import com.jiangcai.fund.dto.LoginRequest;
import com.jiangcai.fund.dto.RegisterRequest;
import com.jiangcai.fund.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /** 登录页 */
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    /** 注册页（复用登录页） */
    @GetMapping("/register")
    public String registerPage() {
        return "login";
    }

    /** 注册 API */
    @PostMapping("/api/auth/register")
    @ResponseBody
    public Map<String, Object> register(@RequestBody RegisterRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            AuthResponse resp = userService.register(request);
            result.put("success", true);
            result.put("token", resp.getToken());
            result.put("username", resp.getUsername());
            result.put("userId", resp.getUserId());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /** 登录 API */
    @PostMapping("/api/auth/login")
    @ResponseBody
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            AuthResponse resp = userService.login(request);
            result.put("success", true);
            result.put("token", resp.getToken());
            result.put("username", resp.getUsername());
            result.put("userId", resp.getUserId());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "用户名或密码错误");
        }
        return result;
    }
}
