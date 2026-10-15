package dev.filenetmcp;

import dev.filenetmcp.gateway.FileNetGateway;
import dev.filenetmcp.util.Util;
import java.util.Collections;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** In-memory gateway so Spring context tests need no Jace.jar. */
@TestConfiguration
class FakeGatewayConfig {
    @Bean
    FileNetGateway gateway() {
        return (FileNetGateway) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{FileNetGateway.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "listObjectStores": return Collections.singletonList(Util.mapOf("symbolicName", "OS1"));
                        case "getObject": return Util.mapOf("os", a[0], "className", a[1], "ref", a[2]);
                        default: throw new IllegalStateException("boom");
                    }
                });
    }
}
