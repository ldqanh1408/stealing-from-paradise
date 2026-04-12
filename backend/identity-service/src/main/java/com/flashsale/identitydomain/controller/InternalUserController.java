package com.flashsale.identitydomain.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    // private final UserRepository userRepository;

    @GetMapping("/{userId}/role")
    public ResponseEntity<UserRoleResponse> getUserRole(@PathVariable String userId) {
        // TODO: Implement - query từ database
        return ResponseEntity.ok(new UserRoleResponse(userId, "BUYER", true));
    }
}

// record DTO
record UserRoleResponse(String userId, String role, boolean active) {}

