package cn.programcx.foxnaserver.mapper;

import cn.programcx.foxnaserver.entity.monitor.SysMainMetrics;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@Repository
public interface SysMainMetricsMapper  extends BaseMapper<SysMainMetrics> {
    List<SysMainMetrics> selectRecentMetrics(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("groupSeconds") Integer groupSeconds
    );
}
