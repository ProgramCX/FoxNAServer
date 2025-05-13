package cn.programcx.foxnaserver.service;

import cn.programcx.foxnaserver.mapper.PermissionMapper;
import cn.programcx.foxnaserver.mapper.ResourceMapper;
import cn.programcx.foxnaserver.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthenticationService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ResourceMapper  resourceMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private List<String> getUserPermissions(String userName) {
        return null;
    }
}
