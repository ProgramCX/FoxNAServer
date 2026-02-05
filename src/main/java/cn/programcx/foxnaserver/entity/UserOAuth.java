package cn.programcx.foxnaserver.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_user_oauth")
public class UserOAuth {
    @TableId("id")
    private String id;
    private String userName;
    private String provider;
    private String oauthId;
    private LocalDateTime createdAt;
}
