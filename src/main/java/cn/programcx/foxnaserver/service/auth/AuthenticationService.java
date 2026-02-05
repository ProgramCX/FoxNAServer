package cn.programcx.foxnaserver.service.auth;

import cn.programcx.foxnaserver.entity.Permission;
import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.mapper.PermissionMapper;
import cn.programcx.foxnaserver.mapper.UserMapper;
import cn.programcx.foxnaserver.util.MailSenderUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.List;

@Transactional
@Service
public class AuthenticationService {

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private VerificationService verificationService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MailSenderUtil mailSenderUtil;
    @Autowired
    private SpringTemplateEngine springTemplateEngine;

    public boolean registerAdmin() {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.eq(User::getUserName, "admin");

        if (userMapper.selectOne(queryWrapper) != null) {
            return false;
        }

        User user = new User();
        user.setUserName("admin");
        user.setPassword(passwordEncoder.encode("123456"));
        user.setState("enabled");
        user.generateId();

        userMapper.insert(user);

        List<String> areaList = List.of("FILE","STREAM","DDNS","EMAIL","USER","LOG");

        areaList.forEach(area -> {
            Permission permission = new Permission();
            permission.setOwnerUuid(user.getId());
            permission.setAreaName(area);
            permissionMapper.insert(permission);
        });


        return true;
    }

    public void registerUser(String userName,String emailAddr,String password,String code) throws Exception {
        try {
            verificationService.verifyCode(emailAddr, code);
        }catch (Exception e){
            throw new Exception("验证码验证失败：" + e.getMessage());
        }

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserName, userName);
        if (userMapper.selectOne(queryWrapper) != null) {
            throw new Exception("用户已存在！");
        }

        queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getEmail, emailAddr);
        if (userMapper.selectOne(queryWrapper) != null) {
            throw new Exception("邮箱已被注册！");
        }

        User user = new User();
        user.setUserName(userName);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(emailAddr);
        user.setState("enabled");
        user.generateId();

        userMapper.insert(user);

        List<String> areaList = List.of("FILE","STREAM");
        areaList.forEach(area -> {
            Permission permission = new Permission();
            permission.setOwnerUuid(user.getId());
            permission.setAreaName(area);
            permissionMapper.insert(permission);
        });

    }

    public void iniPermissionForNewUser(User user) throws Exception {
        List<String> areaList = List.of("LOG");
        areaList.forEach(area -> {
            Permission permission = new Permission();
            permission.setOwnerUuid(user.getId());
            permission.setAreaName(area);
            permissionMapper.insert(permission);
        });
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


    public void resetPasswordByEmail(String emailAddr, String code, String newPassword) throws Exception {
        verificationService.verifyCode(emailAddr, code);

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getEmail, emailAddr);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            throw new Exception("邮箱未绑定任何用户");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    public void sendUsernameByEmail(String emailAddr, String code) throws Exception {
        verificationService.verifyCode(emailAddr, code);

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getEmail, emailAddr);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            throw new Exception("邮箱未绑定任何用户");
        }

        String username = user.getUserName();
        String subject = "FoxNa Server 用户名找回";

        Context context = new Context();
        context.setVariable("username", username);
        String content = springTemplateEngine.process("emailFindUsername", context);
        mailSenderUtil.sendMail(emailAddr, subject, content);
    }
}
