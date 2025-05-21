package cn.programcx.foxnaserver.controller;

import cn.programcx.foxnaserver.entity.Permission;
import cn.programcx.foxnaserver.mapper.PermissionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/common")
public class CommonController {

    @Autowired
    private PermissionMapper permissionMapper;

    @GetMapping("/permissions")
    public ResponseEntity<ArrayList<String>> getPermissions(@RequestParam String username) {
        LambdaQueryWrapper<Permission> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Permission::getOwnerName, username);
        List<Permission> permissions = permissionMapper.selectList(queryWrapper);
        ArrayList<String> permissionsString = new ArrayList<>();

        for (Permission permission : permissions) {
            permissionsString.add(permission.getAreaName());
        }

        Collections.reverse(permissionsString);

        return new ResponseEntity<>(permissionsString, HttpStatus.OK);
    }
}
