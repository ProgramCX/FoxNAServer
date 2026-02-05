package cn.programcx.foxnaserver.api.file;

import cn.programcx.foxnaserver.annotation.CheckFilePermission;

import cn.programcx.foxnaserver.dto.file.FileInfo;
import cn.programcx.foxnaserver.dto.file.PageResponse;
import cn.programcx.foxnaserver.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/file/info")
@Tag(name = "FileDirInfo", description = "文件目录信息相关接口")
public class FileDirInfoController {

    @Operation(
            summary = "获取目录列表",
            description = "获取指定目录下的文件和子目录列表，支持分页和排序"
    )
    @ApiResponse(responseCode = "200", description = "成功获取目录列表")
    @CheckFilePermission(type = "Read", paramFields = {"path"})
    @GetMapping("/getList")
    public ResponseEntity<PageResponse<FileInfo>> getList(@RequestParam("path") String path,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "200") int pageSize,
                                                          @RequestParam(value = "sortBy", defaultValue = "name") String sortBy,
                                                          @RequestParam(value = "order", defaultValue = "asc") String order) {
        File dir = new File(path);

        if (!dir.exists()) {
            return ResponseEntity.notFound().build();
        }
        if (!dir.isDirectory()) {
            return ResponseEntity.badRequest().build();
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            PageResponse<FileInfo> emptyPage = new PageResponse<>();
            emptyPage.setList(Collections.emptyList());
            emptyPage.setTotal(0);
            emptyPage.setFrom(0);
            emptyPage.setTo(0);
            emptyPage.setPage(page);
            emptyPage.setPageSize(pageSize);
            emptyPage.setTotalPage(0);
            return ResponseEntity.ok(emptyPage);
        }

        List<FileInfo> list = new ArrayList<>();
        for (File file : files) {
            FileInfo info = new FileInfo();
            info.setName(file.getName());
            info.setPath(file.getPath().replace(File.separatorChar, '/'));
            info.setSize(file.length());
            info.setLastModified(file.lastModified());
            info.setType(file.isDirectory() ? "directory" : "file");
            list.add(info);
        }

        // 排序逻辑不变
        if (sortBy != null && !sortBy.isEmpty()) {
            boolean asc = "asc".equalsIgnoreCase(order);
            list.sort((f1, f2) -> {
                int typeCmp = compareFileType(f1.getType(), f2.getType());
                if (typeCmp != 0) return typeCmp;

                Object o1 = getFieldValue(f1, sortBy);
                Object o2 = getFieldValue(f2, sortBy);

                if (o1 == null && o2 == null) return 0;
                if (o1 == null) return asc ? -1 : 1;
                if (o2 == null) return asc ? 1 : -1;

                int cmp;
                if (o1 instanceof String && o2 instanceof String) {
                    cmp = ((String) o1).compareToIgnoreCase((String) o2);
                } else if (o1 instanceof Number && o2 instanceof Number) {
                    cmp = Long.compare(((Number) o1).longValue(), ((Number) o2).longValue());
                } else if (o1 instanceof Comparable && o2 instanceof Comparable) {
                    cmp = ((Comparable) o1).compareTo(o2);
                } else {
                    cmp = o1.toString().compareTo(o2.toString());
                }
                return asc ? cmp : -cmp;
            });
        }

        int total = list.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);

        List<FileInfo> pageList = list.subList(from, to);

        PageResponse<FileInfo> pageResponse = new PageResponse<>();
        pageResponse.setList(pageList);
        pageResponse.setTotal(total);
        pageResponse.setFrom(from);
        pageResponse.setTo(to);
        pageResponse.setPage(page);
        pageResponse.setPageSize(pageSize);
        pageResponse.setTotalPage(total / pageSize + (total % pageSize == 0 ? 0 : 1));

        log.info("[{}]获取目录列表成功: {}, 页码: {}, 每页大小: {}, 排序字段: {}, 排序方式: {}, 本页实际个数：{}",
                JwtUtil.getCurrentUuid(), path, page, pageSize, sortBy, order, pageList.size());

        return ResponseEntity.ok(pageResponse);
    }


    @GetMapping("/dir-list")
    public ResponseEntity<?> listDirectories(@RequestParam(value = "path") String path) {
        try {
            List<Map<String,String>> directories = new ArrayList<>();

            File dir = new File(path);
            if (!dir.exists() || !dir.isDirectory()) {
                return ResponseEntity.badRequest().body("指定路径不存在或不是目录");
            }

            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        Map<String,String> map = new HashMap<>();
                        map.put("name",file.getName());
                        map.put("path",file.getPath().replace(File.separatorChar,'/'));
                        directories.add(map);
                    }
                }
            }
            return ResponseEntity.ok(directories);
        } catch (Exception e) {
            log.error("获取可用目录列表失败：{}", e.getMessage());
            return ResponseEntity.status(500).body(null);
        }
    }


    private int compareFileType(String type1, String type2) {
        if (type1 == null && type2 == null) return 0;
        if (type1 == null) return 1;  // 空的排后面
        if (type2 == null) return -1;

        if (type1.equalsIgnoreCase(type2)) return 0;
        if ("directory".equalsIgnoreCase(type1)) return -1;
        if ("directory".equalsIgnoreCase(type2)) return 1;
        return type1.compareToIgnoreCase(type2);
    }

    /**
     * 通过反射获取FileInfo的字段值
     */
    private Object getFieldValue(FileInfo fileInfo, String fieldName) {
        switch (fieldName) {
            case "name":
                return fileInfo.getName();
            case "path":
                return fileInfo.getPath();
            case "size":
                return fileInfo.getSize();
            case "lastModified":
                return fileInfo.getLastModified();
            case "type":
                return fileInfo.getType();
            default:
                return null;
        }
    }

}
