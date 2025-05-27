package cn.programcx.foxnaserver.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/status")
public class StatusController {
    @GetMapping("/status")
    private ResponseEntity<?> getStatus() {
        log.info("[{}]获取服务器状态成功", System.currentTimeMillis());
        return ResponseEntity.ok("online");
    }
}
