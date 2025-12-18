package cn.programcx.foxnaserver.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Result<D> implements Serializable  {
    @Serial
    private static final long serialVersionUID = 1L;

    @JsonProperty("isSuccess")
    private Boolean success = false;

    private D data;

    private int code;

    private String message;

    public Result(Boolean success, D data) {
        this.success = success;
        this.data = data;
    }

    public static <D> Result<D> success() {
        Result<D> result = new Result<>();
        result.success = true;
        result.code = 200;
        result.message = "success";
        return result;
    }

    public static <D> Result<D> ok(D data) {
        Result<D> result = new Result<>();
        result.success = true;
        result.data = data;
        result.code = 200;
        result.message = "success";
        return result;
    }

    public static <D> Result<D> ok(D data, int code) {
        Result<D> result = new Result<>();
        result.success = true;
        result.data = data;
        result.code = code;
        result.message = "success";
        return result;
    }

    public static <D> Result<D> ok(D data, int code, String message) {
        Result<D> result = new Result<>();
        result.success = true;
        result.data = data;
        result.code = code;
        result.message = message;
        return result;
    }


    public static <D> Result<D> notFound(String message) {
        Result<D> result = new Result<>();
        result.success = false;
        result.code = 404;
        result.message = message;
        return result;
    }

    public static <D> Result<D> badRequest(String message) {
        Result<D> result = new Result<>();
        result.success = false;
        result.code = 400;
        result.message = message;
        return result;
    }

    public static <D> Result<D> internalServerError(String message) {
        Result<D> result = new Result<>();
        result.success = false;
        result.code = 500;
        result.message = message;
        return result;
    }

    public static <D> Result<D> fail(int code, String message) {
        Result<D> result = new Result<>();
        result.success = false;
        result.code = code;
        result.message = message;
        return result;
    }

}
