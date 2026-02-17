package com.example.demo.controller;

import com.example.demo.service.InstahyreDebugService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugController {

    private final InstahyreDebugService debugService;

    @GetMapping("/inspect-login")
    public ResponseEntity<?> inspectLoginPage() {
        try {
            debugService.inspectLoginPage();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Check console logs for page inspection details"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}