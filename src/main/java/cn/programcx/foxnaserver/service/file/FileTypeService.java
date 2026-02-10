package cn.programcx.foxnaserver.service.file;

import org.apache.tika.mime.MimeTypes;
import org.springframework.stereotype.Service;

/**
 * 文件类型服务，提供文件类型相关的功能
 */
@Service
public class FileTypeService {
    private static final MimeTypes TIKA = MimeTypes.getDefaultMimeTypes();

}
