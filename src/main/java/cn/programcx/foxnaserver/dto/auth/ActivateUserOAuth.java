package cn.programcx.foxnaserver.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ActivateUserOAuth {
    private String provider;
    private String oAuthId;
}

