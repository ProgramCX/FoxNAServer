package cn.programcx.foxnaserver.controller.ddns;

import cn.programcx.foxnaserver.dto.ddns.DDNSTaskStatus;
import cn.programcx.foxnaserver.entity.AccessSecret;
import cn.programcx.foxnaserver.entity.AccessTask;
import cn.programcx.foxnaserver.service.ddns.DDNSAccessSecretService;
import cn.programcx.foxnaserver.service.ddns.DDNSTaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;


@Slf4j
@RestController
@Tag(name = "DDNSTask", description = "DDNS任务相关接口")
@RequestMapping("/api/ddns/tasks")
public class DDNSTaskController {

    @Autowired
    private DDNSAccessSecretService ddnsAccessSecretService;

    @Autowired
    private DDNSTaskService ddnsTaskService;

    @Operation(
            summary = "创建DDNS任务",
            description = "创建一个新的DDNS任务，必须提供AccessSecret ID"
    )
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "任务创建成功"
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "请求参数错误或AccessSecret未找到"
                    )
            }
    )
    @PostMapping("/create")
    public ResponseEntity<?> createDDNSTask(@RequestBody AccessTask accessTask) {
       if (accessTask == null) {
           return ResponseEntity.badRequest().build();
       }

       if( accessTask.getDnsSecretId() != null) {
           try {
               LambdaQueryWrapper<AccessSecret> queryWrapper = new LambdaQueryWrapper<>();
               queryWrapper.eq(AccessSecret::getId, accessTask.getDnsSecretId());
               if (ddnsAccessSecretService.getOne(queryWrapper) == null) {
                   return ResponseEntity.badRequest().body("AccessSecret not found");
               }
               ddnsTaskService.save(accessTask);

               ddnsTaskService.startTask(accessTask.getId());
               return ResponseEntity.ok().build();
           }catch (Exception e) {
               return ResponseEntity.badRequest().body(e.getMessage());
           }
       }
       return ResponseEntity.badRequest().body("AccessSecret ID is required");
    }

    @Operation(
            summary = "删除DDNS任务",
            description = "根据任务ID删除DDNS任务"
    )
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "任务删除成功"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "任务未找到"
                    )
            }
    )
    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteDDNSTask(@RequestParam("id") Long taskId) {
        try {
            boolean removed = ddnsTaskService.removeById(taskId);
            if (removed) {
                ddnsTaskService.stopTask(taskId);
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.status(404).body("Task not found");
            }
        }
        catch (Exception e) {
            log.error("删除DDNS任务失败，taskId = {}：{}", taskId, e.getMessage());
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(
            summary = "更新DDNS任务",
            description = "根据任务ID更新DDNS任务"
    )
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "任务更新成功"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "任务未找到"
                    )
            }
    )
    @PutMapping("/update")
    public ResponseEntity<?> updateDDNSTask(@RequestBody AccessTask accessTask) {
        if (accessTask == null || accessTask.getId() == null) {
            return ResponseEntity.badRequest().body("Invalid task data");
        }
        try {
            boolean updated = ddnsTaskService.updateById(accessTask);
            if (updated) {
                ddnsTaskService.restartTask(accessTask.getId());
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.status(404).body("Task not found");
            }
        } catch (Exception e) {
            log.error("更新DDNS任务失败，taskId = {}：{}", accessTask.getId(), e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(
            summary = "启用DDNS任务",
            description = "启用DDNS任务"
    )
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "任务启用成功"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "任务未找到或启动失败"
                    )
            }
    )
    @PutMapping("/enable")
    public ResponseEntity<?> enableDDNSTask(@RequestParam("id") Long taskId) {
        try {
            ddnsTaskService.enableTask(taskId);
            log.info("DDNS任务启用成功，taskId = {}", taskId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("启用DDNS任务失败，taskId = {}：{}", taskId, e.getMessage());
            return ResponseEntity.status(404).body("Task not found or enable failed");
        }
    }

    @Operation(
            summary = "禁用DDNS任务",
            description = "禁用DDNS任务"
    )
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "任务禁用成功"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "任务未找到或禁用失败"
                    )
            }
    )
    @PutMapping("/disable")
    public ResponseEntity<?> disableDDNSTask(@RequestParam("id") Long taskId) {
        try {
            ddnsTaskService.disableTask(taskId);
            log.info("DDNS任务禁用成功，taskId = {}", taskId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("禁用DDNS任务失败，taskId = {}：{}", taskId, e.getMessage());
            return ResponseEntity.status(404).body("Task not found or disable failed");
        }
    }

    @Operation(
            summary = "重启DDNS任务",
            description = "重启DDNS任务"
    )
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "任务重启成功"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "任务未找到或重启失败"
                    )
            }
    )
    @PutMapping("/restart")
    public ResponseEntity<?> restartDDNSTask(@RequestParam("id") Long taskId) {
        try {
            ddnsTaskService.restartTask(taskId);
            log.info("DDNS任务重启成功，taskId = {}", taskId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("重启DDNS任务失败，taskId = {}：{}", taskId, e.getMessage());
            return ResponseEntity.status(404).body("Task not found or restart failed");
        }
    }

    @Operation(
            summary = "暂停DDNS任务",
            description = "暂停DDNS任务"
    )
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "任务暂停成功"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "任务未找到或暂停失败"
                    )
            }
    )
    @PutMapping("/pause")
    public ResponseEntity<?> pauseDDNSTask(@RequestParam("id") Long taskId) {
        try {
            ddnsTaskService.pauseTask(taskId);
            log.info("DDNS任务暂停成功，taskId = {}", taskId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("暂停DDNS任务失败，taskId = {}：{}", taskId, e.getMessage());
            return ResponseEntity.status(404).body("Task not found or pause failed");
        }
    }

    @Operation(
            summary = "恢复DDNS任务",
            description = "恢复暂停的DDNS任务"
    )
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "任务恢复成功"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "任务未找到或恢复失败"
                    )
            }
    )
    @PutMapping("/resume")
    public ResponseEntity<?> resumeDDNSTask(@RequestParam("id") Long taskId) {
        try {
            ddnsTaskService.resumeTask(taskId);
            log.info("DDNS任务恢复成功，taskId = {}", taskId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("恢复DDNS任务失败，taskId = {}：{}", taskId, e.getMessage());
            return ResponseEntity.status(404).body("Task not found or resume failed");
        }
    }

    @Operation(
            summary = "分页查询DDNS任务",
            description = "根据页码和每页大小分页查询DDNS任务"
    )
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "分页查询成功",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(name = "成功示例", externalValue = "classpath:/doc/response/ddns/list.json")
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "服务器内部错误"
                    )
            }
    )
    @GetMapping("/list")
    public ResponseEntity<?> listDDNSTasks(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        try {
            Page<AccessTask> pageRequest = new Page<>(page, size);
            LambdaQueryWrapper<AccessTask> queryWrapper = new LambdaQueryWrapper<>();

            if (keyword != null && !keyword.trim().isEmpty()) {
                queryWrapper
                        .like(AccessTask::getTaskName, keyword)
                        .or()
                        .like(AccessTask::getDomainRr, keyword);
            }

            IPage<AccessTask> result = ddnsTaskService.page(pageRequest, queryWrapper);
            log.info("分页查询DDNS任务成功，page = {}, size = {}, keyword = {},total={}", page, size, keyword,result.getTotal());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("分页查询失败：{}", e.getMessage());
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(
            summary = "获取DDNS任务状态",
            description = "根据任务ID列表获取DDNS任务状态"
    )
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "任务状态查询成功"
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "请求错误"
                    )
            }
    )
    @GetMapping("/status")
    public ResponseEntity<List<DDNSTaskStatus>> getDDNSTaskStatus(@RequestBody ArrayList<Long> taskIds) {
        List<DDNSTaskStatus> statusList = new ArrayList<>();
        for (Long taskId : taskIds) {
            try {
                DDNSTaskStatus status= ddnsTaskService.getTaskStatus(taskId);
                statusList.add(status);
            } catch (Exception e) {
                log.error("获取DDNS任务状态失败：{}", e.getMessage());
            }
        }

        return ResponseEntity.ok(statusList);
    }



}
