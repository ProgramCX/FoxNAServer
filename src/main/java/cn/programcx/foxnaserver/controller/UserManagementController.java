package cn.programcx.foxnaserver.controller;

import cn.programcx.foxnaserver.entity.Permission;
import cn.programcx.foxnaserver.entity.Resource;
import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.service.ErrorLogService;
import cn.programcx.foxnaserver.service.UserManagementService;
import cn.programcx.foxnaserver.util.JwtUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/user")
public class UserManagementController {

    @Autowired
    private UserManagementService userManagementService;
    @Autowired
    private ErrorLogService errorLogService;

    @Data
   private static class UserPermissionResourceDTO {
        String userName;
        String password;
        List<Permission> permissions;
        List<Resource> resources;
    }

    @PostMapping("/addUser")
    public ResponseEntity<?> addUser(@RequestBody UserPermissionResourceDTO dto, HttpServletRequest request) {
        try{
            User user = new User();
            System.out.println(dto.password);
            user.setUserName(dto.userName);
            user.setPassword(dto.password);
            user.setState("enabled");
            userManagementService.addUser(user,dto.permissions,dto.resources);
        } catch (Exception e) {
            errorLogService.insertErrorLog(request, e, "添加用户失败: " + dto.userName);
            log.error("[{}]添加用户失败: {}", JwtUtil.getCurrentUsername(),dto.userName, e);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        log.info("[{}]添加用户成功: {}", JwtUtil.getCurrentUsername(), dto.userName);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PostMapping("delUser")
    public ResponseEntity<?> delUser(@RequestParam String userName,HttpServletRequest request) {
       try {
           userManagementService.delUser(userName);

       }
       catch (Exception e) {
           errorLogService.insertErrorLog(request, e, "删除用户失败: " + userName);
           log.error("[{}]删除用户失败: {}", JwtUtil.getCurrentUsername(), userName, e);
           return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
       }
         log.info("[{}]删除用户成功: {}", JwtUtil.getCurrentUsername(), userName);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("blockUser")
    public ResponseEntity<?> blockUser(@RequestParam String userName,HttpServletRequest request) {
        try {
            userManagementService.blockUser(userName);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            errorLogService.insertErrorLog(request, e, "封禁用户失败: " + userName);
            log.error("[{}]封禁用户失败: {}", JwtUtil.getCurrentUsername(), userName, e);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("changePassword")
    public ResponseEntity<?> changePassword(@RequestBody Map<String,String> map,HttpServletRequest request) {
        String password = map.get("password");
        String userName = map.get("userName");

        if (password == null || userName == null) {
            log.error("[{}]修改用户密码失败: 用户名或密码不能为空", JwtUtil.getCurrentUsername());
            errorLogService.insertErrorLog(request, new Exception("密码或用户名不能为空"), "修改用户密码失败: " + userName);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        try{
            userManagementService.changePassword(userName,password);

        } catch (Exception e) {
            log.error("[{}]修改用户密码失败: {}", JwtUtil.getCurrentUsername(), userName, e);
            errorLogService.insertErrorLog(request, e, "修改用户密码失败: " + userName);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        log.info("[{}]修改用户密码成功: {}", JwtUtil.getCurrentUsername(), userName);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
