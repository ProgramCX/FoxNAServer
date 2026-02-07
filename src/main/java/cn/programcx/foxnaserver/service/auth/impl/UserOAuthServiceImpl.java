package cn.programcx.foxnaserver.service.auth.impl;

import cn.programcx.foxnaserver.api.auth.TokenStorageService;
import cn.programcx.foxnaserver.dto.auth.ActivateResponse;
import cn.programcx.foxnaserver.dto.auth.ActivateUserOAuth;
import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.entity.UserOAuth;
import cn.programcx.foxnaserver.mapper.UserMapper;
import cn.programcx.foxnaserver.mapper.UserOAuthMapper;
import cn.programcx.foxnaserver.service.auth.UserOAuthService;
import cn.programcx.foxnaserver.service.auth.VerificationService;
import cn.programcx.foxnaserver.service.user.UserService;
import cn.programcx.foxnaserver.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Transactional
@Service
public class UserOAuthServiceImpl extends ServiceImpl<UserOAuthMapper, UserOAuth> implements UserOAuthService {

    private final VerificationService verificationService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate<String,String> redisTemplate;

    private final String TICKET_PREFIX = "foxnas_oauth:bind:";

    @Autowired
    private TokenStorageService tokenStorageService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    public UserOAuthServiceImpl(VerificationService verificationService, UserMapper userMapper, @Lazy PasswordEncoder passwordEncoder) {
        this.verificationService = verificationService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public UserOAuth findOAuthUser(String provider, String oAuthId){
       LambdaQueryWrapper<UserOAuth> queryWrapper = new LambdaQueryWrapper<>();
       queryWrapper.eq(UserOAuth::getProvider,provider)
               .eq(UserOAuth::getOauthId,oAuthId);
        return baseMapper.selectOne(queryWrapper);
    }

   public UserOAuth addOAuthUser(UserOAuth userOAuth){
        baseMapper.insert(userOAuth);
        return userOAuth;
   }

    public String generateUsernameByOAuthProvider(String provider,String username) {
        return provider.toLowerCase() + "_" + username;
    }

    @Override
    public String generateActivateTicket() throws Exception {
        return UUID.randomUUID().toString();
    }

    @Override
    public ActivateUserOAuth getOAuthUserInfoByTicket(String ticket) throws Exception {
        String key = TICKET_PREFIX + ticket;
        String json = redisTemplate.opsForValue().get(key);
        if(json==null){
            throw new Exception("Ticket过期或无效！");
        }
        Gson gson = new Gson();
        return gson.fromJson(json,ActivateUserOAuth.class);
    }

    @Override
    public void saveTicketByProviderOAuthId(String provider, String oAuthId, String ticket) throws Exception {
        String key = TICKET_PREFIX + ticket;

        ActivateUserOAuth activateUserOAuth = new ActivateUserOAuth();
        activateUserOAuth.setProvider(provider);
        activateUserOAuth.setOAuthId(oAuthId);
        Gson gson = new Gson();
        String json = gson.toJson(activateUserOAuth);

        redisTemplate.opsForValue().set(key,json,1, TimeUnit.HOURS);
    }

    @Override
    public void deleteTicketByTicket(String ticket) throws Exception {
        String key = TICKET_PREFIX + ticket;
        redisTemplate.delete(key);
    }

    @Override
    public boolean validatePassword(String uuid, String password)  {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getId,uuid);
        User user = userMapper.selectOne(queryWrapper);
        if(user==null){
            return false;
        }
        return passwordEncoder.matches(password,user.getPassword());
    }


    public void sendActivationEmailCode(String email) throws Exception {
        verificationService.sendVerificationCode(email);
    }

     public void verifyActivationEmailCode(String email,String code) throws Exception {
         verificationService.verifyCode(email,code);
         LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
         queryWrapper.eq(User::getEmail,email);
         User user = userMapper.selectOne(queryWrapper);
         if(user!=null){
             throw new Exception("邮箱已被注册！");
         }

    }

    public ActivateResponse activateUser(String email , String password, String provider, String oAuthId){
        LambdaQueryWrapper<UserOAuth> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserOAuth::getProvider,provider)
                .eq(UserOAuth::getOauthId,oAuthId);
        UserOAuth userOAuth = baseMapper.selectOne(queryWrapper);

        ActivateResponse activateResponse = new ActivateResponse();
        if(userOAuth==null){
             activateResponse.setMessage("OAuth用户不存在！");
             return activateResponse;
        }
        LambdaQueryWrapper<User> queryWrapperUser = new LambdaQueryWrapper<>();
        queryWrapperUser.eq(User::getUserName,userOAuth.getUserName());
        User user = userMapper.selectOne(queryWrapperUser);
        if(user==null){
             activateResponse.setMessage("用户不存在！");
             return activateResponse;
        }
        if (!"pending".equals(user.getState())) {
             activateResponse.setMessage("用户状态非法，无法激活");
             return activateResponse;
        }
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setState("enabled");
        User exist = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email)
        );
        if (exist != null) {
             activateResponse.setMessage("邮箱已被其他账号绑定");
             return activateResponse;
        }

        final UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUserName());

        // 生成 accessToken 和 refreshToken
        String accessToken = jwtUtil.generateAccessTokenByUuid(user.getId(), userDetails.getAuthorities());
        String refreshToken = jwtUtil.generateRefreshTokenByUuid(user.getId(), userDetails.getAuthorities());

        // 把双 token 存入 redis（使用 uuid 作为 key）
        tokenStorageService.storeAccessToken(accessToken, user.getId());
        tokenStorageService.storeRefreshToken(refreshToken, user.getId());

        userMapper.updateById(user);

        activateResponse.setRefreshToken(refreshToken);
        activateResponse.setAccessToken(accessToken);
        activateResponse.setUsername(user.getUserName());
        activateResponse.setUuid(user.getId());
        activateResponse.setMessage("成功激活！");
        log.info("用户 {} 成功激活！", user.getUserName());
        return activateResponse;
    }

    public void saveUserUserOAuth(UserOAuth userOAuth,User user) {
        userService.save(user);
       this.save(userOAuth);
    }


}

