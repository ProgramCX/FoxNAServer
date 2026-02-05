package cn.programcx.foxnaserver.entity.monitor;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.UUID;

@TableName("sys_disk_metrics")
@Data
public class SysDiskMetrics {
    @TableId
    private String id = UUID.randomUUID().toString().replace("-", "");
    private String mainId;
    private String diskName;
    private Long usedSpace;
    private Long totalSpace;
}
