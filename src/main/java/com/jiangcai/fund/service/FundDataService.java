package com.jiangcai.fund.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FundDataService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public FundDataService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 获取基金实时估值
     */
    public Map<String, Object> getFundQuote(String fundCode) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 东方财富基金估值接口
            String url = "https://fund.eastmoney.com/pingzhongdata/" + fundCode + ".js";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Accept-Charset", "GB2312,GBK,UTF-8");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // 使用exchange with String response - let RestTemplate handle encoding
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
            
            // 使用UTF-8解码
            String responseBody = new String(response.getBody(), StandardCharsets.UTF_8);
            
            if (responseBody != null) {
                // 直接从JS中提取数据
                
                // 提取基金名称
                Pattern namePattern = Pattern.compile("var\\s+fS_name\\s*=\\s*\"([^\"]+)\"");
                Matcher nameMatcher = namePattern.matcher(responseBody);
                if (nameMatcher.find()) {
                    result.put("fundName", nameMatcher.group(1));
                }
                
                // 提取基金代码
                Pattern codePattern = Pattern.compile("var\\s+fS_code\\s*=\\s*\"([^\"]+)\"");
                Matcher codeMatcher = codePattern.matcher(responseBody);
                if (codeMatcher.find()) {
                    result.put("fundCode", codeMatcher.group(1));
                }
                
                // 提取净值数据 - 从 Data_netWorthTrend
                Pattern trendPattern = Pattern.compile("var\\s+Data_netWorthTrend\\s*=\\s*(\\[.+?\\]);");
                Matcher trendMatcher = trendPattern.matcher(responseBody);
                
                if (trendMatcher.find()) {
                    String trendData = trendMatcher.group(1);
                    try {
                        JsonNode array = objectMapper.readTree(trendData);
                        if (array.isArray() && array.size() > 0) {
                            // 获取最后一个元素（最新净值）
                            JsonNode lastItem = array.get(array.size() - 1);
                            
                            if (lastItem.has("y")) {
                                BigDecimal dwjz = new BigDecimal(lastItem.get("y").asText());
                                result.put("dwjz", dwjz.setScale(4, RoundingMode.HALF_UP));
                                
                                // 添加时间戳
                                if (lastItem.has("x")) {
                                    long timestamp = lastItem.get("x").asLong();
                                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                                    sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
                                    String updateTime = sdf.format(new Date(timestamp));
                                    result.put("updateTime", updateTime);
                                }
                                
                                // 获取昨日净值（倒数第二个元素）
                                if (array.size() >= 2) {
                                    JsonNode yesterdayItem = array.get(array.size() - 2);
                                    if (yesterdayItem.has("y")) {
                                        BigDecimal yesterdayNav = new BigDecimal(yesterdayItem.get("y").asText());
                                        result.put("yesterdayNav", yesterdayNav.setScale(4, RoundingMode.HALF_UP));
                                    }
                                }
                                
                                // 获取涨跌幅
                                if (lastItem.has("equityReturn") && !lastItem.get("equityReturn").isNull()) {
                                    BigDecimal zzjz = new BigDecimal(lastItem.get("equityReturn").asText());
                                    result.put("zzjz", zzjz.setScale(4, RoundingMode.HALF_UP));
                                    result.put("changeRate", zzjz.setScale(2, RoundingMode.HALF_UP));
                                    
                                    // 计算净值变化 = 净值 * (涨跌幅/100)
                                    BigDecimal changeDwjz = dwjz.multiply(zzjz)
                                            .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
                                    result.put("changeDwjz", changeDwjz);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                
                result.put("success", true);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取基金估值失败: " + e.getMessage());
        }
        
        if (!result.containsKey("success")) {
            result.put("success", false);
            result.put("message", "未找到基金数据");
        }
        
        return result;
    }
    
    /**
     * 获取基金历史净值（带涨跌幅）
     * @param fundCode 基金代码
     * @param days 请求天数（30/90/180/250）
     */
    public List<Map<String, Object>> getFundHistory(String fundCode, int days) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            // 东方财富历史净值接口
            String url = "https://fund.eastmoney.com/pingzhongdata/" + fundCode + ".js";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Accept-Charset", "GB2312,GBK,UTF-8");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
            String responseBody = new String(response.getBody(), StandardCharsets.UTF_8);
            
            if (responseBody != null) {
                // 提取净值数据
                Pattern trendPattern = Pattern.compile("var\\s+Data_netWorthTrend\\s*=\\s*(\\[.+?\\]);");
                Matcher trendMatcher = trendPattern.matcher(responseBody);
                
                if (trendMatcher.find()) {
                    String trendData = trendMatcher.group(1);
                    try {
                        JsonNode array = objectMapper.readTree(trendData);
                        
                        if (array.isArray() && array.size() >= 2) {
                            // 收集所有数据（从旧到新）
                            List<Map<String, Object>> allData = new ArrayList<>();
                            
                            // 从数组末尾取最新数据
                            int startIdx = Math.max(0, array.size() - days);
                            for (int i = startIdx; i < array.size(); i++) {
                                JsonNode item = array.get(i);
                                Map<String, Object> dayData = new HashMap<>();
                                
                                if (item.has("x") && item.has("y")) {
                                    long timestamp = item.get("x").asLong();
                                    SimpleDateFormat sdfFull = new SimpleDateFormat("yyyy-MM-dd");
                                    SimpleDateFormat sdfShort = new SimpleDateFormat("MM-dd");
                                    String dateFull = sdfFull.format(new Date(timestamp));
                                    String dateShort = sdfShort.format(new Date(timestamp));
                                    
                                    dayData.put("date", dateShort);
                                    dayData.put("dateFull", dateFull);
                                    dayData.put("dwjz", item.get("y").asDouble());
                                    
                                    // 当日涨跌幅由后面统一计算
                                    // 注意：不再从API读取changeRate，避免冲突
                                    
                                    allData.add(dayData);
                                }
                            }
                            
                            // 计算累计涨跌幅和当日涨跌幅（以最早一天为基准）
                            if (allData.size() >= 2) {
                                double baseNav = (double) allData.get(0).get("dwjz");
                                double prevNav = baseNav;
                                
                                for (int i = 0; i < allData.size(); i++) {
                                    Map<String, Object> dayData = allData.get(i);
                                    double nav = (double) dayData.get("dwjz");
                                    
                                    // 累计涨跌幅（以最早一天为基准）
                                    double accumulatedChangeRate = 0;
                                    if (baseNav > 0) {
                                        accumulatedChangeRate = (nav - baseNav) / baseNav * 100;
                                    }
                                    dayData.put("accumulatedChangeRate", Math.round(accumulatedChangeRate * 100) / 100.0);
                                    
                                    // 当日涨跌幅（以上一日为基准）
                                    double dailyChangeRate = 0;
                                    if (i > 0 && prevNav > 0) {
                                        dailyChangeRate = (nav - prevNav) / prevNav * 100;
                                    }
                                    dayData.put("dailyChangeRate", Math.round(dailyChangeRate * 100) / 100.0);
                                    
                                    prevNav = nav;
                                }
                            }
                            
                            // 反转列表，按日期升序排列（最早的在前）
                            Collections.reverse(allData);
                            
                            result.addAll(allData);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return result;
    }
}
