package cn.programcx.foxnaserver.dto.media;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 字幕转码任务 DTO
 * 用于将字幕轨道转换为 VTT 格式
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubtitleTranscodeTask implements Serializable {
    
    // 任务 ID
    private String jobId;

    // 视频文件路径
    private String videoPath;

    // 字幕轨索引
    private int subtitleTrackIndex;

    // 输出文件路径
    private String outputPath;

    // 重试次数
    private int retryCount;
}
