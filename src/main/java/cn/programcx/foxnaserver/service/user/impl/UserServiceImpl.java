package cn.programcx.foxnaserver.service.user.impl;

import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.mapper.UserMapper;
import cn.programcx.foxnaserver.service.user.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

}
