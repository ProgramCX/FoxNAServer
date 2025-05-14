package cn.programcx.foxnaserver.controller;

import cn.programcx.foxnaserver.entity.Permission;
import cn.programcx.foxnaserver.entity.Resource;
import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.service.UserManagementService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserManagementController {

    @Autowired
    private UserManagementService userManagementService;

    @Data
   private static class UserPermissionResourceDTO {
        String userName;
        String password;
        List<Permission> permissions;
        List<Resource> resources;
    }

    @PostMapping("/addUser")
    public ResponseEntity<?> addUser(@RequestBody UserPermissionResourceDTO dto) {
        try{
            User user = new User();
            System.out.println(dto.password);
            user.setUserName(dto.userName);
            user.setPassword(dto.password);
            user.setState("enabled");
            userManagementService.addUser(user,dto.permissions,dto.resources);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

}
