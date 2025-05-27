package cn.programcx.foxnaserver.websocket;

import cn.programcx.foxnaserver.callback.StatusCallback;
import cn.programcx.foxnaserver.service.StatusService;
import cn.programcx.foxnaserver.util.JwtUtil;
import cn.programcx.foxnaserver.util.SpringContextUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@Slf4j
@ServerEndpoint("/ws/overview")
public class OverviewWebsocket implements StatusCallback {

    private StatusService statusService;


    private static final Set<Session> sessions = new CopyOnWriteArraySet<>();

    public OverviewWebsocket() {

    }

    @OnOpen
    public void onOpen(Session session) {
        String query = session.getQueryString();
        String token = null;
        if (query != null && query.startsWith("token=")) {
            token = query.substring("token=".length());
            JwtUtil jwtUtil = SpringContextUtil.getBean(JwtUtil.class);
            if (token == null || token.isEmpty() || !jwtUtil.isTokenValid(token)) {
                try {
                    session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "非法 token"));
                } catch (IOException e) {
                    log.error("[{}] WebSocket 连接关闭失败: {}", JwtUtil.getCurrentUsername(), e.getMessage());
                }
                return;
            }
        }
        ensureServiceInitialized();
        sessions.add(session);
        statusService.startMonitor(session.getId());
    }

    @OnClose
    public void onClose(Session session) {
        ensureServiceInitialized();
        sessions.remove(session);
        statusService.stopMonitor(session.getId());
    }

    private void ensureServiceInitialized() {
        if (statusService == null) {
            statusService = SpringContextUtil.getBean(StatusService.class);
            statusService.setCallback(this);
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        statusService.stopMonitor(session.getId());
        sessions.remove(session);
    }

    @Override
    public void onStatusCallback(Map<String, Object> status) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(status);

        for (Session session : sessions) {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(json);
                } catch (Exception e) {
                    log.error("[{}] WebSocket 发送消息失败: {}", JwtUtil.getCurrentUsername(), e.getMessage());
                }
            }
        }
    }
}
