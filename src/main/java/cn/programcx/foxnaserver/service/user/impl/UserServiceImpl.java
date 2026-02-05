package cn.programcx.foxnaserver.service.user.impl;

import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.mapper.UserMapper;
import cn.programcx.foxnaserver.service.user.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public User findUserByUsername(String username) {
        return baseMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUserName, username));
    }
}
