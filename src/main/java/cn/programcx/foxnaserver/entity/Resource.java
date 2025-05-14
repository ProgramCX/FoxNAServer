package cn.programcx.foxnaserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
@Data
@TableName("tb_resources")
public class Resource {

    @TableId(type = IdType.AUTO)
    private Long resourceId;

    private String ownerName;

    private String folderName;

    private String permissionType;


}
