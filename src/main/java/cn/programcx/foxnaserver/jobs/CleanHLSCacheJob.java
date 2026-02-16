package cn.programcx.foxnaserver.jobs;


import cn.programcx.foxnaserver.api.media.CacheHLSCleanService;
import cn.programcx.foxnaserver.entity.TranscodeJob;
import cn.programcx.foxnaserver.mapper.TranscodeJobMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class CleanHLSCacheJob{

    @Autowired
    private CacheHLSCleanService cacheHLSCleanService;

    @Autowired
    private TranscodeJobMapper transcodeJobMapper;

    @Scheduled(cron = "0 0/1 * * * ?")
    public void scheduled()
    {
        log.info("正在清理过期任务...");
        List<TranscodeJob> jobs = transcodeJobMapper.selectAllJob();

        for(TranscodeJob job : jobs) {
            if(cacheHLSCleanService.cleanIfExpired(job)) {
               log.info("清理过期任务[{}], 任务路径: {}", job.getJobId(), job.getVideoPath());
            }else{
                log.info("任务[{}]未过期or清理失败, 任务路径: {}", job.getJobId(), job.getVideoPath());
            }
        }
    }
}
