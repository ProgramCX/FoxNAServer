package cn.programcx.foxnaserver.api.media;

import cn.programcx.foxnaserver.common.Result;
import cn.programcx.foxnaserver.dto.media.JobStatus;
import cn.programcx.foxnaserver.entity.TranscodeJob;
import cn.programcx.foxnaserver.service.media.TranscodeJobService;
import cn.programcx.foxnaserver.service.media.VideoFingerprintService;
import cn.programcx.foxnaserver.util.JwtUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 转码任务管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/transcode/jobs")
@RequiredArgsConstructor
@Tag(name = "TranscodeJobController", description = "转码任务管理接口")
public class TranscodeJobController {

    private final TranscodeJobService transcodeJobService;
    private final VideoFingerprintService fingerprintService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取当前用户ID (UUID string)
     */
    private String getCurrentUserId() {
        String uuid = JwtUtil.getCurrentUuid();
        if (uuid == null || uuid.isEmpty()) {
            throw new RuntimeException("未登录或登录已过期");
        }
        return uuid;
    }

    /**
     * 创建视频转码任务
     */
    @Operation(summary = "创建视频转码任务", description = "创建一个新的视频转码任务")
    @PostMapping("/create")
    public Result<TranscodeJob> createJob(@RequestBody CreateJobRequest request) {
        try {
            String userId = getCurrentUserId();
            
            // 如果没有提供指纹，生成一个
            String fingerprint = request.getFingerprint();
            if (fingerprint == null || fingerprint.isEmpty()) {
                fingerprint = fingerprintService.generateFingerprint(request.getVideoPath());
            }
            
            // 检查是否已有相同的完成任务
            TranscodeJob existingJob = transcodeJobService.findCompletedByFingerprint(fingerprint);
            if (existingJob != null && existingJob.getCreatorId().equals(userId)) {
                log.info("用户 [{}] 请求转码已存在的视频 [{}]，复用任务 [{}]", 
                    userId, request.getVideoPath(), existingJob.getJobId());
                return Result.success(existingJob, "该视频已转码完成，直接复用");
            }
            
            TranscodeJob job = transcodeJobService.createVideoJob(
                userId,
                request.getVideoPath(),
                request.getAudioTrackIndex(),
                request.getSubtitleTrackIndex(),
                request.isImmediate(),
                fingerprint
            );
            
            return Result.success(job);
        } catch (Exception e) {
            log.error("创建转码任务失败: {}", e.getMessage());
            return Result.error(500, "创建转码任务失败: " + e.getMessage());
        }
    }

    /**
     * 检查指纹状态
     */
    @Operation(summary = "检查视频指纹", description = "检查视频是否已转码过，避免重复转码")
    @GetMapping("/check-fingerprint")
    public Result<FingerprintCheckResult> checkFingerprint(
            @Parameter(description = "视频文件路径") @RequestParam String videoPath,
            @Parameter(description = "指纹（可选，不传则后端计算）") @RequestParam(required = false) String fingerprint) {
        try {
            String userId = getCurrentUserId();
            
            if (fingerprint == null || fingerprint.isEmpty()) {
                fingerprint = fingerprintService.generateFingerprint(videoPath);
            }
            
            // 检查用户是否已有该视频的任务
            TranscodeJob userJob = transcodeJobService.findCompletedByFingerprint(fingerprint);
            if (userJob != null && userJob.getCreatorId().equals(userId)) {
                return Result.success(new FingerprintCheckResult(true, userJob.getJobId(), 
                    fingerprint, userJob.getHlsPath(), userJob.getStatus()), "已存在可用的转码结果");
            }
            
            // 检查是否有进行中的任务
            final String fp = fingerprint; // lambda需要final变量
            TranscodeJob processingJob = transcodeJobService.listAllJobsByCreator(userId).stream()
                .filter(j -> fp.equals(j.getFingerprint()))
                .filter(j -> TranscodeJob.Status.PENDING.name().equals(j.getStatus()) ||
                            TranscodeJob.Status.PROCESSING.name().equals(j.getStatus()))
                .findFirst()
                .orElse(null);
                
            if (processingJob != null) {
                return Result.success(new FingerprintCheckResult(false, processingJob.getJobId(),
                    fingerprint, null, processingJob.getStatus()), "转码任务进行中");
            }
            
            return Result.success(new FingerprintCheckResult(false, null, fingerprint, null, null), 
                "未找到转码记录，需要新建任务");
                
        } catch (Exception e) {
            log.error("检查指纹失败: {}", e.getMessage());
            return Result.error(500, "检查指纹失败: " + e.getMessage());
        }
    }

