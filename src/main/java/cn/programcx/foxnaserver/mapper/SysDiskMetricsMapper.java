package cn.programcx.foxnaserver.mapper;

import cn.programcx.foxnaserver.entity.monitor.SysDiskMetrics;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface SysDiskMetricsMapper  extends BaseMapper<SysDiskMetrics> {
}
