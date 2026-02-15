package cn.programcx.foxnaserver.callback;

import com.fasterxml.jackson.core.JsonProcessingException;

public interface TranscodeCallback {
    void onStatusCallback(long totalMills, long currentMills, double percent) throws JsonProcessingException;
}
