package cn.programcx.foxnaserver.dto.media;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JobStatus {
    public enum State {
        PENDING,    // 等待中
        PROCESSING,    // 进行中
        COMPLETED,    // 成功
        FAILED,      // 失败
        CANCELLED    // 取消
    }

    // 任务状态
    private State state;

    // 任务进度 0-100
    private double progress;

    // 任务完成后的 HlS 文件路径
    private String hlsPath;

    // 任务创建时间
    private LocalDateTime createTime;

    // 任务总阶段数
    private int stages;

    // 当前阶段
    private int currentStage;

    private String message;
}
