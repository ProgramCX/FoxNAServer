package cn.programcx.foxnaserver.controller;

import cn.programcx.foxnaserver.entity.Permission;
import cn.programcx.foxnaserver.entity.Resource;
import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.service.UserManagementService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @PostMapping("delUser")
    public ResponseEntity<?> delUser(@RequestParam String userName) {
       try {
           userManagementService.delUser(userName);
           return new ResponseEntity<>(HttpStatus.OK);
       }
       catch (Exception e) {
           return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
       }
    }

    @PostMapping("blockUser")
    public ResponseEntity<?> blockUser(@RequestParam String userName) {
        try {
            userManagementService.blockUser(userName);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("changePassword")
    public ResponseEntity<?> changePassword(@RequestBody Map<String,String> map) {
        String password = map.get("password");
        String userName = map.get("userName");

        if (password == null || userName == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        try{
            userManagementService.changePassword(userName,password);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

}
