package cn.programcx.foxnaserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
public class WebsocketConfiguration {
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {

        System.out.println("✅ WebSocket ServerEndpointExporter registered.");
        return new ServerEndpointExporter();
    }


}
