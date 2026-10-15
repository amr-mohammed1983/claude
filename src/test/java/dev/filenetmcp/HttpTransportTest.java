package dev.filenetmcp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** The "http" profile: Basic auth required, POST /mcp speaks JSON-RPC, notifications get 202. */
@SpringBootTest(properties = {"spring.profiles.active=http", "MCP_HTTP_PASSWORD=s3cret", "filenet.object-store=OS1"})
@AutoConfigureMockMvc
@Import(FakeGatewayConfig.class)
class HttpTransportTest {
    static final String AUTH = "Basic " + Base64.getEncoder().encodeToString("mcp:s3cret".getBytes());
    static final String PING = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
    @Autowired MockMvc mvc;

    @Test
    void rejectsMissingOrWrongCredentials() throws Exception {
        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(PING)).andExpect(status().isUnauthorized());
        mvc.perform(post("/mcp").header("Authorization", "Basic " + Base64.getEncoder().encodeToString("mcp:bad".getBytes()))
                .contentType(MediaType.APPLICATION_JSON).content(PING)).andExpect(status().isUnauthorized());
    }

    @Test
    void servesJsonRpcWithCredentials() throws Exception {
        mvc.perform(post("/mcp").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON).content(PING))
                .andExpect(status().isOk()).andExpect(content().json("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}"));
        mvc.perform(post("/mcp").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")).andExpect(status().isAccepted());
        mvc.perform(get("/mcp").header("Authorization", AUTH)).andExpect(status().isMethodNotAllowed());
    }
}
