package cn.programcx.foxnaserver.annotation;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(
        responseCode = "403",
        description = "没有权限访问该资源",
        content = @io.swagger.v3.oas.annotations.media.Content(
                mediaType = "text/plain",
                schema = @io.swagger.v3.oas.annotations.media.Schema(
                        type = "string",
                        example = "没有权限访问该资源"
                )
        )
)

public @interface CheckFilePermission {
    String type() default "Read"; // 权限类型，用于指定检查的权限类型
    String[] paramFields() default {}; // 参数字段名数组，用于指定需要检查权限的参数字段
    String[] bodyFields() default {}; // 请求体字段名数组，用于指定需要检查权限的请求体字段
    String[] bodyMapKeyNames() default {}; // 请求体名称数组，用于指定需要检查权限的请求体名称
    String[] paramNames() default {}; // 参数名称数组，用于指定需要检查权限的参数名称
}
