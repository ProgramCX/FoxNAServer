package cn.programcx.foxnaserver.service.user;

import cn.programcx.foxnaserver.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

public interface UserService extends IService<User> {
    public User findUserByUsername(String username);
}
