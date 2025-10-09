package cn.programcx.foxnaserver.service.user;

import cn.programcx.foxnaserver.entity.Permission;
import cn.programcx.foxnaserver.entity.Resource;
import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.mapper.PermissionMapper;
import cn.programcx.foxnaserver.mapper.ResourceMapper;
import cn.programcx.foxnaserver.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserManagementService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PermissionMapper permissionMapper;
    @Autowired
    private ResourceMapper resourceMapper;

    private List<String> permissionList = List.of("SSH","USER","EMAIL","STREAM","FILE","DDNS");

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

    public void delUser(String userName) throws Exception {
        if(userName.equals("admin")){
            throw new  Exception("admin 用户不能被删除！");
        }
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserName, userName);
        userMapper.delete(queryWrapper);
    }

    public void blockUser(String userName) throws Exception {
        if(userName.equals("admin")){
            throw new Exception("admin 用户不能被禁用");
        }
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getUserName, userName).set(User::getState,"disabled");
        userMapper.update(null,updateWrapper);
    }

    public void unblockUser(String userName) throws Exception {
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getUserName, userName).set(User::getState,"enabled");
        userMapper.update(null,updateWrapper);
    }

    public void changePassword(String userName,String newPassword) throws Exception {
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getUserName, userName).set(User::getPassword,newPassword);
         userMapper.update(null,updateWrapper);
    }


    public void grantPermission(String userName,String permissionName) throws Exception {
        if(!permissionList.contains(permissionName)){
            throw new Exception("权限不存在！");
        }
        LambdaQueryWrapper<Permission> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Permission::getOwnerName,userName).eq(Permission::getAreaName,permissionName);
        if(permissionMapper.selectOne(queryWrapper)!=null){
            throw new Exception("用户已经拥有该权限！");
        }
        Permission permission = new Permission();
        permission.setOwnerName(userName);
        permission.setAreaName(permissionName);
        permissionMapper.insert(permission);
    }

    public void revokePermission(String userName,String permissionName) throws Exception {
        if(!permissionList.contains(permissionName)){
            throw new Exception("权限不存在！");
        }
        LambdaQueryWrapper<Permission> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Permission::getOwnerName,userName).eq(Permission::getAreaName,permissionName);
        Permission permission = permissionMapper.selectOne(queryWrapper);
        if(permission==null){
            throw new Exception("用户不拥有该权限！");
        }
        permissionMapper.delete(queryWrapper);
    }

    public List<String> allPermissions(){
        return new ArrayList<>(permissionList);
    }


    public void updateUser(User user) throws Exception {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserName,user.getUserName());
        User userToUpdate = userMapper.selectOne(queryWrapper);
        if(userToUpdate==null){
            throw new Exception("用户不存在！");
        }
        userToUpdate.setUserName(user.getUserName());
        userToUpdate.setState(user.getState());
        userMapper.updateById(user);
    }
}
