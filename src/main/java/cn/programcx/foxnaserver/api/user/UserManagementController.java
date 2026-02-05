package cn.programcx.foxnaserver.api.user;

import cn.programcx.foxnaserver.common.Result;
import cn.programcx.foxnaserver.entity.Permission;
import cn.programcx.foxnaserver.entity.Resource;
import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.mapper.PermissionMapper;
import cn.programcx.foxnaserver.service.log.ErrorLogService;
import cn.programcx.foxnaserver.service.user.UserManagementService;
import cn.programcx.foxnaserver.service.user.UserService;
import cn.programcx.foxnaserver.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    @Autowired
    private UserService userService;

    @Autowired
    private PermissionMapper permissionMapper;

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
        try {
            User user = new User();
            user.setUserName(dto.userName);
            user.setPassword(dto.password);
            user.setState("enabled");
            userManagementService.addUser(user, dto.permissions, dto.resources);
        } catch (Exception e) {
            errorLogService.insertErrorLog(request, e, "添加用户失败: " + dto.userName);
            log.error("[{}]添加用户失败: {}", JwtUtil.getCurrentUuid(), dto.userName, e);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        log.info("[{}]添加用户成功: {}", JwtUtil.getCurrentUuid(), dto.userName);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "删除用户",
            description = "删除指定 UUID 的用户")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "用户删除成功"),
            @ApiResponse(responseCode = "400", description = "请求参数错误或用户不存在")
    })
    @DeleteMapping("delUser")
    public ResponseEntity<?> delUser(@RequestParam("uuid") String uuid, HttpServletRequest request) {
        try {
            userManagementService.delUserByUuid(uuid);

        } catch (Exception e) {
            errorLogService.insertErrorLog(request, e, "删除用户失败: " + uuid);
            log.error("[{}]删除用户失败: {}", JwtUtil.getCurrentUuid(), uuid, e);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        log.info("[{}]删除用户成功: {}", JwtUtil.getCurrentUuid(), uuid);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "封禁用户",
            description = "封禁指定 UUID 的用户")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "用户封禁成功"),
            @ApiResponse(responseCode = "400", description = "请求参数错误或用户不存在")
    })
    @PutMapping("blockUser")
    public ResponseEntity<?> blockUser(@RequestParam("uuid") String uuid, HttpServletRequest request) {
        try {
            userManagementService.blockUserByUuid(uuid);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            errorLogService.insertErrorLog(request, e, "封禁用户失败: " + uuid);
            log.error("[{}]封禁用户失败: {}", JwtUtil.getCurrentUuid(), uuid, e);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "解封用户",
            description = "解封指定 UUID 的用户")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "用户解封成功"),
            @ApiResponse(responseCode = "400", description = "请求参数错误或用户不存在")
    }
    )
    @PutMapping("unblockUser")
    public ResponseEntity<?> unblockUser(@RequestParam("uuid") String uuid, HttpServletRequest request) {
        try {
            userManagementService.unblockUserByUuid(uuid);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            errorLogService.insertErrorLog(request, e, "解封用户失败: " + uuid);
            log.error("[{}]解封用户失败: {}", JwtUtil.getCurrentUuid(), uuid, e);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "修改用户密码",
            description = "修改指定 UUID 的用户密码")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "用户密码修改成功"),
            @ApiResponse(responseCode = "400", description = "请求参数错误或用户不存在")
    })
    @PutMapping("changePassword")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> map, HttpServletRequest request) {
        String password = map.get("password");
        String uuid = map.get("uuid");

        if (password == null || uuid == null) {
            log.error("[{}]修改用户密码失败: uuid 或密码不能为空", JwtUtil.getCurrentUuid());
            errorLogService.insertErrorLog(request, new Exception("密码或 uuid 不能为空"), "修改用户密码失败: " + uuid);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        try {
            userManagementService.changePasswordByUuid(uuid, password);

        } catch (Exception e) {
            log.error("[{}]修改用户密码失败: {}", JwtUtil.getCurrentUuid(), uuid, e);
            errorLogService.insertErrorLog(request, e, "修改用户密码失败: " + uuid);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        log.info("[{}]修改用户密码成功: {}", JwtUtil.getCurrentUuid(), uuid);
        return new ResponseEntity<>(HttpStatus.OK);
    }


    @Operation(summary = "查询用户列表",
            description = "分页查询用户列表，支持按用户名关键字搜索")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功获取用户列表"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    }
    )
    @GetMapping("/list")
    public ResponseEntity<?> listUsers(@RequestParam(value = "keyword", required = false) String keyword,
                                       @RequestParam(value = "size", defaultValue = "30") int size,
                                       @RequestParam(value = "page", defaultValue = "1") int page) {
        try {
            LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.select(User::getUserName, User::getState, User::getId);
            Page<User> userPage = new Page<>(page, size);
            if (keyword != null && !keyword.isEmpty()) {
                queryWrapper.like(User::getUserName, keyword);
            }
            IPage<User> resultPage = userService.page(userPage, queryWrapper);
            return ResponseEntity.ok(resultPage);
        }
        catch (Exception e) {
            log.error("查询用户列表失败", e);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "查询用户权限",
            description = "查询指定 UUID 的用户权限列表")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功获取用户权限列表"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    }
    )

    @GetMapping("/permissions")
    public ResponseEntity<?> userPermission(@RequestParam(value = "uuid") String uuid) {
        try{
            LambdaQueryWrapper<Permission> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Permission::getOwnerUuid, uuid);
            List<Permission> permissions = permissionMapper.selectList(queryWrapper);
            return ResponseEntity.ok(permissions);
        }catch (Exception e) {
            log.error("查询用户权限失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "授予用户权限",
            description = "授予指定 UUID 的用户某个权限")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功授予用户权限"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @PutMapping("/grantPermission")
    public ResponseEntity<?> grantPermission(@RequestParam("uuid") String uuid,
                                             @RequestParam("areaName") String areaName) {
        try {
            userManagementService.grantPermissionByUuid(uuid, areaName);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            log.error("授予用户权限失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "撤销用户权限",
            description = "撤销指定 UUID 的用户某个权限")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功撤销用户权限"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @PutMapping("/revokePermission")
    public ResponseEntity<?> revokePermission(@RequestParam("uuid") String uuid,
                                              @RequestParam("areaName") String areaName) {
        try {
            userManagementService.revokePermissionByUuid(uuid, areaName);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            log.error("撤销用户权限失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "获取所有权限",
            description = "获取系统中所有可用的权限列表")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功获取权限列表"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    }
    )

    @GetMapping("/allPermissions")
    public ResponseEntity<?> allPermissions() {
      return ResponseEntity.ok(userManagementService.allPermissions());
    }

    @Operation(summary = "更新用户信息",
            description = "更新用户的基本信息，用户名称和状态")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功更新用户信息"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    }
    )
    @PutMapping("/updateUser")
    public ResponseEntity<?> updateUser(@RequestBody User user,@RequestParam String uuid, HttpServletRequest request) {
        try {
            userManagementService.updateUser(user, uuid);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            log.error("更新用户信息失败", e);
            errorLogService.insertErrorLog(request, e, "更新用户信息失败: " + user.getUserName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PutMapping("/grantResource")
    public ResponseEntity<?> grantResource(@RequestParam String uuid, @RequestParam String resourcePath,@RequestParam String type ,HttpServletRequest request) {
        try {
            userManagementService.grantResourceByUuid(uuid, resourcePath, type);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            log.error("授予用户资源失败", e);
            errorLogService.insertErrorLog(request, e, "授予用户资源失败: " + uuid);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PutMapping("/revokeResource")
    public ResponseEntity<?> revokeResource(@RequestParam String uuid, @RequestParam String resourcePath,@RequestParam String type ,HttpServletRequest request) {
        try {
            userManagementService.revokeResourceByUuid(uuid, resourcePath, type);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            log.error("撤销用户资源失败", e);
            errorLogService.insertErrorLog(request, e, "撤销用户资源失败: " + uuid);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PutMapping("/modifyResource")
    public ResponseEntity<Result<?>> modifyResource(@RequestParam String uuid, @RequestParam String oldResourcePath,
                                                    @RequestParam String newResourcePath,@RequestBody List<String> newTypeList ,HttpServletRequest request) {
        try {
            userManagementService.modifyResourceByUuid(uuid, oldResourcePath, newResourcePath, newTypeList);
            return ResponseEntity.ok(Result.success());
        } catch (Exception e) {
            log.error("修改用户资源失败", e);
            errorLogService.insertErrorLog(request, e, "修改用户资源失败: " + uuid);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.internalServerError(e.getMessage()));
        }
    }

    @GetMapping("/allResources")
    public ResponseEntity<Result<?>> allResources(@RequestParam String uuid) {
        try {
            return ResponseEntity.ok(Result.ok( userManagementService.allResourcesByUuid(uuid)));
        }catch (Exception e) {
            log.error("查询用户资源失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.internalServerError(e.getMessage()));
        }

    }

    @PostMapping("/createResource")
    public ResponseEntity<Result<?>> createResource(@RequestParam String uuid, @RequestParam String resourcePath,@RequestBody List<String> typeList ,HttpServletRequest request) {
        try {
            userManagementService.createResourceByUuid(uuid, resourcePath, typeList);
            return ResponseEntity.ok(Result.success());
        }catch (Exception e) {
            log.error("创建资源失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.internalServerError(e.getMessage()));
        }

    }

    @DeleteMapping("/deleteResource")
    public ResponseEntity<Result<?>> deleteResource(@RequestParam String uuid, @RequestParam String resourcePath ,HttpServletRequest request) {
        try {
            userManagementService.deleteResourceByUuid(uuid, resourcePath);
            return ResponseEntity.ok(Result.success());
        }catch (Exception e) {
            log.error("删除资源失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.internalServerError(e.getMessage()));
        }

    }

    @GetMapping("/dirs")
    public ResponseEntity<Result<List<UserManagementService.DirectoryDTO>>> listDirectories(@RequestParam(value = "path") String path) {
        return ResponseEntity.ok(Result.ok(userManagementService.listDirectories(path)));
    }
}
