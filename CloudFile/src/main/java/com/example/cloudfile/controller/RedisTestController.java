
package com.example.cloudfile.controller;

import com.example.cloudfile.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/redis")
@RequiredArgsConstructor
public class RedisTestController {

    private final RedisService redisService;

    @PostMapping("/set")
    public String setValue() {
        redisService.setValue(
                "cloudfile:test",
                "Redis Connected",
                Duration.ofMinutes(5)
        );

        return "Value stored in Redis";
    }

    @GetMapping("/get")
    public String getValue() {
        return redisService.getValue("cloudfile:test");
    }
}
