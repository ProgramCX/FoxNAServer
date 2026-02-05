package cn.programcx.foxnaserver.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("tb_users")
public class User {

    @TableId(value = "id")
    private String id = UUID.randomUUID().toString().replace("-", "");

    private String userName;
    private String state;
    private String password;
    private String email;

    // 注册时自动生成 UUID
    public void generateId() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString().replace("-", "");
        }
    }
}
