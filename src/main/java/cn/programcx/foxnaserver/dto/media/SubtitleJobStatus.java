package cn.programcx.foxnaserver.dto.media;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字幕转码任务状态
 */
@Data
public class SubtitleJobStatus {
    
    public enum State {
        PENDING,      // 等待中
        PROCESSING,   // 进行中
        COMPLETED,    // 成功
        FAILED        // 失败
    }

    // 任务状态
    private State state;

    // 任务进度 0-100
    private double progress;

    // 任务完成后的 VTT 文件访问路径
    private String vttPath;

    // 错误信息
    private String message;

    // 任务创建时间
    private LocalDateTime createTime;

    // 任务完成时间
    private LocalDateTime completeTime;
}
