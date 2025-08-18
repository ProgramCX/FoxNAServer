package cn.programcx.foxnaserver.service.media;

import cn.programcx.foxnaserver.dto.media.MediaInfoDTO;
import cn.programcx.foxnaserver.util.MediaInfoExtractor;
import jakarta.servlet.ServletOutputStream;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class DecodeMediaService {
    public MediaInfoDTO getRangeMediaData(String file) throws Exception {
        return MediaInfoExtractor.extract(file);
    }
}
