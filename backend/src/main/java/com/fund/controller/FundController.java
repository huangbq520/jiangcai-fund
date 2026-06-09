package com.fund.controller;

import com.fund.entity.Fund;
import com.fund.entity.FundGroup;
import com.fund.entity.User;
import com.fund.entity.UserFund;
import com.fund.mapper.FundGroupMapper;
import com.fund.mapper.FundMapper;
import com.fund.mapper.UserMapper;
import com.fund.mapper.UserFundMapper;
import com.fund.service.FundDataService;
import com.fund.service.FundGroupService;
import com.fund.service.FundHoldingService;
import com.fund.service.FundSearchService;
import com.fund.service.DailyProfitService;
import com.fund.vo.ApiResponse;
import com.fund.vo.FundData;
import com.fund.vo.FundGroupVO;
import com.fund.vo.FundHoldingVO;
import com.fund.vo.FundSearchResult;
import com.fund.vo.FundWatchlistVO;
import com.fund.vo.PerformanceData;
import com.fund.vo.PortfolioSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fund")
@CrossOrigin(origins = "*")
public class FundController {

    private static final Logger logger = LoggerFactory.getLogger(FundController.class);

    @Resource
    private FundDataService fundDataService;

    @Resource
    private FundSearchService fundSearchService;

    @Resource
    private FundMapper fundMapper;

    @Resource
    private FundGroupMapper fundGroupMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserFundMapper userFundMapper;

    @Resource
    private FundHoldingService fundHoldingService;

    @Resource
    private FundGroupService fundGroupService;

    @Resource
    private DailyProfitService dailyProfitService;

