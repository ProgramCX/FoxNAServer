package cn.programcx.foxnaserver.service.auth;

import cn.programcx.foxnaserver.entity.Permission;
import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.mapper.PermissionMapper;
import cn.programcx.foxnaserver.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthenticationService {

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private UserMapper userMapper;

    public boolean registerAdmin() {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.eq(User::getUserName, "admin");

        if (userMapper.selectOne(queryWrapper) != null) {
            return false;
        }

        User user = new User();
        user.setUserName("admin");
        user.setPassword("123456");
        user.setState("enabled");

        Permission permission = new Permission();
        permission.setOwnerName("admin");
        userMapper.insert(user);

        List<String> areaList = List.of("FILE","STREAM","DDNS","EMAIL","USER");

        areaList.forEach(area -> {
            permission.setAreaName(area);
            permissionMapper.insert(permission);
        });


        return true;
    }

    public void checkUserStatus(String username) throws Exception{
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserName, username);
        User user = userMapper.selectOne(queryWrapper);
        if (user != null) {
            if(!user.getState().equals("enabled")){
                throw new Exception("用户已被禁用！");
            }
        }
    }
}
