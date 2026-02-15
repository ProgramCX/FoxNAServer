package cn.programcx.foxnaserver.dto.media;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranscodeTask implements Serializable {
    // 转码任务 ID
    private String jobId;

    // 视频文件路径
    private String videoPath;

    // 音频轨索引
    private int audioTrackIndex;

    // 字幕轨索引
    private int subtitleTrackIndex;

    // 输出目录
    private String outputDir;

    // 是否立即观看
    private boolean isImmediate;

    // 重试次数
    private int retryCount;
}

