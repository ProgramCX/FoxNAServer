package cn.programcx.foxnaserver.entity.monitor;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Data
@TableName("sys_main_metrics")
public class SysMainMetrics{
    @TableId
    private String id = UUID.randomUUID().toString().replace("-", "");
    private double cpu;
    private Long totalMemory;
    private Long usedMemory;
    private Long uploadSpeed;
    private Long downloadSpeed;
    private Timestamp timestamp = new Timestamp(System.currentTimeMillis());

    private List<SysDiskMetrics> diskMetricsList;
}
