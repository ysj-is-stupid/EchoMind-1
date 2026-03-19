package com.example.aiagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * MCP 客户端配置 - 手动注册 Steam MCP 服务
 */
@Configuration
public class McpConfig {

        @Bean(destroyMethod = "close")
        public McpSyncClient steamMcpClient() {
                ServerParameters params = ServerParameters.builder("node")
                                .args(
                                                "F:/Other_Code/SpringAi1/MCP/steam/steam_mcp.js",
                                                "--api-key",
                                                "B4F83D26524686257CECF2DAA50AA47B")
                                .build();

                StdioClientTransport transport = new StdioClientTransport(params,
                                new JacksonMcpJsonMapper(new ObjectMapper()));

                McpSyncClient client = McpClient.sync(transport)
                                .requestTimeout(Duration.ofSeconds(10))
                                .build();

                client.initialize();
                System.out.println("========== Steam MCP Client Initialized ==========");

                return client;
        }
}
