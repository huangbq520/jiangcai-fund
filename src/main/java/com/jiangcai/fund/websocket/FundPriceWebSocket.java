package com.jiangcai.fund.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiangcai.fund.service.FundDataService;
import com.jiangcai.fund.util.TradeDayUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 基金净值WebSocket推送服务
 */
@Component
@ServerEndpoint("/ws/fund-price")
public class FundPriceWebSocket {
    
    private static final Logger logger = LoggerFactory.getLogger(FundPriceWebSocket.class);
    
    private static FundDataService fundDataService;
    private static TradeDayUtil tradeDayUtil;
    private static ObjectMapper objectMapper;
    
    // 存储所有连接的客户端session
    private static final Map<String, Session> clients = new ConcurrentHashMap<>();
    
    // 定时任务执行器，用于推送数据
    private static ScheduledExecutorService scheduler;
    
    public static void setServices(FundDataService fundDataService, TradeDayUtil tradeDayUtil, ObjectMapper objectMapper) {
        FundPriceWebSocket.fundDataService = fundDataService;
        FundPriceWebSocket.tradeDayUtil = tradeDayUtil;
        FundPriceWebSocket.objectMapper = objectMapper;
        
        // 启动定时推送任务
        startScheduler();
    }
    
    private static void startScheduler() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fund-price-pusher");
            t.setDaemon(true);
            return t;
        });
        
        // 每分钟检查一次，如果是开盘时间则推送数据
        scheduler.scheduleAtFixedRate(() -> {
            if (tradeDayUtil.isMarketOpen()) {
                // 通知所有客户端数据已更新
                broadcastMarketStatus();
            }
        }, 0, 1, TimeUnit.MINUTES);
    }
    
    @OnOpen
    public void onOpen(Session session) {
        String sessionId = session.getId();
        clients.put(sessionId, session);
        logger.info("WebSocket连接已建立: {}", sessionId);
        
        // 发送初始市场状态
        try {
            Map<String, Object> status = Map.of(
                "type", "status",
                "marketStatus", tradeDayUtil.getMarketStatus(),
                "lastTradeDay", tradeDayUtil.getLastTradeDay().toString()
            );
            session.getBasicRemote().sendText(objectMapper.writeValueAsString(status));
        } catch (IOException e) {
            logger.error("发送初始状态失败", e);
        }
    }
    
    @OnClose
    public void onClose(Session session) {
        String sessionId = session.getId();
        clients.remove(sessionId);
        logger.info("WebSocket连接已关闭: {}", sessionId);
    }
    
    @OnMessage
    public void onMessage(String message, Session session) {
        logger.info("收到消息: {}", message);
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            String action = (String) data.get("action");
            
            if ("subscribe".equals(action)) {
                // 客户端订阅基金价格更新
                String fundCode = (String) data.get("fundCode");
                logger.info("客户端订阅基金: {}", fundCode);
                
                // 立即发送最新数据
                if (fundCode != null && fundDataService != null) {
                    sendFundPrice(session, fundCode);
                }
            } else if ("ping".equals(action)) {
                // 心跳检测
                session.getBasicRemote().sendText("{\"type\":\"pong\"}");
            }
        } catch (Exception e) {
            logger.error("处理消息失败", e);
        }
    }
    
    @OnError
    public void onError(Session session, Throwable error) {
        logger.error("WebSocket错误: {}", error.getMessage());
    }
    
    /**
     * 向指定客户端发送基金价格
     */
    private void sendFundPrice(Session session, String fundCode) {
        try {
            Map<String, Object> fundInfo = fundDataService.getFundQuote(fundCode);
            Map<String, Object> response = Map.of(
                "type", "price",
                "fundCode", fundCode,
                "data", fundInfo
            );
            session.getBasicRemote().sendText(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            logger.error("发送基金价格失败: {}", fundCode, e);
        }
    }
    
    /**
     * 广播市场状态给所有客户端
     */
    public static void broadcastMarketStatus() {
        String status = tradeDayUtil.getMarketStatus();
        String lastTradeDay = tradeDayUtil.getLastTradeDay().toString();
        
        for (Session session : clients.values()) {
            if (session.isOpen()) {
                try {
                    Map<String, Object> message = Map.of(
                        "type", "status",
                        "marketStatus", status,
                        "lastTradeDay", lastTradeDay
                    );
                    session.getBasicRemote().sendText(objectMapper.writeValueAsString(message));
                } catch (IOException e) {
                    logger.error("广播市场状态失败", e);
                }
            }
        }
    }
    
    /**
     * 推送所有持仓基金的最新净值给所有客户端
     */
    public static void broadcastFundPrices(Map<String, BigDecimal> fundPrices) {
        for (Session session : clients.values()) {
            if (session.isOpen()) {
                try {
                    Map<String, Object> message = Map.of(
                        "type", "prices",
                        "prices", fundPrices
                    );
                    session.getBasicRemote().sendText(objectMapper.writeValueAsString(message));
                } catch (IOException e) {
                    logger.error("广播基金价格失败", e);
                }
            }
        }
    }
}