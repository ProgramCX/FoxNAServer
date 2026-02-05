package cn.programcx.foxnaserver.controller.user;

import cn.programcx.foxnaserver.entity.Permission;
import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.mapper.PermissionMapper;
import cn.programcx.foxnaserver.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/common")
@Tag(name = "Common", description = "公共接口")
public class CommonController {

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private UserMapper userMapper;

    @Operation(
            summary = "获取用户权限列表",
            description = "根据用户名获取该用户的权限列表"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功获取权限列表",
            content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    type = "array",
                                    example = "[\"ddns\", \"ssh\"]"
                            )
            )
                    ),
            @ApiResponse(responseCode = "404", description = "用户未找到")
    })
    @GetMapping("/permissions")
    public ResponseEntity<ArrayList<String>> getPermissions(@RequestParam("username") String username) {
        //判断用户是否存在
        LambdaQueryWrapper<User> userQueryWrapper = new LambdaQueryWrapper<>();
        userQueryWrapper.eq(User::getUserName, username);
        User user = userMapper.selectOne(userQueryWrapper);
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        // 查询用户权限
        LambdaQueryWrapper<Permission> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Permission::getOwnerUuid, user.getId());
        List<Permission> permissions = permissionMapper.selectList(queryWrapper);
        ArrayList<String> permissionsString = new ArrayList<>();

        for (Permission permission : permissions) {
            permissionsString.add(permission.getAreaName());
        }

        Collections.reverse(permissionsString);

        return new ResponseEntity<>(permissionsString, HttpStatus.OK);
    }

    @GetMapping("/permissionsByUuid")
    public ResponseEntity<?> userPermission(@RequestParam(value = "uuid") String uuid) {
        try{
            LambdaQueryWrapper<Permission> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Permission::getOwnerUuid, uuid);
            List<Permission> permissions = permissionMapper.selectList(queryWrapper);
            return ResponseEntity.ok(permissions);
        }catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}
