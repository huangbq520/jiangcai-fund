package com.jiangcai.fund.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

/**
 * WebSocket配置
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig {
    // WebSocket配置类，启用WebSocket支持
    // 实际端点由 @ServerEndpoint 注解在 FundPriceWebSocket 中定义
}