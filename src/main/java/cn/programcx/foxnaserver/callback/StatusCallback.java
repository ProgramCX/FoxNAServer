package cn.programcx.foxnaserver.callback;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Map;

public interface StatusCallback {
    void onStatusCallback(Map<String, Object> status) throws JsonProcessingException;
}
