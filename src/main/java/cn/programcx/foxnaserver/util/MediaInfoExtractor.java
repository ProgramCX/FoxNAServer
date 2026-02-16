package cn.programcx.foxnaserver.util;

import cn.programcx.foxnaserver.dto.media.MediaInfoDTO;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.ffmpeg.global.avcodec.avcodec_get_name;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

public class MediaInfoExtractor {

    public static MediaInfoDTO extract(String filePath) throws Exception {
        MediaInfoDTO dto = new MediaInfoDTO();
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(filePath)) {
            grabber.start();

            AVFormatContext fmtCtx = grabber.getFormatContext();

            // 文件级信息
            dto.setTitle(grabber.getFormatContext().metadata() != null ? "Unknown" : null); // 可优化读取 title
            dto.setDurationSeconds(grabber.getLengthInTime() / 1_000_000);
            dto.setVideoTrackCount(grabber.getVideoStream());
            dto.setAudioTrackCount(grabber.getAudioStream());

            // 遍历轨道
            List<MediaInfoDTO.TrackDTO> tracks = new ArrayList<>();
            int nbStreams = fmtCtx.nb_streams();
            int audioIndex = 0;
            int videoIndex = 0;
            int subtitleIndex = 0;
            int othersIndex = 0;
            for (int i = 0; i < nbStreams; i++) {
                AVStream stream = fmtCtx.streams(i);
                MediaInfoDTO.TrackDTO track = new MediaInfoDTO.TrackDTO();
                track.setIndex(i);

                switch (stream.codecpar().codec_type()) {
                    case AVMEDIA_TYPE_VIDEO -> {
                        track.setType("video");
                        track.setIndex(videoIndex++);
                    }
                    case AVMEDIA_TYPE_AUDIO -> {
                        track.setType("audio");
                        track.setIndex(audioIndex++);
                    }
                    case AVMEDIA_TYPE_SUBTITLE -> {
                        track.setType("subtitle");
                        track.setIndex(subtitleIndex++);
                    }
                    default -> {
                        track.setType("others");
                        track.setIndex(othersIndex++);
                    }
                }

                track.setCodec(avcodec_get_name(stream.codecpar().codec_id()).getString());

                // 轨道 metadata
                AVDictionary meta = stream.metadata();
                if (meta != null) {
                    var entry = av_dict_get(meta, "language", null, 0);
                    track.setLanguage(entry != null ? entry.value().getString() : "und");
                    var titleEntry = av_dict_get(meta, "title", null, 0);
                    track.setTitle(titleEntry != null ? titleEntry.value().getString() : "");
                }

                tracks.add(track);
            }

            dto.setTracks(tracks);

            grabber.stop();
        }

        return dto;
    }
}
