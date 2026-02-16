package cn.programcx.foxnaserver.service.media;

import cn.programcx.foxnaserver.config.TranscodeRabbitMQConfig;
import cn.programcx.foxnaserver.dto.media.FFmpegProcessManager;
import cn.programcx.foxnaserver.dto.media.JobStatus;
import cn.programcx.foxnaserver.dto.media.TranscodeTask;
import cn.programcx.foxnaserver.entity.TranscodeJob;
import cn.programcx.foxnaserver.mapper.TranscodeJobMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 转码任务管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TranscodeJobService {

    private final TranscodeJobMapper transcodeJobMapper;
    private final VideoFingerprintService fingerprintService;
    private final RabbitTemplate rabbitTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    private final String tempDir = System.getProperty("user.dir") +
            File.separator + "temp" +
            File.separator + "foxnas" +
            File.separator + "transcode";
    private final FFmpegProcessManager fFmpegProcessManager;

    // 内存中存储正在运行的进程（用于停止任务）
//    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();

    /**
     * 创建视频转码任务
     * 
     * @param creatorId 创建者ID
     * @param videoPath 视频路径
     * @param audioTrackIndex 音频轨道
     * @param subtitleTrackIndex 字幕轨道
     * @param immediate 是否立即观看
     * @param fingerprint 文件指纹（可选）
     * @return 创建的任务
     */
    @Transactional
    public TranscodeJob createVideoJob(String creatorId, String videoPath, 
                                       Integer audioTrackIndex, Integer subtitleTrackIndex,
                                       boolean immediate, String fingerprint, Long expireSecs) {
        // 如果没有提供指纹，生成一个
        if (fingerprint == null || fingerprint.isEmpty()) {
            fingerprint = fingerprintService.generateFingerprint(videoPath);
        }

        // 检查是否已有进行中的相同任务
        TranscodeJob existingJob = transcodeJobMapper.selectByFingerprintAndCreator(fingerprint, creatorId);
        if (existingJob != null) {
            String status = existingJob.getStatus();
            if (TranscodeJob.Status.PENDING.name().equals(status) || 
                TranscodeJob.Status.PROCESSING.name().equals(status)) {
                log.info("用户 [{}] 已有相同视频的在进行中的任务 [{}]，复用", creatorId, existingJob.getJobId());
                return existingJob;
            }
            if (TranscodeJob.Status.COMPLETED.name().equals(status)) {
                log.info("用户 [{}] 已有相同视频的已完成任务 [{}]，复用", creatorId, existingJob.getJobId());
                return existingJob;
            }
        }

        String jobId = UUID.randomUUID().toString();
        String outputDir = tempDir + File.separator + jobId;

        TranscodeJob job = new TranscodeJob();
        job.setJobId(jobId);
        job.setCreatorId(creatorId);
        job.setVideoPath(videoPath);
        job.setFingerprint(fingerprint);
        job.setJobType(TranscodeJob.JobType.VIDEO.name());
        job.setAudioTrackIndex(audioTrackIndex != null ? audioTrackIndex : 0);
        job.setSubtitleTrackIndex(subtitleTrackIndex != null ? subtitleTrackIndex : -1);
        job.setStatus(TranscodeJob.Status.PENDING.name());
        job.setProgress(0.0);
        job.setCurrentStage(0);
        job.setTotalStages(2); // 音频提取 + HLS封装
        job.setOutputPath(outputDir);
        job.setRetryCount(0);
        job.setImmediate(immediate);
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());

        transcodeJobMapper.insert(job);

        // 初始化Redis缓存状态为PENDING
        JobStatus initialStatus = new JobStatus();
        initialStatus.setState(JobStatus.State.PENDING);
        initialStatus.setProgress(0);
        initialStatus.setStages(2);
        initialStatus.setCurrentStage(0);
        initialStatus.setCreateTime(LocalDateTime.now());
        redisTemplate.opsForValue().set("job:" + jobId, initialStatus, 8, TimeUnit.HOURS);

        // 绑定指纹
        fingerprintService.bindFingerprint(fingerprint, jobId);

        // 创建转码任务并发送到队列
        TranscodeTask task = TranscodeTask.builder()
                .jobId(jobId)
                .videoPath(videoPath)
                .audioTrackIndex(job.getAudioTrackIndex())
                .subtitleTrackIndex(job.getSubtitleTrackIndex())
                .outputDir(outputDir)
                .isImmediate(immediate)
                .retryCount(0)
                .fingerprint(fingerprint)
                .build();

        String routingKey = immediate ? "task.priority" : "task.normal";
        rabbitTemplate.convertAndSend(
                TranscodeRabbitMQConfig.EXCHANGE_TRANSCODE,
                routingKey,
                task,
                msg -> {
                    if (immediate) {
                        msg.getMessageProperties().setPriority(9);
                    }
                    return msg;
                }
        );

        log.info("创建视频转码任务 [{}]，用户 [{}]，视频 [{}]", jobId, creatorId, videoPath);
        return job;
    }

    /**
     * 根据ID查询任务
     */
    public TranscodeJob getByJobId(String jobId) {
        return transcodeJobMapper.selectByJobId(jobId);
    }

    /**
     * 根据ID和创建者查询任务
     */
    public TranscodeJob getByJobIdAndCreator(String jobId, String creatorId) {
        TranscodeJob job = transcodeJobMapper.selectByJobId(jobId);
        if (job != null && job.getCreatorId().equals(creatorId)) {
            return job;
        }
        return null;
    }

    /**
     * 分页查询用户的任务列表
     */
    public IPage<TranscodeJob> listJobsByCreator(String creatorId, int page, int size) {
        Page<TranscodeJob> pageParam = new Page<>(page, size);
        return transcodeJobMapper.selectByCreator(pageParam, creatorId);
    }

    /**
     * 查询用户的所有任务
     */
    public List<TranscodeJob> listAllJobsByCreator(String creatorId) {
        return transcodeJobMapper.selectByCreator(new Page<>(1, Integer.MAX_VALUE), creatorId).getRecords();
    }

    /**
     * 按状态查询任务
     */
    public List<TranscodeJob> listJobsByStatus(String status) {
        return transcodeJobMapper.selectByStatus(status);
    }

    /**
     * 更新任务进度（由消费者调用）
     */
    public void updateProgress(String jobId, Double progress, Integer currentStage) {
        transcodeJobMapper.updateProgress(jobId, progress, currentStage);
        
        // 同时更新Redis缓存
        String redisKey = "job:progress:" + jobId;
        redisTemplate.opsForValue().set(redisKey, Map.of(
            "progress", progress,
            "currentStage", currentStage,
            "timestamp", System.currentTimeMillis()
        ), 1, TimeUnit.HOURS);
    }

    /**
     * 更新任务状态为进行中
     */
    public void updateProcessing(String jobId) {
        transcodeJobMapper.updateStatus(jobId, TranscodeJob.Status.PROCESSING.name());
        log.info("任务 [{}] 状态更新为 PROCESSING", jobId);
    }

    /**
     * 更新任务为完成状态
     */
    public void updateCompleted(String jobId, String hlsPath) {
        transcodeJobMapper.updateCompleted(jobId, hlsPath);
        // 绑定指纹
        TranscodeJob job = transcodeJobMapper.selectByJobId(jobId);
        if (job != null && job.getFingerprint() != null) {
            fingerprintService.bindFingerprint(job.getFingerprint(), jobId);
        }
        log.info("任务 [{}] 完成，HLS路径: {}", jobId, hlsPath);
    }

    /**
     * 更新任务为失败状态
     */
    public void updateFailed(String jobId, String errorMessage) {
        transcodeJobMapper.updateFailed(jobId, errorMessage);
        // 失败时清理指纹绑定
        TranscodeJob job = transcodeJobMapper.selectByJobId(jobId);
        if (job != null && job.getFingerprint() != null) {
            fingerprintService.removeFingerprint(job.getFingerprint());
        }
        log.info("任务 [{}] 失败，错误: {}", jobId, errorMessage);
    }

    /**
     * 停止任务
     */
    @Transactional
    public boolean stopJob(String jobId, String creatorId) {
        TranscodeJob job = getByJobIdAndCreator(jobId, creatorId);
        if (job == null) {
            log.warn("用户 [{}] 尝试停止不存在的任务 [{}]", creatorId, jobId);
            return false;
        }

        String status = job.getStatus();
        if (!TranscodeJob.Status.PENDING.name().equals(status) && 
            !TranscodeJob.Status.PROCESSING.name().equals(status)) {
            log.warn("任务 [{}] 当前状态 [{}] 无法停止", jobId, status);
            return false;
        }

        // 如果任务在队列中（PENDING），尝试从队列移除（实际RabbitMQ很难精确移除，这里主要是标记状态）
        if (TranscodeJob.Status.PENDING.name().equals(status)) {
            transcodeJobMapper.updateStatus(jobId, TranscodeJob.Status.CANCELLED.name());
            JobStatus redisJob = getRedisJob(jobId);
            redisJob.setState(JobStatus.State.CANCELLED);
            redisJob.setMessage("用户请求取消");
            redisTemplate.opsForValue().set("job:" + jobId, redisJob);

            log.info("任务 [{}] 已标记为 CANCELLED", jobId);
            return true;
        }

        fFmpegProcessManager.terminateIfExists(jobId);
        transcodeJobMapper.updateStatus(jobId, TranscodeJob.Status.CANCELLED.name());

        // 同步更新Redis状态为CANCELLED
        JobStatus redisJob2 = getRedisJob(jobId);
        if (redisJob2 != null) {
            redisJob2.setState(JobStatus.State.CANCELLED);
            redisJob2.setMessage("用户请求取消");
            redisTemplate.opsForValue().set("job:" + jobId, redisJob2);
        } else {
            JobStatus cancelStatus = new JobStatus();
            cancelStatus.setState(JobStatus.State.CANCELLED);
            cancelStatus.setMessage("用户请求取消");
            redisTemplate.opsForValue().set("job:" + jobId, cancelStatus, 8, TimeUnit.HOURS);
        }

        return true;

    }

    /**
     * 重试失败的任务
     */
    @Transactional
    public boolean retryJob(String jobId, String creatorId) {
        TranscodeJob job = getByJobIdAndCreator(jobId, creatorId);
        if (job == null) {
            return false;
        }

        if (!TranscodeJob.Status.FAILED.name().equals(job.getStatus()) &&
            !TranscodeJob.Status.CANCELLED.name().equals(job.getStatus())) {
            log.warn("任务 [{}] 当前状态 [{}] 无法重试", jobId, job.getStatus());
            return false;
        }

        // 清理旧文件
        cleanupJobFiles(job);

        // 更新状态
        transcodeJobMapper.incrementRetryCount(jobId);
        transcodeJobMapper.updateStatus(jobId, TranscodeJob.Status.PENDING.name());

        // 重新提交到队列
        TranscodeTask task = TranscodeTask.builder()
                .jobId(jobId)
                .videoPath(job.getVideoPath())
                .audioTrackIndex(job.getAudioTrackIndex())
                .subtitleTrackIndex(job.getSubtitleTrackIndex())
                .outputDir(job.getOutputPath())
                .isImmediate(job.getImmediate() != null ? job.getImmediate() : false)
                .retryCount(job.getRetryCount() + 1)
                .fingerprint(job.getFingerprint())
                .build();

        String routingKey = job.getImmediate() != null && job.getImmediate() ? "task.priority" : "task.normal";
        rabbitTemplate.convertAndSend(
                TranscodeRabbitMQConfig.EXCHANGE_TRANSCODE,
                routingKey,
                task
        );

        log.info("任务 [{}] 已重试，第 {} 次尝试", jobId, job.getRetryCount() + 1);
        return true;
    }

    /**
     * 删除任务
     */
    @Transactional
    public boolean deleteJob(String jobId, String creatorId) {
        TranscodeJob job = getByJobIdAndCreator(jobId, creatorId);
        if (job == null) {
            return false;
        }

        // 如果任务正在运行，先停止
        if (TranscodeJob.Status.PROCESSING.name().equals(job.getStatus())) {
            stopJob(jobId, creatorId);
        }

        // 清理文件
        cleanupJobFiles(job);

        // 清理指纹绑定
        if (job.getFingerprint() != null) {
            fingerprintService.removeFingerprint(job.getFingerprint());
        }

        // 删除数据库记录
        int result = transcodeJobMapper.deleteByJobIdAndCreator(jobId, creatorId);
        
        // 清理Redis缓存
        redisTemplate.delete("job:" + jobId);
        redisTemplate.delete("job:progress:" + jobId);

        log.info("任务 [{}] 已删除", jobId);
        return result > 0;
    }

    /**
     * 批量删除用户的所有任务
     */
    @Transactional
    public int deleteAllJobsByCreator(String creatorId) {
        List<TranscodeJob> jobs = listAllJobsByCreator(creatorId);
        for (TranscodeJob job : jobs) {
            // 停止运行中的任务
            if (TranscodeJob.Status.PROCESSING.name().equals(job.getStatus())) {
                stopJob(job.getJobId(), creatorId);
            }
            // 清理文件
            cleanupJobFiles(job);
            // 清理指纹
            if (job.getFingerprint() != null) {
                fingerprintService.removeFingerprint(job.getFingerprint());
            }
            // 清理Redis
            redisTemplate.delete("job:" + job.getJobId());
            redisTemplate.delete("job:progress:" + job.getJobId());
        }

        int result = transcodeJobMapper.deleteByCreator(creatorId);
        log.info("用户 [{}] 的所有 [{}] 个任务已删除", creatorId, result);
        return result;
    }

    /**
     * 获取任务统计
     */
    public Map<String, Object> getStatistics(String creatorId) {
        Long total = transcodeJobMapper.countByCreator(creatorId);
        List<Map<String, Object>> statusCounts = transcodeJobMapper.countByStatus(creatorId);
        
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("total", total);
        stats.put("byStatus", statusCounts);
        
        return stats;
    }

    /**
     * 根据指纹查找已完成的任务
     */
    public TranscodeJob findCompletedByFingerprint(String fingerprint) {
        return transcodeJobMapper.selectCompletedByFingerprint(fingerprint);
    }

    /**
     * 清理任务相关文件
     */
    public void cleanupJobFiles(TranscodeJob job) {
        if (job.getOutputPath() != null) {
            try {
                Path outputPath = Path.of(job.getOutputPath());
                if (Files.exists(outputPath)) {
                    Files.walk(outputPath)
                            .sorted(java.util.Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(file -> {
                                if (!file.delete()) {
                                    log.warn("无法删除文件: {}", file.getAbsolutePath());
                                }
                            });
                    log.info("清理任务 [{}] 的输出目录: {}", job.getJobId(), job.getOutputPath());
                }
            } catch (IOException e) {
                log.error("清理任务 [{}] 文件失败: {}", job.getJobId(), e.getMessage());
            }
        }
    }

    /**
     * 系统启动时恢复进行中的任务
     */
    public void recoverRunningJobs() {
        List<TranscodeJob> runningJobs = transcodeJobMapper.selectRunningJobs();
        log.info("发现 [{}] 个运行中的任务需要恢复", runningJobs.size());

        for (TranscodeJob job : runningJobs) {
            // 将这些任务标记为失败（因为系统重启，这些任务已经丢失）
            transcodeJobMapper.updateFailed(job.getJobId(), "系统重启导致任务中断");

            // 同步更新Redis状态为FAILED
            JobStatus failedStatus = new JobStatus();
            failedStatus.setState(JobStatus.State.FAILED);
            failedStatus.setProgress(0);
            failedStatus.setMessage("系统重启导致任务中断");
            redisTemplate.opsForValue().set("job:" + job.getJobId(), failedStatus, 8, TimeUnit.HOURS);

            // 清理指纹绑定
            if (job.getFingerprint() != null) {
                fingerprintService.removeFingerprint(job.getFingerprint());
            }
            log.info("任务 [{}] 已标记为失败（系统重启）", job.getJobId());
        }
    }

    public JobStatus getRedisJob(String jobId) {
        return (JobStatus) redisTemplate.opsForValue().get("job:" + jobId);
    }
}
