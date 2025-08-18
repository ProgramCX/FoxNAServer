package cn.programcx.foxnaserver.dto.media;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@AllArgsConstructor
@Data
public class MediaInfoDTO {

    private String title;           // 视频标题
    private double durationSeconds; // 时长（秒）
    private int videoTrackCount;    // 视频轨道数量
    private int audioTrackCount;    // 音频轨道数量
    private List<TrackDTO> tracks;  // 所有轨道信息

    public MediaInfoDTO() {

    }

    @AllArgsConstructor
    @Data
    public static class TrackDTO {
        private int index;          // 轨道索引
        private String type;        // video/audio/aas
        private String codec;       // 编码器名称
        private String language;    // 语言
        private String title;       // 轨道标题

        public TrackDTO() {

        }
    }

}
