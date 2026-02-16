package cn.programcx.foxnaserver.api.media;

import cn.programcx.foxnaserver.entity.TranscodeJob;
import cn.programcx.foxnaserver.service.media.TranscodeJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class CacheHLSCleanService {

    @Autowired
    private TranscodeJobService transcodeJobService;

    /**
     * 检查任务是否过期，如果过期则删除任务记录和文件
     *
     * @param job 需要检验的任务
     * @return 如果任务过期且清理成功则返回true，否则返回false
     */
    public boolean cleanIfExpired(TranscodeJob job) {
        Long expireSecs = job.getExpireSecs();
        LocalDateTime completedTime = job.getCompletedAt();
        LocalDateTime updatedTime = job.getUpdatedAt();
        LocalDateTime createTime = job.getCreatedAt();

        if (completedTime != null) {
            if (completedTime.isBefore(LocalDateTime.now().minusSeconds(expireSecs))) {
                return cleanupJob(job);
            }
        } else if (updatedTime != null) {
            if (updatedTime.isBefore(LocalDateTime.now().minusSeconds(expireSecs))) {
                return  cleanupJob(job);
            }
        } else if (createTime != null) {
            if (createTime.isBefore(LocalDateTime.now().minusSeconds(expireSecs))) {
                return  cleanupJob(job);
            }
        }
        return false;
    }

    public boolean cleanupJob(TranscodeJob job) {
        if (job.getStatus().equals(TranscodeJob.Status.FAILED.name()) ||
                job.getStatus().equals(TranscodeJob.Status.CANCELLED.name())) {
            try {
                transcodeJobService.cleanupJobFiles(job);
                return true;
            } catch (Exception e) {
                return false;
            }
        } else if (job.getStatus().equals(TranscodeJob.Status.PENDING.name())) {
            return true;
        }
        return transcodeJobService.deleteJob(job.getJobId(), job.getCreatorId());
    }
}
