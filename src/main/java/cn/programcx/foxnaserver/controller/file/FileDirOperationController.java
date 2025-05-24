package cn.programcx.foxnaserver.controller.file;

import cn.programcx.foxnaserver.util.LimitedInputStream;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Objects;

@RestController
@RequestMapping("/api/file/op")
public class FileDirOperationController {
    @DeleteMapping("delete")
    public ResponseEntity<?> delete(String path) {
        File dir = new File(path);

        if(!dir.exists()){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        if(dir.isDirectory()){
            Path pathDel= Paths.get(path);
            try {
                Files.walkFileTree(pathDel, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                            throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            catch(IOException e){
                e.printStackTrace();
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }else{
            if(!dir.delete()){
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("get")
    public ResponseEntity<?> get(@RequestHeader(required = false) String Range, String path) throws IOException {
        File file = new File(path);
        if(!file.exists()){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if(file.isDirectory()){
            //TODO: 压缩文件夹以便下载
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        long fileLength = file.length();
        long start = 0;
        long end = fileLength - 1;

        if (Range != null && Range.startsWith("bytes=")) {
            String[] ranges = Range.substring(6).split("-");
            try {
                start = Long.parseLong(ranges[0]);
                if (ranges.length > 1 && !ranges[1].isEmpty()) {
                    end = Long.parseLong(ranges[1]);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (start > end || end >= fileLength) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileLength)
                    .build();
        }

        long contentLength = end - start + 1;
        InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
        inputStream.skip(start);

        InputStreamResource resource = new InputStreamResource(new LimitedInputStream(inputStream, contentLength));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(contentLength);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + URLEncoder.encode(file.getName(), "UTF-8").replaceAll("\\+", "%20") + "\"");
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength);

        return new ResponseEntity<>(resource, headers, Range == null ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT);
    }

    @PutMapping("move")
    public ResponseEntity<?> move(String oldPath, String newPath){
        Path sourcePath = Paths.get(oldPath);
        Path targetPath = Paths.get(newPath);

        if (!Files.exists(sourcePath)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("源文件不存在！");
        }

        if (Files.isRegularFile(sourcePath)) {
            try {
                Files.move(sourcePath, targetPath);
                return ResponseEntity.ok().build();
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("文件移动失败: " + e.getMessage());
            }
        }

        try {
            copyDirectory(sourcePath, targetPath);

            deleteDirectoryRecursively(sourcePath);
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("目录移动失败: " + e.getMessage());
        }
    }

    @PostMapping("copy")
    public ResponseEntity<?> copy(String oldPath, String newPath) {
        Path sourcePath = Paths.get(oldPath);
        Path targetPath = Paths.get(newPath);

        if (!Files.exists(sourcePath)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("源文件不存在！");
        }

        if (Files.isRegularFile(sourcePath)) {
            try {
                Files.copy(sourcePath, targetPath);
                return ResponseEntity.ok().build();
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("文件复制失败: " + e.getMessage());
            }
        }

        try {
            copyDirectory(sourcePath, targetPath);
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("目录移动失败: " + e.getMessage());
        }
    }

    @PutMapping("rename")
    public ResponseEntity<?> rename(String path, String newName){
       File file = new File(path);
       if(!file.exists()){
           return new ResponseEntity<>(HttpStatus.NOT_FOUND);
       }
       if( !file.renameTo(new File(newName))){
           return new ResponseEntity<>("重命名失败！",HttpStatus.INTERNAL_SERVER_ERROR);
       }
       return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,@RequestParam String path) throws IOException {
        //上传文件
        if(file.isEmpty()){
            return new ResponseEntity<>("上传文件不能为空",HttpStatus.BAD_REQUEST);
        }

        Path targetPath = Paths.get(path);
        if(!Files.exists(targetPath)){
            Files.createDirectories(targetPath);
        }

        try{
            File destFile = new File(targetPath.toString(), Objects.requireNonNull(file.getOriginalFilename()));
            file.transferTo(destFile);
            return ResponseEntity.ok().build();
        }catch(IOException e){
            return new ResponseEntity<>("上传文件失败！："+e.getMessage(),HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }
    private void copyDirectory(Path sourcePath, Path targetPath) throws IOException {
        Files.walk(sourcePath).forEach(src -> {
            try {
                Path relative = sourcePath.relativize(src);
                Path dest = targetPath.resolve(relative);

                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(src, dest);
                }
            } catch (IOException e) {
                throw new RuntimeException("复制失败: " + e.getMessage(), e);
            }
        });
    }


    private void deleteDirectoryRecursively(Path path) throws IOException {
        Files.walk(path)
                .sorted(Comparator.reverseOrder()) // 先删子文件
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new RuntimeException("删除源目录失败", e);
                    }
                });
    }
}
