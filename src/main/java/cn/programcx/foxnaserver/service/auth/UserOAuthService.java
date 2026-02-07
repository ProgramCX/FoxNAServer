package cn.programcx.foxnaserver.service.auth;

import cn.programcx.foxnaserver.dto.auth.ActivateResponse;
import cn.programcx.foxnaserver.dto.auth.ActivateUserOAuth;
import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.entity.UserOAuth;
import com.baomidou.mybatisplus.extension.service.IService;

public interface  UserOAuthService extends IService<UserOAuth> {
    public UserOAuth findOAuthUser(String provider, String oAuthId);

    public UserOAuth addOAuthUser(UserOAuth userOAuth);

    public void sendActivationEmailCode(String email) throws Exception;

    public void verifyActivationEmailCode(String email,String code) throws Exception;

    public ActivateResponse activateUser(String email , String password, String provider, String oAuthId) ;

    public void saveUserUserOAuth(UserOAuth userOAuth, User user);

    public String generateUsernameByOAuthProvider(String provider,String username) ;

    String generateActivateTicket() throws Exception;

    public ActivateUserOAuth getOAuthUserInfoByTicket(String ticket) throws Exception;

    public void saveTicketByProviderOAuthId(String provider, String oAuthId, String ticket) throws Exception;

    public void deleteTicketByTicket(String ticket) throws Exception;

    public boolean validatePassword(String uuid, String password);
}
