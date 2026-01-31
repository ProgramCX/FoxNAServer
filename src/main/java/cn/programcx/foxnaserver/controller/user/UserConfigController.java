package cn.programcx.foxnaserver.controller.user;

import cn.programcx.foxnaserver.common.Result;
import cn.programcx.foxnaserver.service.user.UserConfigService;
import cn.programcx.foxnaserver.util.JwtUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/user-self")
@Tag(name = "UserSelfConfig", description = "用户自我设置相关接口")
public class UserConfigController {

    @Autowired
    private UserConfigService userConfigService;

    @PutMapping("/changePassword")
    public ResponseEntity<Result<?>> changePassword(String oldPassword, String newPassword) {
        String userName = JwtUtil.getCurrentUuid();
        try {
            userConfigService.changePassword(userName, newPassword, oldPassword);
            return ResponseEntity.ok(Result.success());
        } catch (Exception e) {
            log.error("Change password failed for user: {}", userName, e);
            return ResponseEntity.status(500).body(Result.internalServerError(e.getMessage()));
        }
    }

    @PutMapping("/changeUserName")
    public ResponseEntity<Result<?>> changeUserName(String newUserName) throws Exception {
        String currentUserName = JwtUtil.getCurrentUuid();
        if (currentUserName != null && currentUserName.equalsIgnoreCase("admin")) {
            return ResponseEntity.status(500).body(Result.internalServerError("Cannot change admin username"));
        }
        try {
            userConfigService.changeUserName(currentUserName, newUserName);
            return ResponseEntity.ok(Result.success());
        } catch (Exception e) {
            log.error("Update username failed for user: {}", currentUserName, e);
            return ResponseEntity.status(500).body(Result.internalServerError(e.getMessage()));
        }
    }
}
