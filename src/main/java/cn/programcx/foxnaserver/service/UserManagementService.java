package cn.programcx.foxnaserver.service;

import cn.programcx.foxnaserver.entity.Permission;
import cn.programcx.foxnaserver.entity.Resource;
import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.mapper.PermissionMapper;
import cn.programcx.foxnaserver.mapper.ResourceMapper;
import cn.programcx.foxnaserver.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserManagementService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PermissionMapper permissionMapper;
    @Autowired
    private ResourceMapper resourceMapper;


    public void addUser(User user, List<Permission> permissionList, List<Resource> resourceList) throws Exception {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();


        System.out.println(user.getPassword());
        queryWrapper.eq(User::getUserName,user.getUserName());

        if(userMapper.selectOne(queryWrapper)!=null){
            throw new Exception("用户已经存在！");
        }

       userMapper.insert(user);

        permissionList.forEach(permission -> {
            permissionMapper.insert(permission);
        });

        resourceList.forEach(resource -> {
            resourceMapper.insert(resource);
        });

    }
}
