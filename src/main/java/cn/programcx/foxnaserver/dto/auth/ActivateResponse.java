package cn.programcx.foxnaserver.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ActivateResponse {
    private String message;
    private String accessToken;
    private String refreshToken;
    private String username;
    private String uuid;
}
