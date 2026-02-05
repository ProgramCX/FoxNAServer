package cn.programcx.foxnaserver.mapper;

import cn.programcx.foxnaserver.entity.SysMainMetrics;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SysMainMetricsMapper extends BaseMapper<SysMainMetrics> {
    
    /**
     * Query metrics within a time range with grouping
     * @param startTime Start time
     * @param endTime End time
     * @param groupSeconds Grouping interval in seconds
     * @return List of aggregated metrics
     */
    List<SysMainMetrics> selectRecentMetrics(
            @Param("startTime") LocalDateTime startTime, 
            @Param("endTime") LocalDateTime endTime, 
            @Param("groupSeconds") int groupSeconds
    );
}
