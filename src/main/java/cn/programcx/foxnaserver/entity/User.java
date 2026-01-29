package cn.programcx.foxnaserver.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("tb_users")
public class User {

    @TableId(value = "user_name")
    private String userName;

    private String state;
    private String password;
    private String email;
}
