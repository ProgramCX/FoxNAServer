package cn.programcx.foxnaserver.service.media;

import cn.programcx.foxnaserver.dto.media.MediaInfoDTO;
import cn.programcx.foxnaserver.util.MediaInfoExtractor;
import jakarta.servlet.ServletOutputStream;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class DecodeMediaService {
    public MediaInfoDTO getRangeMediaData(String file) throws Exception {
        return MediaInfoExtractor.extract(file);
    }

    public String detectMediaType(String filePath) {
        FFmpegFrameGrabber grabber = null;
        try {
            grabber = new FFmpegFrameGrabber(filePath);
            grabber.start();

            boolean hasVideo = grabber.getImageWidth() > 0 && grabber.getImageHeight() > 0;
            boolean hasAudio = grabber.getAudioChannels() > 0;

            grabber.stop();

            if (hasVideo) return "video";
            if (hasAudio) return "audio";
            return "other";

        } catch (Exception e) {
            return "other"; // 无法解析
        } finally {
            try {
                if (grabber != null) grabber.release();
            } catch (Exception ignore) {}
        }
    }
}
