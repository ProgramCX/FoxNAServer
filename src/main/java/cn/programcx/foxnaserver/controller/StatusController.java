package cn.programcx.foxnaserver.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/status")
public class StatusController {
    @GetMapping("/status")
    private ResponseEntity<?> getStatus() {
        return ResponseEntity.ok("online");
    }
}
