package cn.programcx.foxnaserver.service.user;

import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserConfigService {
    @Autowired
    private UserMapper userMapper;
    public void changePassword(String userName, String newPassword, String oldPassword) throws Exception {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserName, userName);
        User user = userMapper.selectOne(queryWrapper);
        if (user != null && user.getPassword().equals(oldPassword)) {
            user.setPassword(newPassword);
            userMapper.updateById(user);
        }else {
            throw new Exception("密码不正确");
        }
    }

    public void changeUserName(String oldUserName, String newUserName) throws Exception {
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getUserName, oldUserName).set(User::getUserName, newUserName);
        int rows = userMapper.update(null, updateWrapper);
        if (rows == 0) {
            throw new Exception("用户名修改失败");
        }
    }
}
