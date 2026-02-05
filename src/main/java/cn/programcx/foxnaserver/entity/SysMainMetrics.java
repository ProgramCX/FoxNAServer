package cn.programcx.foxnaserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_main_metrics")
public class SysMainMetrics {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private LocalDateTime recordTime;
    
    private Double cpuUsage;
    
    private Double memoryUsed;
    
    private Double memoryTotal;
    
    private Double diskUsed;
    
    private Double diskTotal;
    
    private Long networkRecvSpeed;
    
    private Long networkSentSpeed;
    
    private LocalDateTime createTime;
}
