package cn.programcx.foxnaserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 转码任务实体
 */
@Data
@TableName("transcode_jobs")
public class TranscodeJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务ID (UUID)
     */
    @TableField("job_id")
    private String jobId;

    /**
     * 创建者用户ID (UUID string)
     */
    @TableField("creator_id")
    private String creatorId;

    /**
     * 视频文件路径
     */
    @TableField("video_path")
    private String videoPath;

    /**
     * 视频文件指纹 (MD5)
     */
    @TableField("fingerprint")
    private String fingerprint;

    /**
     * 任务类型: VIDEO视频转码, SUBTITLE字幕转码
     */
    @TableField("job_type")
    private String jobType;

    /**
     * 音频轨道索引
     */
    @TableField("audio_track_index")
    private Integer audioTrackIndex;

    /**
     * 字幕轨道索引
     */
    @TableField("subtitle_track_index")
    private Integer subtitleTrackIndex;

    /**
     * 状态: PENDING等待中, PROCESSING进行中, COMPLETED已完成, FAILED失败, CANCELLED已取消
     */
    @TableField("status")
    private String status;

    /**
     * 进度 0-100
     */
    @TableField("progress")
    private Double progress;

    /**
     * 当前阶段
     */
    @TableField("current_stage")
    private Integer currentStage;

    /**
     * 总阶段数
     */
    @TableField("total_stages")
    private Integer totalStages;

    /**
     * 输出文件路径
     */
    @TableField("output_path")
    private String outputPath;

    /**
     * HLS播放路径
     */
    @TableField("hls_path")
    private String hlsPath;

    /**
     * 错误信息
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 重试次数
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * 是否立即观看
     */
    @TableField("is_immediate")
    private Boolean immediate;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 完成时间
     */
    @TableField("completed_at")
    private LocalDateTime completedAt;

    /**
     * 任务状态枚举
     */
    public enum Status {
        PENDING,      // 等待中
        PROCESSING,   // 进行中
        COMPLETED,    // 已完成
        FAILED,       // 失败
        CANCELLED     // 已取消
    }

    /**
     * 任务类型枚举
     */
    public enum JobType {
        VIDEO,        // 视频转码
        SUBTITLE      // 字幕转码
    }
}
