package cn.programcx.foxnaserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Slf4j
@Configuration
public class WebsocketConfiguration {
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {

      log.info("WebsocketConfiguration: Initializing ServerEndpointExporter bean");
        return new ServerEndpointExporter();
    }


}