    private Long getUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            logger.warn("未从JWT找到用户ID，用户未登录！");
            return null;
        }
        return userId;
    }

    @GetMapping("/nav-at")
    public ApiResponse<Map<String, Object>> getNavAt(
            @RequestParam("code") String code,
            @RequestParam("date") String date) {
        logger.info("API: 查询历史净值, code={}, date={}", code, date);
        BigDecimal nav = fundDataService.getNavByDate(code, date);
        Map<String, Object> result = new HashMap<>();
        result.put("date", date);
        result.put("nav", nav);
        return ApiResponse.success(result);
    }

    @GetMapping("/data")
    public ApiResponse<FundData> getFundData(@RequestParam("code") String code) {
        logger.info("API: 获取基金数据, code={}", code);

        if (code == null || code.trim().isEmpty()) {
            return ApiResponse.error("基金代码不能为空");
        }

        FundData fundData = fundDataService.getFundData(code.trim());

        if (fundData.getFundCode() != null) {
            return ApiResponse.success(fundData);
        } else {
            return ApiResponse.error("基金数据加载失败");
        }
    }

    @GetMapping("/performance")
    public ApiResponse<PerformanceData> getPerformanceData(
            @RequestParam("code") String code,
            @RequestParam(value = "period", defaultValue = "6month") String period) {
        logger.info("API: 获取基金业绩走势, code={}, period={}", code, period);

        if (code == null || code.trim().isEmpty()) {
            return ApiResponse.error("基金代码不能为空");
        }

        if (!isValidPeriod(period)) {
            return ApiResponse.error("无效的周期参数，支持: 1month, 3month, 6month, 1year, 3year, all");
        }

        PerformanceData performanceData = fundDataService.getPerformanceData(code.trim(), period);

        if (performanceData.getFundCode() != null) {
            return ApiResponse.success(performanceData);
        } else {
            return ApiResponse.error("业绩数据加载失败");
        }
    }

    private boolean isValidPeriod(String period) {
        return "1month".equals(period) || "3month".equals(period) ||
               "6month".equals(period) || "1year".equals(period) ||
               "3year".equals(period) || "all".equals(period);
    }

    @GetMapping("/search")
    public ApiResponse<List<FundSearchResult>> searchFunds(@RequestParam("keyword") String keyword) {
        logger.info("API: 搜索基金, keyword={}", keyword);

        if (keyword == null || keyword.trim().isEmpty()) {
            return ApiResponse.error("搜索关键词不能为空");
        }

        List<FundSearchResult> searchResults = fundSearchService.searchFunds(keyword.trim());

        return ApiResponse.success(searchResults);
    }

    @GetMapping("/list")
    public ApiResponse<List<Fund>> listFunds(HttpServletRequest request,
                                              @RequestParam(value = "groupType", required = false) String groupType) {
        Long userId = getUserId(request);
        logger.info("API: 获取基金列表, userId={}, groupType={}", userId, groupType);
        try {
            List<Fund> funds;
            if (groupType != null && !groupType.isEmpty()) {
                funds = fundMapper.selectByGroup(userId, groupType);
            } else {
                funds = fundMapper.selectAll(userId);
            }
            return ApiResponse.success(funds);
        } catch (Exception e) {
            logger.error("获取基金列表失败: {}", e.getMessage());
            return ApiResponse.error("获取基金列表失败");
        }
    }

    @GetMapping("/holding/list")
    public ApiResponse<List<FundHoldingVO>> listHoldings(HttpServletRequest request) {
        Long userId = getUserId(request);
        logger.info("API: 获取持仓列表, userId={}", userId);
        try {
            List<FundHoldingVO> holdings = fundHoldingService.getHoldingList(userId);
            return ApiResponse.success(holdings);
        } catch (Exception e) {
            logger.error("获取持仓列表失败: {}", e.getMessage());
            return ApiResponse.error("获取持仓列表失败");
        }
    }

    @GetMapping("/watchlist/list")
    public ApiResponse<List<FundWatchlistVO>> listWatchlist(HttpServletRequest request) {
        Long userId = getUserId(request);
        logger.info("API: 获取自选列表, userId={}", userId);
        try {
            List<FundWatchlistVO> list = fundHoldingService.getWatchlistList(userId);
            return ApiResponse.success(list);
        } catch (Exception e) {
            logger.error("获取自选列表失败: {}", e.getMessage());
            return ApiResponse.error("获取自选列表失败");
        }
    }

    @GetMapping("/portfolio/summary")
    public ApiResponse<PortfolioSummary> getPortfolioSummary(HttpServletRequest request) {
        Long userId = getUserId(request);
        logger.info("API: 获取组合汇总, userId={}", userId);
        try {
            PortfolioSummary summary = fundHoldingService.getPortfolioSummary(userId);
            return ApiResponse.success(summary);
        } catch (Exception e) {
            logger.error("获取组合汇总失败: {}", e.getMessage());
            return ApiResponse.error("获取组合汇总失败");
        }
    }

    @PostMapping("/add")
    public ApiResponse<Map<String, Object>> addFund(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        String fundCode = request.get("fundCode");
        String fundName = request.get("fundName");

        // 支持两种方式：groupId（新）或 groupType（旧）
        Long groupId = null;
        String groupTypeStr = null;

        if (request.containsKey("groupId")) {
            groupId = Long.parseLong(request.get("groupId"));
        } else {
            // 旧版兼容：根据 groupType 查找对应的分组
            groupTypeStr = request.getOrDefault("groupType", "HOLDING");
            FundGroup group = fundGroupMapper.findByUserIdAndGroupType(userId, groupTypeStr);
            if (group == null) {
                fundGroupService.initDefaultGroups(userId);
                group = fundGroupMapper.findByUserIdAndGroupType(userId, groupTypeStr);
            }
            if (group != null) {
                groupId = group.getId();
            }
        }

        if (groupId == null) {
            return ApiResponse.error("分组不存在，请先创建分组");
        }

        // 验证分组存在且属于该用户
        FundGroup group = fundGroupMapper.findById(groupId);
        if (group == null || !group.getUserId().equals(userId)) {
            return ApiResponse.error("分组不存在");
        }

        logger.info("API: 添加基金, fundCode={}, fundName={}, groupId={}, groupName={}, userId={}",
                fundCode, fundName, groupId, group.getName(), userId);

        if (fundCode == null || fundCode.trim().isEmpty()) {
            return ApiResponse.error("基金代码不能为空");
        }

        try {
            Fund existingFund = fundMapper.selectByCodeAndGroupId(fundCode, userId, groupId);
            if (existingFund != null) {
                return ApiResponse.error("该基金已存在于此分组");
            }

            Fund fund = new Fund();
            fund.setUserId(userId);
            fund.setFundCode(fundCode);
            fund.setFundName(fundName);
            fund.setGroupId(groupId);
            fundMapper.insert(fund);

            // 只有HOLDING类型分组才创建 user_fund 记录
            if ("HOLDING".equals(group.getGroupType())) {
                UserFund userFund = new UserFund();
                userFund.setUserId(userId);
                userFund.setFundCode(fundCode);
                userFund.setFundName(fundName);
                userFund.setHoldShare(BigDecimal.ZERO);
                userFund.setHoldAmount(BigDecimal.ZERO);
                userFund.setCostPrice(BigDecimal.ZERO);
                userFundMapper.insert(userFund);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("fundCode", fundCode);
            result.put("fundName", fundName);
            result.put("groupId", groupId);
            result.put("groupName", group.getName());
            return ApiResponse.success(result);
        } catch (Exception e) {
            logger.error("添加基金失败: fundCode={}, error={}", fundCode, e.getMessage());
            return ApiResponse.error("添加基金失败: " + e.getMessage());
        }
    }

    @PostMapping("/holding/update")
    public ApiResponse<FundHoldingVO> updateHolding(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        String fundCode = (String) request.get("fundCode");
        String mode = request.containsKey("mode") ? (String) request.get("mode") : "SHARES";
        logger.info("API: 更新持仓, fundCode={}, userId={}, mode={}", fundCode, userId, mode);

        if (fundCode == null || fundCode.trim().isEmpty()) {
            return ApiResponse.error("基金代码不能为空");
        }

        try {
            UserFund userFund = userFundMapper.findByUserIdAndFundCode(userId, fundCode);
            if (userFund == null) {
                return ApiResponse.error("持仓记录不存在");
            }

            if ("AMOUNT".equals(mode)) {
                fundHoldingService.applyModeTwo(userFund, request);
            } else {
                if (request.containsKey("holdShare")) {
                    userFund.setHoldShare(new BigDecimal(request.get("holdShare").toString()));
                }
                if (request.containsKey("costPrice")) {
                    userFund.setCostPrice(new BigDecimal(request.get("costPrice").toString()));
                }
                BigDecimal holdShare = userFund.getHoldShare();
                BigDecimal costPrice = userFund.getCostPrice();
                if (holdShare != null && costPrice != null
                        && holdShare.compareTo(BigDecimal.ZERO) > 0
                        && costPrice.compareTo(BigDecimal.ZERO) > 0) {
                    userFund.setHoldAmount(holdShare.multiply(costPrice));
                }
            }

            if (request.containsKey("buyDate")) {
                String buyDateStr = (String) request.get("buyDate");
                if (buyDateStr != null && !buyDateStr.isEmpty()) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                    userFund.setBuyDate(sdf.parse(buyDateStr));
                }
            }

            userFundMapper.update(userFund);
            FundHoldingVO vo = fundHoldingService.getSingleHolding(userId, fundCode);
            return ApiResponse.success(vo);
        } catch (Exception e) {
            logger.error("更新持仓失败: fundCode={}, error={}", fundCode, e.getMessage());
            return ApiResponse.error("更新持仓失败: " + e.getMessage());
        }
    }

    @PostMapping("/holding/adjust")
    public ApiResponse<FundHoldingVO> adjustHolding(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        String fundCode = (String) request.get("fundCode");
        String type = (String) request.get("type");
        String adjustDateStr = (String) request.get("adjustDate");
        Boolean before3pm = request.containsKey("before3pm") && Boolean.TRUE.equals(request.get("before3pm"));
        logger.info("API: 调整持仓, fundCode={}, type={}, date={}, before3pm={}, userId={}",
                fundCode, type, adjustDateStr, before3pm, userId);

        if (fundCode == null || fundCode.trim().isEmpty()) {
            return ApiResponse.error("基金代码不能为空");
        }

        try {
            UserFund userFund = userFundMapper.findByUserIdAndFundCode(userId, fundCode);
            if (userFund == null) {
                return ApiResponse.error("持仓记录不存在");
            }

            BigDecimal adjustShare = new BigDecimal(request.get("adjustShare").toString());
            BigDecimal effectiveNAV = null;

            if (adjustDateStr != null && !adjustDateStr.isEmpty()) {
                String navDate = adjustDateStr;
                if (!before3pm) {
                    java.time.LocalDate d = java.time.LocalDate.parse(adjustDateStr);
                    navDate = d.plusDays(1).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
                }
                effectiveNAV = fundDataService.getNavByDate(fundCode, navDate);
                logger.info("历史净值查询: fundCode={}, date={}, navDate={}, nav={}", fundCode, adjustDateStr, navDate, effectiveNAV);
            }

            if ("BUY".equals(type)) {
                if (effectiveNAV == null) {
                    effectiveNAV = new BigDecimal(
                        fundDataService.getFundData(fundCode).getEstimatedNetValue() != null
                            ? fundDataService.getFundData(fundCode).getEstimatedNetValue() : "1");
                }
                fundHoldingService.applyBuy(userFund, adjustShare, effectiveNAV);
            } else if ("SELL".equals(type)) {
                fundHoldingService.applySell(userFund, adjustShare);
            } else {
                return ApiResponse.error("类型错误，只支持 BUY 或 SELL");
            }

            userFundMapper.update(userFund);
            FundHoldingVO vo = fundHoldingService.getSingleHolding(userId, fundCode);
            return ApiResponse.success(vo);
        } catch (Exception e) {
            logger.error("调整持仓失败: fundCode={}, error={}", fundCode, e.getMessage());
            return ApiResponse.error("调整持仓失败: " + e.getMessage());
        }
    }

    @PostMapping("/holding/clear")
    public ApiResponse<Void> clearHolding(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        String fundCode = request.get("fundCode");
        logger.info("API: 清仓, fundCode={}, userId={}", fundCode, userId);

        if (fundCode == null || fundCode.trim().isEmpty()) {
            return ApiResponse.error("基金代码不能为空");
        }

        try {
            UserFund userFund = userFundMapper.findByUserIdAndFundCode(userId, fundCode);
            if (userFund == null) {
                return ApiResponse.error("持仓记录不存在");
            }

            userFund.setHoldShare(BigDecimal.ZERO);
            userFund.setCostPrice(BigDecimal.ZERO);
            userFund.setHoldAmount(BigDecimal.ZERO);
            userFundMapper.update(userFund);
            return ApiResponse.success(null);
        } catch (Exception e) {
            logger.error("清仓失败: fundCode={}, error={}", fundCode, e.getMessage());
            return ApiResponse.error("清仓失败");
        }
    }

    @PostMapping("/delete")
    public ApiResponse<Void> deleteFund(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        String fundCode = request.get("fundCode");

        if (fundCode == null || fundCode.trim().isEmpty()) {
            return ApiResponse.error("基金代码不能为空");
        }

        try {
            if (request.containsKey("groupId")) {
                Long groupId = Long.parseLong(request.get("groupId"));
                FundGroup group = fundGroupMapper.findById(groupId);
                if (group != null && "HOLDING".equals(group.getGroupType())) {
                    userFundMapper.deleteByUserIdAndFundCode(userId, fundCode);
                }
                fundMapper.deleteByCodeAndGroupId(fundCode, userId, groupId);
                logger.info("API: 删除基金(by groupId), fundCode={}, groupId={}, userId={}", fundCode, groupId, userId);
            } else {
                // 旧版兼容
                String groupType = request.getOrDefault("groupType", "HOLDING");
                userFundMapper.deleteByUserIdAndFundCode(userId, fundCode);
                fundMapper.deleteByCode(fundCode, userId, groupType);
                logger.info("API: 删除基金(旧版), fundCode={}, groupType={}, userId={}", fundCode, groupType, userId);
            }
            return ApiResponse.success(null);
        } catch (Exception e) {
            logger.error("删除基金失败: fundCode={}, error={}", fundCode, e.getMessage());
            return ApiResponse.error("删除基金失败");
        }
    }

    @PostMapping("/delete/batch")
    public ApiResponse<Void> deleteFundsBatch(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        List<String> fundCodes = (List<String>) request.get("fundCodes");

        if (fundCodes == null || fundCodes.isEmpty()) {
            return ApiResponse.error("基金代码列表不能为空");
        }

        try {
            if (request.containsKey("groupId")) {
                Long groupId = Long.parseLong(request.get("groupId").toString());
                FundGroup group = fundGroupMapper.findById(groupId);
                boolean isHolding = group != null && "HOLDING".equals(group.getGroupType());
                for (String fundCode : fundCodes) {
                    if (isHolding) {
                        userFundMapper.deleteByUserIdAndFundCode(userId, fundCode);
                    }
                    fundMapper.deleteByCodeAndGroupId(fundCode, userId, groupId);
                }
                logger.info("API: 批量删除基金(by groupId), fundCodes={}, groupId={}, userId={}", fundCodes, groupId, userId);
            } else {
                String groupType = request.containsKey("groupType") ? (String) request.get("groupType") : "HOLDING";
                for (String fundCode : fundCodes) {
                    userFundMapper.deleteByUserIdAndFundCode(userId, fundCode);
                    fundMapper.deleteByCode(fundCode, userId, groupType);
                }
                logger.info("API: 批量删除基金(旧版), fundCodes={}, groupType={}, userId={}", fundCodes, groupType, userId);
            }
            return ApiResponse.success(null);
        } catch (Exception e) {
            logger.error("批量删除基金失败: fundCodes={}, error={}", fundCodes, e.getMessage());
            return ApiResponse.error("批量删除基金失败");
        }
    }

    // ===================== 分组管理 =====================

    /**
     * 获取用户所有分组
     */
    @GetMapping("/groups")
    public ApiResponse<List<FundGroupVO>> listGroups(HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        logger.info("API: 获取分组列表, userId={}", userId);
        try {
            // 确保有默认分组
            fundGroupService.initDefaultGroups(userId);
            List<FundGroupVO> groups = fundGroupService.getUserGroups(userId);
            return ApiResponse.success(groups);
        } catch (Exception e) {
            logger.error("获取分组列表失败: {}", e.getMessage());
            return ApiResponse.error("获取分组列表失败: " + e.getMessage());
        }
    }

    /**
     * 创建新分组
     */
    @PostMapping("/group")
    public ApiResponse<FundGroupVO> createGroup(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        String name = request.get("name");
        logger.info("API: 创建分组, name={}, userId={}", name, userId);
        try {
            FundGroupVO group = fundGroupService.createGroup(userId, name);
            return ApiResponse.success(group);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("创建分组失败: {}", e.getMessage());
            return ApiResponse.error("创建分组失败: " + e.getMessage());
        }
    }

    /**
     * 重命名分组
     */
    @PutMapping("/group/{id}")
    public ApiResponse<FundGroupVO> renameGroup(@PathVariable("id") Long groupId,
                                                 @RequestBody Map<String, String> request,
                                                 HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        String name = request.get("name");
        logger.info("API: 重命名分组, groupId={}, name={}, userId={}", groupId, name, userId);
        try {
            FundGroupVO group = fundGroupService.renameGroup(groupId, userId, name);
            return ApiResponse.success(group);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("重命名分组失败: {}", e.getMessage());
            return ApiResponse.error("重命名分组失败: " + e.getMessage());
        }
    }

    /**
     * 删除分组
     */
    @DeleteMapping("/group/{id}")
    public ApiResponse<Void> deleteGroup(@PathVariable("id") Long groupId,
                                          HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        logger.info("API: 删除分组, groupId={}, userId={}", groupId, userId);
        try {
            fundGroupService.deleteGroup(groupId, userId);
            return ApiResponse.success(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("删除分组失败: {}", e.getMessage());
            return ApiResponse.error("删除分组失败: " + e.getMessage());
        }
    }

    /**
     * 重新排序分组
     */
    @PutMapping("/groups/reorder")
    public ApiResponse<Void> reorderGroups(@RequestBody Map<String, Object> request,
                                            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        List<Long> groupIds = (List<Long>) request.get("groupIds");
        logger.info("API: 重新排序分组, groupIds={}, userId={}", groupIds, userId);
        try {
            fundGroupService.reorderGroups(userId, groupIds);
            return ApiResponse.success(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("重新排序分组失败: {}", e.getMessage());
            return ApiResponse.error("重新排序分组失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定分组下的基金列表
     */
    @GetMapping("/group/{groupId}/funds")
    public ApiResponse<List<?>> getGroupFunds(@PathVariable("groupId") Long groupId,
                                               HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        logger.info("API: 获取分组基金列表, groupId={}, userId={}", groupId, userId);
        try {
            List<?> funds = fundHoldingService.getGroupFundList(userId, groupId);
            return ApiResponse.success(funds);
        } catch (Exception e) {
            logger.error("获取分组基金列表失败: {}", e.getMessage());
            return ApiResponse.error("获取分组基金列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/daily-profit/recalculate")
    public ApiResponse<String> recalculateDailyProfit() {
        logger.info("API: 重新计算所有每日收益");
        try {
            dailyProfitService.recalculateAllDailyProfit();
            return ApiResponse.success("重新计算完成");
        } catch (Exception e) {
            logger.error("重新计算每日收益失败: {}", e.getMessage(), e);
            return ApiResponse.error("重新计算失败: " + e.getMessage());
        }
    }
}
