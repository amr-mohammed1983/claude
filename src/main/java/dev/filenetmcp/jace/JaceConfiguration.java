package dev.filenetmcp.jace;

import dev.filenetmcp.FileNetProperties;
import dev.filenetmcp.gateway.FileNetGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Active only when the Jace API is on the classpath. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "com.filenet.api.core.Factory")
class JaceConfiguration {
    @Bean
    FileNetGateway jaceGateway(FileNetProperties properties) {
        return new JaceGateway(properties);
    }
}
