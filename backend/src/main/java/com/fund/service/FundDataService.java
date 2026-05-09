package com.fund.service;

import com.fund.util.HttpUtil;
import com.fund.vo.CompareIndex;
import com.fund.vo.FundData;
import com.fund.vo.FundHolding;
import com.fund.vo.FundHistoryTrend;
import com.fund.vo.PerformanceData;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FundDataService {

    private static final Logger logger = LoggerFactory.getLogger(FundDataService.class);

    private static final int TIMEOUT_SECONDS = 10;

    private static final String TIANTIAN_FUND_URL = "https://fundgz.1234567.com.cn/js/%s.js?rt=%d";
    private static final String EASTMONEY_HOLDINGS_URL = "https://fundf10.eastmoney.com/FundArchivesDatas.aspx?type=jjcc&code=%s&topline=10&year=&month=&rt=%d";
    private static final String TENCENT_STOCK_URL = "https://qt.gtimg.cn/q=%s";
    private static final String EASTMONEY_TREND_URL = "https://fund.eastmoney.com/pingzhongdata/%s.js?v=%d";

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Resource
    private HttpUtil httpUtil;

    public FundData getFundData(String fundCode) {
        FundData fundData = new FundData();
        fundData.setFundCode(fundCode);

        try {
            fetchBasicInfo(fundCode, fundData);
            fetchHoldingsAndEnrich(fundCode, fundData);
            fetchHistoryTrend(fundCode, fundData);
        } catch (Exception e) {
            logger.error("获取基金数据异常: fundCode={}, error={}", fundCode, e.getMessage());
            fundData.getErrorMessages().add("获取基金数据异常: " + e.getMessage());
        }

        return fundData;
    }

    private void fetchBasicInfo(String fundCode, FundData fundData) {
        try {
            String url = String.format(TIANTIAN_FUND_URL, fundCode, System.currentTimeMillis());
            String response = httpUtil.get(url, TIMEOUT_SECONDS);

            if (response == null || response.isEmpty()) {
                fundData.setBasicInfoSuccess(false);
                fundData.getErrorMessages().add("天天基金接口返回为空");
                logger.warn("天天基金接口返回为空: fundCode={}", fundCode);
                return;
            }

            String jsonStr = parseJsonP(response);
            if (jsonStr == null) {
                fundData.setBasicInfoSuccess(false);
                fundData.getErrorMessages().add("天天基金JSONP解析失败");
                logger.warn("天天基金JSONP解析失败: fundCode={}", fundCode);
                return;
            }

            Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"?([^\",}]*)\"?");
            Matcher matcher = pattern.matcher(jsonStr);

            while (matcher.find()) {
                String key = matcher.group(1);
                String value = matcher.group(2);

                switch (key) {
                    case "fundcode":
                        fundData.setFundCode(value);
                        break;
                    case "name":
                        fundData.setFundName(value);
                        break;
                    case "dwjz":
                        fundData.setUnitNetValue(value);
                        break;
                    case "gsz":
                        fundData.setEstimatedNetValue(value);
                        break;
                    case "gztime":
                        fundData.setValuationTime(value);
                        break;
                    case "gszzl":
                        try {
                            fundData.setEstimatedChange(value.isEmpty() ? null : Double.parseDouble(value));
                        } catch (NumberFormatException e) {
                            fundData.setEstimatedChange(null);
                        }
                        break;
                }
            }

            fundData.setBasicInfoSuccess(true);
            logger.info("天天基金基本信息获取成功: fundCode={}, fundName={}", fundCode, fundData.getFundName());

            updateTradingDayStatus(fundData);

        } catch (Exception e) {
            fundData.setBasicInfoSuccess(false);
            fundData.getErrorMessages().add("获取基本信息失败: " + e.getMessage());
            logger.error("获取基金基本信息失败: fundCode={}, error={}", fundCode, e.getMessage());
        }
    }

    private void updateTradingDayStatus(FundData fundData) {
        String gztime = fundData.getValuationTime();
        String gsz = fundData.getEstimatedNetValue();

        fundData.setTradingDay(isTradingDay());

        if (gsz != null && !gsz.isEmpty() && !gsz.equals("null")) {
            fundData.setPriced(true);
        } else {
            fundData.setPriced(false);
        }

        logger.info("交易日状态更新: fundCode={}, tradingDay={}, priced={}, gztime={}",
                fundData.getFundCode(), fundData.isTradingDay(), fundData.isPriced(), gztime);
    }

    private boolean isTradingDay() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);
        if (dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY) {
            return false;
        }
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        int minute = cal.get(java.util.Calendar.MINUTE);
        int currentTime = hour * 60 + minute;
        int tradingStart = 9 * 60 + 30;
        int tradingEnd = 15 * 60;
        return currentTime >= tradingStart && currentTime <= tradingEnd;
    }

    private void fetchHoldingsAndEnrich(String fundCode, FundData fundData) {
        try {
            String url = String.format(EASTMONEY_HOLDINGS_URL, fundCode, System.currentTimeMillis());
            String response = httpUtil.get(url, TIMEOUT_SECONDS);

            if (response == null || response.isEmpty()) {
                fundData.setHoldingsSuccess(false);
                fundData.getErrorMessages().add("东方财富持仓接口返回为空");
                logger.warn("东方财富持仓接口返回为空: fundCode={}", fundCode);
                return;
            }

            String htmlContent = extractApidataContent(response);
            if (htmlContent == null || htmlContent.isEmpty() ||
                htmlContent.contains("暂无") ||
                htmlContent.length() < 100) {
                fundData.setHoldingsSuccess(true);
                logger.info("东方财富持仓数据为空（无持仓或未公布）: fundCode={}", fundCode);
                return;
            }

            List<FundHolding> holdings = parseHoldingsTable(htmlContent);
            fundData.setHoldings(holdings);

            if (!holdings.isEmpty()) {
                enrichStockChange(holdings);
            }

            fundData.setHoldingsSuccess(true);
            logger.info("东方财富持仓数据获取成功: fundCode={}, count={}", fundCode, holdings.size());

        } catch (Exception e) {
            fundData.setHoldingsSuccess(false);
            fundData.getErrorMessages().add("获取持仓数据失败: " + e.getMessage());
            logger.error("获取持仓数据失败: fundCode={}, error={}", fundCode, e.getMessage());
        }
    }

    private List<FundHolding> parseHoldingsTable(String htmlContent) {
        List<FundHolding> holdings = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(htmlContent);

            Elements tables = doc.select("table");
            for (Element table : tables) {
                String classAttr = table.attr("class");
                if (classAttr.contains("tzxq") || classAttr.contains("comm")) {
                    Elements rows = table.select("tbody tr");
                    if (rows.isEmpty()) {
                        rows = table.select("tr");
                    }
                    for (Element row : rows) {
                        if (holdings.size() >= 10) break;

                        Elements tds = row.select("td");
                        if (tds.size() >= 7) {
                            FundHolding holding = new FundHolding();

                            String stockCodeHtml = tds.get(1).html();
                            String stockCode = extractStockCode(stockCodeHtml);
                            holding.setStockCode(stockCode);

                            String stockName = tds.get(2).text();
                            holding.setStockName(stockName);

                            String weight = tds.get(6).text();
                            holding.setWeight(weight);

                            holdings.add(holding);
                        }
                    }
                    break;
                }
            }

        } catch (Exception e) {
            logger.error("解析持仓表格失败: {}", e.getMessage());
        }

        return holdings;
    }

    private void enrichStockChange(List<FundHolding> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return;
        }

        try {
            List<String> stockCodes = new ArrayList<>();
            for (FundHolding holding : holdings) {
                String code = holding.getStockCode();
                if (code != null && code.length() == 6) {
                    char first = code.charAt(0);
                    String prefix;
                    if (first == '6' || first == '9') {
                        prefix = "sh";
                    } else if (first == '0' || first == '3') {
                        prefix = "sz";
                    } else if (first == '4' || first == '8') {
                        prefix = "bj";
                    } else {
                        prefix = "sz";
                    }
                    stockCodes.add(prefix + code);
                }
            }

            if (stockCodes.isEmpty()) {
                return;
            }

            StringBuilder urlBuilder = new StringBuilder();
            for (int i = 0; i < stockCodes.size(); i++) {
                if (i > 0) urlBuilder.append(",");
                urlBuilder.append(stockCodes.get(i));
            }

            String url = String.format(TENCENT_STOCK_URL, urlBuilder.toString());
            String response = httpUtil.get(url, TIMEOUT_SECONDS);

            if (response == null || response.isEmpty()) {
                logger.warn("腾讯股票行情接口返回为空");
                return;
            }

            Pattern stockPattern = Pattern.compile("v_(sh|sz|bj|hk)(\\d{5,6})=\"([^\"]+)\"");
            Matcher stockMatcher = stockPattern.matcher(response);

            while (stockMatcher.find()) {
                String code = stockMatcher.group(2);
                String data = stockMatcher.group(3);
                String[] fields = data.split("~");

                for (FundHolding holding : holdings) {
                    if (code.equals(holding.getStockCode())) {
                        try {
                            if (fields.length > 5 && fields[5] != null && !fields[5].isEmpty()) {
                                holding.setChange(Double.parseDouble(fields[5]));
                            }
                        } catch (Exception e) {
                            logger.debug("解析股票涨跌幅失败: code={}", code);
                        }
                        break;
                    }
                }
            }

            logger.info("股票行情批量获取完成: count={}", stockCodes.size());

        } catch (Exception e) {
            logger.error("批量获取股票行情失败: error={}", e.getMessage());
        }
    }

    private void fetchHistoryTrend(String fundCode, FundData fundData) {
        try {
            String url = String.format(EASTMONEY_TREND_URL, fundCode, System.currentTimeMillis());
            String response = httpUtil.get(url, TIMEOUT_SECONDS);

            if (response == null || response.isEmpty()) {
                fundData.setHistorySuccess(false);
                fundData.getErrorMessages().add("东方财富走势接口返回为空");
                logger.warn("东方财富走势接口返回为空: fundCode={}", fundCode);
                return;
            }

            parseNetWorthTrend(response, fundData);

            parseCompareIndices(response, fundData);

            fundData.setHistorySuccess(true);
            logger.info("东方财富走势数据获取成功: fundCode={}, historyCount={}, compareCount={}",
                    fundCode,
                    fundData.getHistoryTrend() != null ? fundData.getHistoryTrend().size() : 0,
                    fundData.getCompareIndices() != null ? fundData.getCompareIndices().size() : 0);

        } catch (Exception e) {
            fundData.setHistorySuccess(false);
            fundData.getErrorMessages().add("获取历史走势失败: " + e.getMessage());
            logger.error("获取历史走势失败: fundCode={}, error={}", fundCode, e.getMessage());
        }
    }

    private void parseNetWorthTrend(String response, FundData fundData) {
        String varName = "Data_netWorthTrend";
        String startMarker = varName + " = ";
        int startIndex = response.indexOf(startMarker);

        if (startIndex == -1) {
            startIndex = response.indexOf("Data_netWorthTrend=");
            if (startIndex != -1) {
                startIndex += "Data_netWorthTrend=".length();
            } else {
                logger.warn("parseNetWorthTrend: 未找到Data_netWorthTrend数据");
                return;
            }
        } else {
            startIndex += startMarker.length();
        }

        int endIndex = response.indexOf(";", startIndex);
        if (endIndex == -1) {
            endIndex = response.length();
        }

        String arrayStr = response.substring(startIndex, endIndex).trim();

        List<FundHistoryTrend> trends = new ArrayList<>();

        Pattern itemPattern = Pattern.compile("\\{([^}]+)\\}");
        Matcher itemMatcher = itemPattern.matcher(arrayStr);

        List<String> items = new ArrayList<>();
        while (itemMatcher.find()) {
            items.add(itemMatcher.group(1));
        }

        for (String item : items) {
            Long x = null;
            Double y = null;
            Double equityReturn = null;

            Pattern xPattern = Pattern.compile("\"x\"\\s*:\\s*(\\d+)");
            Matcher xMatcher = xPattern.matcher(item);
            if (xMatcher.find()) {
                x = Long.parseLong(xMatcher.group(1));
            }

            Pattern yPattern = Pattern.compile("\"y\"\\s*:\\s*([\\d.]+)");
            Matcher yMatcher = yPattern.matcher(item);
            if (yMatcher.find()) {
                y = Double.parseDouble(yMatcher.group(1));
            }

            Pattern erPattern = Pattern.compile("\"equityReturn\"\\s*:\\s*([\\d.-]+)");
            Matcher erMatcher = erPattern.matcher(item);
            if (erMatcher.find()) {
                equityReturn = Double.parseDouble(erMatcher.group(1));
            }

            if (x != null && y != null) {
                FundHistoryTrend trend = new FundHistoryTrend();
                trend.setDate(String.valueOf(x));
                trend.setNetValue(y);
                trend.setDailyChange(equityReturn);
                trends.add(trend);
            }
        }

        fundData.setHistoryTrend(trends);

        if (trends.size() >= 2) {
            FundHistoryTrend last = trends.get(trends.size() - 1);
            FundHistoryTrend secondLast = trends.get(trends.size() - 2);
            fundData.setYesterdayNetValue(String.valueOf(secondLast.getNetValue()));

            if (last.getNetValue() != null && secondLast.getNetValue() != null
                && secondLast.getNetValue() != 0) {
                double change = (last.getNetValue() - secondLast.getNetValue()) / secondLast.getNetValue() * 100;
                fundData.setYesterdayChange(change);
            } else if (secondLast.getDailyChange() != null) {
                fundData.setYesterdayChange(secondLast.getDailyChange());
            }
        }
    }

    private void parseCompareIndices(String response, FundData fundData) {
        String varName = "Data_grandTotal";
        String startMarker = varName + " = ";
        int startIndex = response.indexOf(startMarker);

        logger.info("parseCompareIndices: searching for Data_grandTotal, found at index: {}", startIndex);

        if (startIndex == -1) {
            startIndex = response.indexOf("Data_grandTotal=");
            if (startIndex != -1) {
                startIndex += "Data_grandTotal=".length();
                logger.info("parseCompareIndices: found with Data_grandTotal=, startIndex: {}", startIndex);
            } else {
                logger.info("parseCompareIndices: Data_grandTotal not found in response");
                int idx = response.indexOf("Data_");
                logger.info("parseCompareIndices: first Data_ found at: {}", idx);
                if (idx > 0) {
                    logger.info("parseCompareIndices: surrounding content: {}", response.substring(idx, Math.min(idx + 100, response.length())));
                }
                return;
            }
        } else {
            startIndex += startMarker.length();
        }

        int endIndex = response.indexOf(";", startIndex);
        if (endIndex == -1) {
            endIndex = response.length();
        }

        String arrayStr = response.substring(startIndex, endIndex).trim();
        logger.info("parseCompareIndices: arrayStr length={}, first 300 chars: {}", arrayStr.length(), arrayStr.substring(0, Math.min(300, arrayStr.length())));

        try {
            com.alibaba.fastjson2.JSONArray jsonArray = com.alibaba.fastjson2.JSON.parseArray(arrayStr);
            if (jsonArray == null || jsonArray.isEmpty()) {
                logger.info("parseCompareIndices: JSON array is empty");
                return;
            }

            com.alibaba.fastjson2.JSONArray szzsData = null;
            com.alibaba.fastjson2.JSONArray tysmData = null;

            for (int i = 0; i < jsonArray.size(); i++) {
                com.alibaba.fastjson2.JSONObject obj = jsonArray.getJSONObject(i);
                String name = obj.getString("name");
                if (name != null) {
                    if (name.contains("沪深300") || name.contains("hs300")) {
                        szzsData = obj.getJSONArray("data");
                        logger.info("parseCompareIndices: found szzs data, size={}", szzsData != null ? szzsData.size() : 0);
                    } else if (name.contains("同类平均") || name.contains("tysm")) {
                        tysmData = obj.getJSONArray("data");
                        logger.info("parseCompareIndices: found tysm data, size={}", tysmData != null ? tysmData.size() : 0);
                    }
                }
            }

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            java.util.Map<String, CompareIndex> compareMap = new java.util.LinkedHashMap<>();

            if (szzsData != null) {
                for (int i = 0; i < szzsData.size(); i++) {
                    com.alibaba.fastjson2.JSONArray point = szzsData.getJSONArray(i);
                    if (point != null && point.size() >= 2) {
                        Long timestamp = point.getLong(0);
                        Double value = point.getDouble(1);
                        String dateStr = sdf.format(new java.util.Date(timestamp));
                        CompareIndex idx = compareMap.get(dateStr);
                        if (idx == null) {
                            idx = new CompareIndex();
                            idx.setDate(dateStr);
                            compareMap.put(dateStr, idx);
                        }
                        idx.setSzzs(value);
                    }
                }
            }

            if (tysmData != null) {
                for (int i = 0; i < tysmData.size(); i++) {
                    com.alibaba.fastjson2.JSONArray point = tysmData.getJSONArray(i);
                    if (point != null && point.size() >= 2) {
                        Long timestamp = point.getLong(0);
                        Double value = point.getDouble(1);
                        String dateStr = sdf.format(new java.util.Date(timestamp));
                        CompareIndex idx = compareMap.get(dateStr);
                        if (idx == null) {
                            idx = new CompareIndex();
                            idx.setDate(dateStr);
                            compareMap.put(dateStr, idx);
                        }
                        idx.setTysm(value);
                    }
                }
            }

            List<CompareIndex> compareIndices = new ArrayList<>(compareMap.values());
            fundData.setCompareIndices(compareIndices);
            logger.info("parseCompareIndices: 解析对比指数数据完成, count={}", compareIndices.size());

        } catch (Exception e) {
            logger.error("parseCompareIndices: 解析失败: {}", e.getMessage(), e);
        }
    }

    public PerformanceData getPerformanceData(String fundCode, String period) {
        FundData fundData = getFundData(fundCode);

        PerformanceData performanceData = new PerformanceData();
        performanceData.setFundCode(fundData.getFundCode());
        performanceData.setFundName(fundData.getFundName());
        performanceData.setPeriod(period);

        List<FundHistoryTrend> filteredTrend = filterByPeriod(fundData.getHistoryTrend(), period);
        performanceData.setNetWorthTrend(filteredTrend);

        List<CompareIndex> filteredCompare = filterCompareIndicesByPeriod(fundData.getCompareIndices(), period);
        performanceData.setCompareIndices(normalizeCompareIndices(filteredCompare, filteredTrend));

        Double periodReturn = calculatePeriodReturn(filteredTrend);
        performanceData.setPeriodReturn(periodReturn);

        return performanceData;
    }

    private List<CompareIndex> normalizeCompareIndices(List<CompareIndex> indices, List<FundHistoryTrend> trends) {
        if (indices == null || indices.isEmpty() || trends == null || trends.isEmpty()) {
            return indices;
        }

        String firstDate = trends.get(0).getDate();
        String firstDateFormatted = formatCompareDate(firstDate);

        Double baselineSzzs = null;
        Double baselineTysm = null;

        for (CompareIndex idx : indices) {
            if (idx.getDate().equals(firstDateFormatted)) {
                baselineSzzs = idx.getSzzs();
                baselineTysm = idx.getTysm();
                break;
            }
        }

        if (baselineSzzs == null && baselineTysm == null) {
            return indices;
        }

        List<CompareIndex> normalized = new ArrayList<>(indices.size());
        for (CompareIndex idx : indices) {
            CompareIndex newIdx = new CompareIndex();
            newIdx.setDate(idx.getDate());
            if (baselineSzzs != null && idx.getSzzs() != null) {
                newIdx.setSzzs(idx.getSzzs() - baselineSzzs);
            }
            if (baselineTysm != null && idx.getTysm() != null) {
                newIdx.setTysm(idx.getTysm() - baselineTysm);
            }
            normalized.add(newIdx);
        }

        return normalized;
    }

    private String formatCompareDate(String timestamp) {
        try {
            long ts = Long.parseLong(timestamp);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            return sdf.format(new java.util.Date(ts));
        } catch (NumberFormatException e) {
            return timestamp;
        }
    }

    private List<FundHistoryTrend> filterByPeriod(List<FundHistoryTrend> trends, String period) {
        if (trends == null || trends.isEmpty() || period == null) {
            return trends;
        }

        long now = System.currentTimeMillis();
        long startTime;

        switch (period) {
            case "1month":
                startTime = now - 30L * 24 * 60 * 60 * 1000;
                break;
            case "3month":
                startTime = now - 90L * 24 * 60 * 60 * 1000;
                break;
            case "6month":
                startTime = now - 180L * 24 * 60 * 60 * 1000;
                break;
            case "1year":
                startTime = now - 365L * 24 * 60 * 60 * 1000;
                break;
            case "3year":
                startTime = now - 1095L * 24 * 60 * 60 * 1000;
                break;
            case "all":
            default:
                return trends;
        }

        return trends.stream()
                .filter(t -> {
                    try {
                        long timestamp = Long.parseLong(t.getDate());
                        return timestamp >= startTime;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                })
                .collect(java.util.stream.Collectors.toList());
    }

    private List<CompareIndex> filterCompareIndicesByPeriod(List<CompareIndex> indices, String period) {
        if (indices == null || indices.isEmpty() || period == null) {
            return indices;
        }

        long now = System.currentTimeMillis();
        long startTime;

        switch (period) {
            case "1month":
                startTime = now - 30L * 24 * 60 * 60 * 1000;
                break;
            case "3month":
                startTime = now - 90L * 24 * 60 * 60 * 1000;
                break;
            case "6month":
                startTime = now - 180L * 24 * 60 * 60 * 1000;
                break;
            case "1year":
                startTime = now - 365L * 24 * 60 * 60 * 1000;
                break;
            case "3year":
                startTime = now - 1095L * 24 * 60 * 60 * 1000;
                break;
            case "all":
            default:
                return indices;
        }

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        return indices.stream()
                .filter(idx -> {
                    try {
                        Date date = sdf.parse(idx.getDate());
                        return date.getTime() >= startTime;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(java.util.stream.Collectors.toList());
    }

    private Double calculatePeriodReturn(List<FundHistoryTrend> trends) {
        if (trends == null || trends.size() < 2) {
            return null;
        }

        FundHistoryTrend first = trends.get(0);
        FundHistoryTrend last = trends.get(trends.size() - 1);

        if (first.getNetValue() == null || last.getNetValue() == null ||
            first.getNetValue() == 0) {
            return null;
        }

        return (last.getNetValue() - first.getNetValue()) / first.getNetValue() * 100;
    }

    private String parseJsonP(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }
        Pattern pattern = Pattern.compile("jsonpgz\\((.*)\\)");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return response;
    }

    private String extractApidataContent(String response) {
        if (response == null || response.isEmpty()) {
            logger.warn("extractApidataContent: response为空");
            return null;
        }
        try {
            int apidataStart = response.indexOf("apidata={ content:");
            if (apidataStart == -1) {
                logger.warn("extractApidataContent: 未找到apidata={ content:, response前200字符={}", response.substring(0, Math.min(200, response.length())));
                return null;
            }

            int contentStart = apidataStart + "apidata={ content:".length();
            if (contentStart >= response.length()) {
                logger.warn("extractApidataContent: content开始位置超出范围");
                return null;
            }

            char firstChar = response.charAt(contentStart);

            int searchFrom = contentStart + 1;
            int contentEnd = -1;

            if (firstChar == '"' || firstChar == '\'') {
                contentEnd = findUnescapedChar(response, firstChar, searchFrom);
            }

            if (contentEnd == -1) {
                logger.warn("extractApidataContent: 未找到结束引号");
                return null;
            }

            String content = response.substring(contentStart + 1, contentEnd);

            return content;

        } catch (Exception e) {
            logger.warn("解析apidata失败: {}", e.getMessage());
        }
        return null;
    }

    private int findUnescapedChar(String str, char target, int start) {
        boolean escaped = false;
        for (int i = start; i < str.length(); i++) {
            char c = str.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == target) {
                return i;
            }
        }
        return -1;
    }

    private String extractStockCode(String html) {
        if (html == null) {
            return "";
        }
        Pattern pattern = Pattern.compile("<a[^>]*>(\\d{5,6})</a>");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        Pattern numPattern = Pattern.compile("\\d{5,6}");
        Matcher numMatcher = numPattern.matcher(html);
        if (numMatcher.find()) {
            return numMatcher.group();
        }
        return "";
    }
}
