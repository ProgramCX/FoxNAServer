package cn.programcx.foxnaserver.api.file;

import cn.programcx.foxnaserver.annotation.CheckFilePermission;
import cn.programcx.foxnaserver.entity.Resource;
import cn.programcx.foxnaserver.mapper.ResourceMapper;
import cn.programcx.foxnaserver.service.log.ErrorLogService;
import cn.programcx.foxnaserver.util.JwtUtil;
import cn.programcx.foxnaserver.util.LimitedInputStream;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.URLEncoder;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@RestController
@RequestMapping("/api/file/op")
@Tag(name = "FileDirOperation", description = "文件和目录操作相关接口")
@ApiResponse(responseCode = "403", description = "没有相关权限")
public class FileDirOperationController {
    @Autowired
    private ErrorLogService errorLogService;
    @Autowired
    private ResourceMapper resourceMapper;

    @Operation(
            summary = "删除文件或目录",
            description = "删除指定的文件或目录，支持批量删除"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功删除文件或目录",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "成功示例", externalValue = "classpath:/doc/response/file/delete-200.json")
                    )),
            @ApiResponse(responseCode = "500", description = "删除失败，可能是路径不存在或权限不足")
    })
    @CheckFilePermission(type = "Write", bodyFields = {"paths"})
    @DeleteMapping("delete")
    public ResponseEntity<?> delete(@RequestBody List<String> paths, HttpServletRequest request) {
        Map<String, Object> resultMap = new HashMap<>();

        List<Map<String, Object>> failedPaths = new ArrayList<>();

        for (String path : paths) {
            File dir = new File(path);

            if (!dir.exists()) {
                addFailedPath(failedPaths, path, "路径不存在");
                continue;
            }

            if (dir.isDirectory()) {
                Path pathDel = Paths.get(path);
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
                            removeResourcePathName(dir.toAbsolutePath().toString()); // 删除资源路径
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException e) {
                    errorLogService.insertErrorLog(request, e, "遍历目录 " + pathDel + "失败！" + e.getMessage());
                    addFailedPath(failedPaths, path, e.getMessage());
                }
            } else {
                if (!dir.delete()) {

                    errorLogService.insertErrorLog(request, new Exception("删除文件 " + path + " 失败！"), "删除文件 " + path + " 失败！");

                    addFailedPath(failedPaths, path, "文件删除失败");
                }
            }
        }

        if (!failedPaths.isEmpty()) {
            resultMap.put("status", "failed");
            resultMap.put("successCount", paths.size() - failedPaths.size());
            resultMap.put("failedCount", failedPaths.size());
            resultMap.put("totalCount", paths.size());
            resultMap.put("failedPaths", failedPaths);

            log.error("[{}]删除文件或目录失败，路径数量: {}, 失败数量:{}, 请求的目录数组：{}, 出现错误的目录：{}，", JwtUtil.getCurrentUuid(), paths.size(), failedPaths.size(), paths, failedPaths);
            return new ResponseEntity<>(resultMap, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        resultMap.put("status", "success");
        resultMap.put("totalDeleted", paths.size());

        log.info("[{}]删除文件或目录成功，路径数量: {},目录数组：{}", JwtUtil.getCurrentUuid(), paths.size(), paths);
        return new ResponseEntity<>(resultMap, HttpStatus.OK);
    }

    @Operation(
            summary = "下载文件或文件夹",
            description = "下载指定路径的文件或文件夹，文件夹会自动打包为ZIP"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功下载文件或ZIP包"),
            @ApiResponse(responseCode = "206", description = "部分内容响应，支持断点续传（仅文件）"),
            @ApiResponse(responseCode = "404", description = "文件或文件夹未找到")
    })
    @CheckFilePermission(type = "Read", paramFields = {"path"})
    @GetMapping("get")
    public ResponseEntity<?> get(@RequestHeader(required = false,value = "Range") String Range,@RequestParam("path") String path,@RequestParam(value = "inline",required = false) boolean isInline, HttpServletRequest request) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if (file.isDirectory()) {
            return downloadDirectory(path, request);
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
        if(isInline){
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + URLEncoder.encode(file.getName(), "UTF-8").replaceAll("\\+", "%20") + "\"");
        }else {
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + URLEncoder.encode(file.getName(), "UTF-8").replaceAll("\\+", "%20") + "\"");
        }
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength);

        log.info("[{}]下载文件：{}, Range: {}, 起始字节: {}, 结束字节: {}, 文件长度: {}", JwtUtil.getCurrentUuid(), path, Range, start, end, fileLength);

        return new ResponseEntity<>(resource, headers, Range == null ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT);
    }

    private ResponseEntity<?> downloadDirectory(String path, HttpServletRequest request) {
        File folder = new File(path);
        if (!folder.exists()) {
            log.warn("[{}]下载文件夹失败，路径不存在: {}", JwtUtil.getCurrentUuid(), path);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if (!folder.isDirectory()) {
            log.warn("[{}]下载文件夹失败，路径不是目录: {}", JwtUtil.getCurrentUuid(), path);
            return ResponseEntity.badRequest().body(Map.of("status", "failed", "message", "路径不是文件夹"));
        }

        String zipFileName = folder.getName() + ".zip";
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try (ZipOutputStream zipOut = new ZipOutputStream(byteArrayOutputStream)) {
                zipFolder(folder.toPath(), folder.getName(), zipOut);
            }
            byte[] zipBytes = byteArrayOutputStream.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentLength(zipBytes.length);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + URLEncoder.encode(zipFileName, "UTF-8").replaceAll("\\+", "%20") + "\"");

            log.info("[{}]下载文件夹成功: {}, 大小: {} bytes", JwtUtil.getCurrentUuid(), path, zipBytes.length);

            return new ResponseEntity<>(zipBytes, headers, HttpStatus.OK);
        } catch (IOException e) {
            errorLogService.insertErrorLog(request, e, "打包文件夹 " + path + " 失败：" + e.getMessage());
            log.error("[{}]下载文件夹失败: {}, 错误: {}", JwtUtil.getCurrentUuid(), path, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("status", "failed", "message", "打包文件夹失败"));
        }
    }

    private void zipFolder(Path sourcePath, String entryName, ZipOutputStream zipOut) throws IOException {
        File file = sourcePath.toFile();
        if (file.isHidden()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    zipFolder(child.toPath(), entryName + "/" + child.getName(), zipOut);
                }
            }
        } else {
            ZipEntry zipEntry = new ZipEntry(entryName);
            zipOut.putNextEntry(zipEntry);
            byte[] buffer = new byte[8192];
            try (FileInputStream fis = new FileInputStream(file)) {
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zipOut.write(buffer, 0, len);
                }
            }
            zipOut.closeEntry();
        }
    }


    @Operation(
            summary = "移动文件或目录",
            description = "将指定的文件或目录从一个位置移动到另一个位置，支持批量操作"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功移动文件或目录"),
            @ApiResponse(responseCode = "500", description = "移动失败，可能是路径不存在或权限不足")
    })
    @PutMapping("move")
    @CheckFilePermission(type = "Write", bodyFields = {"pathsList"}, bodyMapKeyNames = {"oldPath", "newPath"})
    public ResponseEntity<?> move(@RequestBody List<Map<String, String>> pathsList, HttpServletRequest request) {
        Map<String, Object> resultMap = new HashMap<>();
        List<Map<String, Object>> failedPaths = new ArrayList<>();
        int successCount = 0;

        for (Map<String, String> pathMap : pathsList) {
            String oldPath = pathMap.get("oldPath");
            String newPath = pathMap.get("newPath");

            if (oldPath == null || newPath == null) {
                addFailedPath(failedPaths, "", "oldPath 或 newPath 不能为空！");
                continue;
            }

            Path sourcePath = Paths.get(oldPath);
            Path targetPath = Paths.get(newPath);

            if (!Files.exists(sourcePath)) {
                addFailedPath(failedPaths, List.of(oldPath, newPath), "源文件不存在！");
                continue;
            }

            try {
                if (Files.isRegularFile(sourcePath)) {
                    // 是普通文件
                    Files.move(sourcePath, targetPath);
                } else {
                    // 是目录
                    copyDirectory(sourcePath, targetPath, failedPaths, request);
                    deleteDirectoryRecursively(sourcePath, request);
                    removeResourcePathName(oldPath); // 删除资源路径
                }
                successCount++;
            } catch (IOException e) {
                errorLogService.insertErrorLog(request, e, "移动 " + sourcePath + " 到" + targetPath + " 失败：" + e.getMessage());
                addFailedPath(failedPaths, List.of(oldPath, newPath), "移动失败：" + e.getMessage());
            }
        }

        if (!failedPaths.isEmpty()) {
            resultMap.put("status", "failed");
            resultMap.put("successCount", successCount);
            resultMap.put("failedCount", pathsList.size() - successCount);
            resultMap.put("failedPaths", failedPaths);
            log.error("[{}]移动文件或目录失败，路径数量: {}, 成功数量: {}, 失败数量: {}, 目录数组：{}", JwtUtil.getCurrentUuid(), pathsList.size(), successCount, failedPaths.size(), pathsList);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultMap);
        }

        resultMap.put("status", "success");
        resultMap.put("totalMoved", successCount);

        log.info("[{}]移动文件或目录成功，路径数量: {}, 成功数量: {}, 失败数量: {}, 目录数组：{}", JwtUtil.getCurrentUuid(), pathsList.size(), successCount, failedPaths.size(), pathsList);
        return ResponseEntity.ok(resultMap);
    }


    @Operation(
            summary = "复制文件或目录",
            description = "将指定的文件或目录从一个位置复制到另一个位置，支持批量操作"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功复制文件或目录"),
            @ApiResponse(responseCode = "500", description = "复制失败，可能是路径不存在或权限不足")
    })
    @CheckFilePermission(type = "Write", bodyFields = {"pathsList"}, bodyMapKeyNames = {"oldPath", "newPath"})
    @PostMapping("copy")
    public ResponseEntity<?> copy(@RequestBody List<Map<String, String>> pathsList, HttpServletRequest request) {
        Map<String, Object> resultMap = new HashMap<>();
        List<Map<String, Object>> failedPaths = new ArrayList<>();
        int successCount = 0;


        for (Map<String, String> pathMap : pathsList) {

            String oldPath = pathMap.get("oldPath");
            String newPath = pathMap.get("newPath");
            Path sourcePath = Paths.get(oldPath);
            Path targetPath = Paths.get(newPath);

            if (!Files.exists(sourcePath)) {
                errorLogService.insertErrorLog(request, new FileNotFoundException("源文件不存在！"), "源文件 " + sourcePath + " 不存在！");
                addFailedPath(failedPaths, List.of(oldPath, newPath), "源文件不存在！");
                continue;
            }

            try {
                if (Files.isRegularFile(sourcePath)) {

                    Files.copy(sourcePath, targetPath);

                } else {
                    copyDirectory(sourcePath, targetPath, failedPaths, request);
                }
                successCount++;
            } catch (IOException e) {
                errorLogService.insertErrorLog(request, e, "复制 " + sourcePath + " 到 " + targetPath + " 失败：" + e.getMessage());
                addFailedPath(failedPaths, List.of(oldPath, newPath), "复制失败：" + e.getMessage());
            }


        }
        if (!failedPaths.isEmpty()) {
            resultMap.put("status", "failed");
            resultMap.put("totalCount", pathsList.size());
            resultMap.put("failedCount", pathsList.size() - successCount);
            resultMap.put("failedPaths", failedPaths);

            log.error("[{}]复制文件或目录失败，路径数量: {}, 成功数量: {}, 失败数量: {}, 目录数组：{}", JwtUtil.getCurrentUuid(), pathsList.size(), successCount, failedPaths.size(), pathsList);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultMap);
        }

        log.info("[{}]复制文件或目录成功，路径数量: {}, 成功数量: {}, 失败数量: {}, 目录数组：{}", JwtUtil.getCurrentUuid(), pathsList.size(), successCount, failedPaths.size(), pathsList);
        resultMap.put("status", "success");
        resultMap.put("totalCopied", successCount);
        return ResponseEntity.ok(resultMap);
    }

    @Operation(
            summary = "重命名文件或目录",
            description = "将指定的文件或目录重命名为新的名称"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功重命名文件或目录"),
            @ApiResponse(responseCode = "500", description = "重命名失败，可能是路径不存在或权限不足"),
            @ApiResponse(responseCode = "404", description = "文件或目录不存在")
    })
    @CheckFilePermission(type = "Write", paramFields = {"path"})
    @PutMapping("rename")
    public ResponseEntity<?> rename(@RequestParam("path") String path,@RequestParam("newName") String newName, HttpServletRequest request) {
        Map<String, Object> resultMap = new HashMap<>();
        File file = new File(path);
        boolean success = true;

        String parent = file.getParent();
        if (!file.exists()) {
            errorLogService.insertErrorLog(request, new FileNotFoundException("文件或目录不存在！"), "重命名文件：文件或目录" + path + "不存在");
            resultMap.put("message", "文件或目录不存在！");
            success = false;
        } else if (!file.renameTo(new File(parent, newName))) {
            errorLogService.insertErrorLog(request, new IOException("重命名文件或目录失败！"), "文件或目录" + path + "重命名为" + newName + "失败");
            resultMap.put("message", "重命名文件或目录失败！");
            success = false;
        }

        resultMap.put("path", path);
        resultMap.put("renameTo", newName);
        if (!success) {
            resultMap.put("status", "failed");
            log.error("[{}]重命名文件或目录失败，路径: {}, 新名称: {}", JwtUtil.getCurrentUuid(), path, newName);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultMap);
        }

        // 更新资源路径
        modifyResourcePathName(path, newName);

        log.info("[{}]重命名文件或目录成功，路径: {}, 新名称: {}", JwtUtil.getCurrentUuid(), path, newName);
        resultMap.put("status", "success");
        return new ResponseEntity<>(resultMap, HttpStatus.OK);
    }

    @Operation(
            summary = "上传文件",
            description = "将文件上传到指定路径，支持创建目录"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功上传文件"),
            @ApiResponse(responseCode = "500", description = "上传失败，可能是路径不存在或权限不足")
    })
    @CheckFilePermission(type = "Write", paramFields = {"path"})
    @PostMapping("upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file, @RequestParam("path") String path, HttpServletRequest request) throws IOException {

        if (file.isEmpty()) {
            errorLogService.insertErrorLog(request, new Exception("文件为空！"), "上传文件：文件为空");
            log.error("[{}]上传文件失败，文件名: {}, 目标路径: {}, 错误信息: 文件为空", JwtUtil.getCurrentUuid(), file.getOriginalFilename(), path);
        }

        Path targetPath = Paths.get(path);
        if (!Files.exists(targetPath.getParent())) {
            Files.createDirectories(targetPath.getParent());
        }

        try {
            File destFile = new File(targetPath.toString());
            InputStream inputStream = file.getInputStream();
            OutputStream outputStream = new FileOutputStream(destFile);

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.close();
        } catch (IOException e) {
            errorLogService.insertErrorLog(request, e, "上传文件" + file.getOriginalFilename() + "到" + path + "失败" + e.getMessage());
            log.error("[{}]上传文件失败，文件名: {}, 目标路径: {}, 错误信息: {}", JwtUtil.getCurrentUuid(), file.getOriginalFilename(), path, e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("status", "failed", "message", "文件上传失败！", "error", e.getMessage()));
        }

        log.info("[{}]上传文件成功，文件名: {}, 目标路径: {}", JwtUtil.getCurrentUuid(), file.getOriginalFilename(), targetPath.toString());
        return ResponseEntity.ok(Map.of("status", "success", "message", "文件上传成功！", "fileName", file.getOriginalFilename(), "path", targetPath.toString()));

    }

    @Operation(
            summary = "创建目录",
            description = "在指定路径创建一个新目录"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功创建目录"),
            @ApiResponse(responseCode = "409", description = "目录已存在"),
            @ApiResponse(responseCode = "500", description = "创建目录失败，可能是路径不存在或权限不足")
    })
    @CheckFilePermission(type = "Write", paramFields = {"path"})
    @PostMapping("createDir")
    public ResponseEntity<?> createDir(@RequestParam("path") String path, HttpServletRequest request) {
        Map<String, Object> resultMap = new HashMap<>();
        Path dirPath = Paths.get(path);

        resultMap.put("path", dirPath.toString());

        if (Files.exists(dirPath)) {
            errorLogService.insertErrorLog(request, new IOException("目录已存在！"), "创建目录失败，目录已存在：" + path);
            resultMap.put("status", "failed");
            resultMap.put("message", "目录已存在！");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(resultMap);
        }


        try {
            Files.createDirectories(dirPath);
            log.info("[{}]创建目录成功，路径: {}", JwtUtil.getCurrentUuid(), path);
            resultMap.put("status", "success");
            resultMap.put("message", "目录创建成功！");
            return ResponseEntity.ok(resultMap);
        } catch (IOException e) {
            errorLogService.insertErrorLog(request, e, "创建目录失败：" + e.getMessage());
            log.error("[{}]创建目录失败，路径: {}, 错误信息: {}", JwtUtil.getCurrentUuid(), path, e.getMessage());
            resultMap.put("status", "failed");
            resultMap.put("message", "目录创建失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultMap);
        }
    }

    private void copyDirectory(Path sourcePath, Path targetPath, List<Map<String, Object>> failedList, HttpServletRequest request) {
        if (targetPath.startsWith(sourcePath)) {
            addFailedPath(failedList, List.of(sourcePath.toAbsolutePath().toString(), targetPath.toAbsolutePath().toString()), "不能将目录复制到其自身或子目录中");
            errorLogService.insertErrorLog(request, new IOException("不能将目录复制到其自身或子目录中"), "复制目录" + sourcePath + "到" + targetPath + "失败：不能将目录复制到其自身或子目录中");
            return;
        }
        try {
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
                    errorLogService.insertErrorLog(request, e, "复制文件" + sourcePath + "到" + targetPath + "失败:" + e.getMessage());

                    addFailedPath(failedList, List.of(sourcePath.toAbsolutePath().toString(), targetPath.toAbsolutePath().toString()), "复制失败：" + e.getMessage());
                }
            });
        } catch (IOException e) {
            errorLogService.insertErrorLog(request, e, "复制文件：遍历目录" + sourcePath + "失败:" + e.getMessage());
            addFailedPath(failedList, List.of(sourcePath, targetPath), "复制失败：" + e.getMessage());
        }
    }


    private void deleteDirectoryRecursively(Path path, HttpServletRequest request) {
        try {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder()) // 先删子文件
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            log.error("[{}]删除目录失败，路径: {}, 错误信息: {}", JwtUtil.getCurrentUuid(), p, e.getMessage());
                            errorLogService.insertErrorLog(request, e, "删除目录" + path + "时" + "删除文件" + p + "失败：" + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.error("[{}]遍历目录失败，路径: {}, 错误信息: {}", JwtUtil.getCurrentUuid(), path, e.getMessage());
        }
    }

    private void addFailedPath(List<Map<String, Object>> failedPaths, Object path, String error) {
        Map<String, Object> map = new HashMap<>();
        map.put("path", path);
        map.put("error", error);
        failedPaths.add(map);
    }

    private void modifyResourcePathName(String path, String newName) {
        LambdaQueryWrapper<Resource> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Resource::getFolderName, path);

        List<Resource> resources = resourceMapper.selectList(queryWrapper);

        for (Resource resource : resources) {
            String oldPath = resource.getFolderName();
            String newPath = Paths.get(oldPath).getParent().resolve(newName).toString().replace(File.separatorChar, '/');
            resource.setFolderName(newPath);
            resourceMapper.updateById(resource);
        }

        log.info("[{}]更新资源路径成功，旧路径: {}, 新路径: {}", JwtUtil.getCurrentUuid(), path, newName);
        if (resources.isEmpty()) {
            log.warn("[{}]没有找到资源路径: {}", JwtUtil.getCurrentUuid(), path);
        }

    }

    private void removeResourcePathName(String path) {
        LambdaQueryWrapper<Resource> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Resource::getFolderName, path.replace(File.separator, "/"));

        List<Resource> resources = resourceMapper.selectList(queryWrapper);

        for (Resource resource : resources) {
            resourceMapper.deleteById(resource.getResourceId());
        }
    }
}
