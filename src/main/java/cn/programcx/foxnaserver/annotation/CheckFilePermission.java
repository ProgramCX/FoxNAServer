package cn.programcx.foxnaserver.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckFilePermission {
    String type() default "Read"; // 权限类型，用于指定检查的权限类型
    String[] paramFields() default {}; // 参数字段名数组，用于指定需要检查权限的参数字段
    String[] bodyFields() default {}; // 请求体字段名数组，用于指定需要检查权限的请求体字段
    String[] bodyMapKeyNames() default {}; // 请求体名称数组，用于指定需要检查权限的请求体名称
    String[] paramNames() default {}; // 参数名称数组，用于指定需要检查权限的参数名称
}