    /**
     * 查询任务详情
     */
    @Operation(summary = "查询任务详情", description = "根据任务ID查询转码任务详情")
    @GetMapping("/{jobId}")
    public Result<TranscodeJob> getJobDetail(
            @Parameter(description = "任务ID") @PathVariable String jobId) {
        try {
            String userId = getCurrentUserId();
            TranscodeJob job = transcodeJobService.getByJobIdAndCreator(jobId, userId);

            if (job == null) {
                return Result.error(404, "任务不存在或无权限访问");
            }
            JobStatus jobStatus = (JobStatus) redisTemplate.opsForValue().get("job:" + jobId);
            if (jobStatus != null) {
                job.setProgress(jobStatus.getProgress());
                job.setTotalStages(jobStatus.getStages());
                job.setCurrentStage(jobStatus.getCurrentStage());
            }
//
            return Result.success(job);
        } catch (Exception e) {
            log.error("查询任务详情失败: {}", e.getMessage());
            return Result.error(500, "查询任务详情失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询任务列表
     */
    @Operation(summary = "查询任务列表", description = "分页查询当前用户的转码任务列表")
    @GetMapping("/list")
    public Result<IPage<TranscodeJob>> listJobs(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") int size) {
        try {
            String userId = getCurrentUserId();
            IPage<TranscodeJob> jobs = transcodeJobService.listJobsByCreator(userId, page, size);
            // 从Redis获取任务状态
            for (TranscodeJob job : jobs.getRecords()) {
                JobStatus jobStatus = (JobStatus) redisTemplate.opsForValue().get("job:" + job.getJobId());
                if (jobStatus != null) {
                    job.setProgress(jobStatus.getProgress());
                    job.setTotalStages(jobStatus.getStages());
                    job.setCurrentStage(jobStatus.getCurrentStage());
                }
            }
            return Result.success(jobs);
        } catch (Exception e) {
            log.error("查询任务列表失败: {}", e.getMessage());
            return Result.error(500, "查询任务列表失败: " + e.getMessage());
        }
    }

    /**
     * 查询所有任务（不分页）
     */
    @Operation(summary = "查询所有任务", description = "查询当前用户的所有转码任务")
    @GetMapping("/list-all")
    public Result<List<TranscodeJob>> listAllJobs() {
        try {
            String userId = getCurrentUserId();
            List<TranscodeJob> jobs = transcodeJobService.listAllJobsByCreator(userId);
            for (TranscodeJob job : jobs) {
                JobStatus jobStatus = (JobStatus) redisTemplate.opsForValue().get("job:" + job.getJobId());
                if (jobStatus != null) {
                    job.setProgress(jobStatus.getProgress());
                    job.setTotalStages(jobStatus.getStages());
                    job.setCurrentStage(jobStatus.getCurrentStage());
                }
            }
            return Result.success(jobs);
        } catch (Exception e) {
            log.error("查询任务列表失败: {}", e.getMessage());
            return Result.error(500, "查询任务列表失败: " + e.getMessage());
        }
    }

    /**
     * 停止任务
     */
    @Operation(summary = "停止任务", description = "停止进行中的转码任务")
    @PostMapping("/{jobId}/stop")
    public Result<String> stopJob(
            @Parameter(description = "任务ID") @PathVariable String jobId) {
        try {
            String userId = getCurrentUserId();
            boolean success = transcodeJobService.stopJob(jobId, userId);
            if (success) {
                return Result.success(null, "任务已停止");
            } else {
                return Result.error(400, "任务不存在或无法停止");
            }
        } catch (Exception e) {
            log.error("停止任务失败: {}", e.getMessage());
            return Result.error(500, "停止任务失败: " + e.getMessage());
        }
    }

    /**
     * 重试任务
     */
    @Operation(summary = "重试任务", description = "重试失败或已取消的转码任务")
    @PostMapping("/{jobId}/retry")
    public Result<String> retryJob(
            @Parameter(description = "任务ID") @PathVariable String jobId) {
        try {
            String userId = getCurrentUserId();
            boolean success = transcodeJobService.retryJob(jobId, userId);
            if (success) {
                return Result.success(null, "任务已重新提交");
            } else {
                return Result.error(400, "任务不存在或无法重试");
            }
        } catch (Exception e) {
            log.error("重试任务失败: {}", e.getMessage());
            return Result.error(500, "重试任务失败: " + e.getMessage());
        }
    }

    /**
     * 删除任务
     */
    @Operation(summary = "删除任务", description = "删除转码任务及其相关文件")
    @DeleteMapping("/{jobId}")
    public Result<String> deleteJob(
            @Parameter(description = "任务ID") @PathVariable String jobId) {
        try {
            String userId = getCurrentUserId();
            boolean success = transcodeJobService.deleteJob(jobId, userId);
            if (success) {
                return Result.success(null, "任务已删除");
            } else {
                return Result.error(404, "任务不存在或无权限删除");
            }
        } catch (Exception e) {
            log.error("删除任务失败: {}", e.getMessage());
            return Result.error(500, "删除任务失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除所有任务
     */
    @Operation(summary = "批量删除所有任务", description = "删除当前用户的所有转码任务")
    @DeleteMapping("/delete-all")
    public Result<String> deleteAllJobs() {
        try {
            String userId = getCurrentUserId();
            int count = transcodeJobService.deleteAllJobsByCreator(userId);
            return Result.success(null, "已删除 " + count + " 个任务");
        } catch (Exception e) {
            log.error("批量删除任务失败: {}", e.getMessage());
            return Result.error(500, "批量删除任务失败: " + e.getMessage());
        }
    }

    /**
     * 获取任务统计
     */
    @Operation(summary = "获取任务统计", description = "获取当前用户的转码任务统计信息")
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        try {
            String userId = getCurrentUserId();
            Map<String, Object> stats = transcodeJobService.getStatistics(userId);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取任务统计失败: {}", e.getMessage());
            return Result.error(500, "获取任务统计失败: " + e.getMessage());
        }
    }

    /**
     * 获取任务实时进度（轮询）
     */
     @Operation(summary = "获取任务实时进度", description = "轮询获取任务的实时进度")
     @GetMapping("/{jobId}/progress")
     public Result<Map<String,String>> getJobProgress(
             @Parameter(description = "任务ID") @PathVariable String jobId) {
         try {
             JobStatus jobStatus = (JobStatus) redisTemplate.opsForValue().get("job:" + jobId);
             if (jobStatus ==  null) {
                 return Result.error(404, "任务不存在或进度未更新");
             }
             Map<String,String> progress = new HashMap<>();
             progress.put("progress", String.format("%.2f", jobStatus.getProgress()));
             progress.put("totalStages", String.valueOf(jobStatus.getStages()));
             progress.put("currentStage", String.valueOf(jobStatus.getCurrentStage()));
             progress.put("state", jobStatus.getState().name());
             return Result.success(progress);
         } catch (Exception e) {
             log.error("获取任务进度失败: {}", e.getMessage());
             return Result.error(500, "获取任务进度失败: " + e.getMessage());
         }
     }


    // ==================== 请求/响应 DTO ====================

    @Data
    public static class CreateJobRequest {
        private String videoPath;
        private Integer audioTrackIndex = 0;
        private Integer subtitleTrackIndex = -1;
        private boolean immediate = false;
        private String fingerprint; // 可选
    }

    @Data
    public static class FingerprintCheckResult {
        private boolean existed;       // 是否已存在可用结果
        private String jobId;          // 任务ID
        private String fingerprint;    // 指纹
        private String hlsPath;        // HLS播放路径
        private String status;         // 任务状态

        public FingerprintCheckResult(boolean existed, String jobId, String fingerprint, 
                                       String hlsPath, String status) {
            this.existed = existed;
            this.jobId = jobId;
            this.fingerprint = fingerprint;
            this.hlsPath = hlsPath;
            this.status = status;
        }
    }
}
