package cn.programcx.foxnaserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
@Data
@TableName("tb_permissions")
public class Permission {

    @TableId(type= IdType.AUTO)
    private Long permissionId;

    private String areaName;

    private String ownerName;

}
