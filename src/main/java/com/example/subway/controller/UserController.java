package com.example.subway.controller;

import com.example.subway.domain.User;
import com.example.subway.dto.UserRegistrationRequest;
import com.example.subway.dto.UserLoginRequest;
import com.example.subway.dto.UserResponse;
import com.example.subway.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // 회원가입 엔드포인트
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody UserRegistrationRequest request) {
        User user = userService.registerUser(request);
        return ResponseEntity.ok(new UserResponse(user.getId(), user.getUsername(), user.getWorkStation(), user.getAlertThreshold()));
    }

    // 로그인 엔드포인트
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@RequestBody UserLoginRequest request) {
        return userService.authenticate(request)
                .map(user -> ResponseEntity.ok(new UserResponse(user.getId(), user.getUsername(), user.getWorkStation(), user.getAlertThreshold())))
                .orElse(ResponseEntity.status(401).build());
    }

    // 프로필 업데이트 (근무역, 알림 정거장 수 변경)
    @PutMapping("/{userId}/profile")
    public ResponseEntity<UserResponse> updateProfile(@PathVariable Long userId,
                                                      @RequestBody UserRegistrationRequest request) {
        User updatedUser = userService.updateUserProfile(userId, request.getWorkStation(), request.getAlertThreshold());
        return ResponseEntity.ok(new UserResponse(updatedUser.getId(), updatedUser.getUsername(), updatedUser.getWorkStation(), updatedUser.getAlertThreshold()));
    }
}
