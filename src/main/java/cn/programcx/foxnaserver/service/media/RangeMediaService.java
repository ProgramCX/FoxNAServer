package cn.programcx.foxnaserver.service.media;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

@Service
public class RangeMediaService {

    public void getRangeMediaData(long start, long end, File file, ServletOutputStream out){
        long contentLength = end - start + 1;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(start);
            byte[] buffer = new byte[8192];
            long bytesLeft = contentLength;
            int len;
            while (bytesLeft > 0 && (len = raf.read(buffer, 0, (int) Math.min(buffer.length, bytesLeft))) != -1) {
                out.write(buffer, 0, len);
                bytesLeft -= len;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
