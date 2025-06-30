package cn.programcx.foxnaserver.controller.user;

import cn.programcx.foxnaserver.entity.Permission;
import cn.programcx.foxnaserver.entity.Resource;
import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.service.log.ErrorLogService;
import cn.programcx.foxnaserver.service.user.UserManagementService;
import cn.programcx.foxnaserver.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/user")
@Tag(name = "UserManagement", description = "用户管理相关接口")
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

    @Operation(
            summary = "添加用户",
            description = "添加新用户，并分配权限和资源"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "用户添加成功"),
            @ApiResponse(responseCode = "500", description = "请求参数错误或用户已存在")
    })
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

    @Operation(summary = "删除用户",
            description = "删除指定用户名的用户")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "用户删除成功"),
            @ApiResponse(responseCode = "400", description = "请求参数错误或用户不存在")
    })
    @PostMapping("delUser")
    public ResponseEntity<?> delUser(@RequestParam("userName") String userName,HttpServletRequest request) {
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

    @Operation(summary = "封禁用户",
            description = "封禁指定用户名的用户")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "用户封禁成功"),
            @ApiResponse(responseCode = "400", description = "请求参数错误或用户不存在")
    })
    @PostMapping("blockUser")
    public ResponseEntity<?> blockUser(@RequestParam("userName") String userName,HttpServletRequest request) {
        try {
            userManagementService.blockUser(userName);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            errorLogService.insertErrorLog(request, e, "封禁用户失败: " + userName);
            log.error("[{}]封禁用户失败: {}", JwtUtil.getCurrentUsername(), userName, e);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "修改用户密码",
            description = "修改指定用户名的用户密码")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "用户密码修改成功"),
            @ApiResponse(responseCode = "400", description = "请求参数错误或用户不存在")
    })
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
