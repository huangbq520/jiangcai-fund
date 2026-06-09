package com.fund.service;

import com.fund.entity.FundGroup;
import com.fund.mapper.FundGroupMapper;
import com.fund.mapper.FundMapper;
import com.fund.vo.FundGroupVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class FundGroupService {

    private static final Logger logger = LoggerFactory.getLogger(FundGroupService.class);

    private static final String GROUP_TYPE_HOLDING = "HOLDING";
    private static final String GROUP_TYPE_WATCHLIST = "WATCHLIST";

    private static final int MAX_GROUPS_PER_USER = 20;

    @Resource
    private FundGroupMapper fundGroupMapper;

    @Resource
    private FundMapper fundMapper;

    /**
     * 获取用户的所有分组，按sort_order排序
     */
    public List<FundGroupVO> getUserGroups(Long userId) {
        List<FundGroup> groups = fundGroupMapper.findByUserId(userId);
        List<FundGroupVO> result = new ArrayList<>();
        for (FundGroup group : groups) {
            FundGroupVO vo = new FundGroupVO();
            vo.setId(group.getId());
            vo.setName(group.getName());
            vo.setGroupType(group.getGroupType());
            vo.setSortOrder(group.getSortOrder());
            vo.setCreateTime(group.getCreateTime());
            vo.setFundCount(fundGroupMapper.countFundsByGroupId(group.getId()));
            result.add(vo);
        }
        return result;
    }

    /**
     * 根据ID获取分组
     */
    public FundGroup getGroupById(Long groupId) {
        return fundGroupMapper.findById(groupId);
    }

    /**
     * 获取用户的HOLDING类型分组（持仓分组，一个用户只有一个）
     */
    public FundGroup getHoldingGroup(Long userId) {
        return fundGroupMapper.findByUserIdAndGroupType(userId, GROUP_TYPE_HOLDING);
    }

    /**
     * 获取用户默认的WATCHLIST分组（自选分组）
     */
    public FundGroup getDefaultWatchlistGroup(Long userId) {
        return fundGroupMapper.findByUserIdAndGroupType(userId, GROUP_TYPE_WATCHLIST);
    }

    /**
     * 创建新分组（WATCHLIST类型）
     */
    public FundGroupVO createGroup(Long userId, String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("分组名称不能为空");
        }
        name = name.trim();
        if (name.length() > 50) {
            throw new IllegalArgumentException("分组名称不能超过50个字符");
        }

        // 检查分组数量限制
        List<FundGroup> existingGroups = fundGroupMapper.findByUserId(userId);
        if (existingGroups.size() >= MAX_GROUPS_PER_USER) {
            throw new IllegalArgumentException("最多创建" + MAX_GROUPS_PER_USER + "个分组");
        }

        // 检查名称是否重复
        for (FundGroup g : existingGroups) {
            if (g.getName().equals(name)) {
                throw new IllegalArgumentException("分组名称已存在");
            }
        }

        int maxSort = fundGroupMapper.getMaxSortOrder(userId);

        FundGroup group = new FundGroup();
        group.setUserId(userId);
        group.setName(name);
        group.setGroupType(GROUP_TYPE_WATCHLIST);
        group.setSortOrder(maxSort + 1);
        fundGroupMapper.insert(group);

        FundGroupVO vo = new FundGroupVO();
        vo.setId(group.getId());
        vo.setName(group.getName());
        vo.setGroupType(group.getGroupType());
        vo.setSortOrder(group.getSortOrder());
        vo.setFundCount(0);
        vo.setCreateTime(group.getCreateTime());

        logger.info("用户 {} 创建了新分组: {} (id={})", userId, name, group.getId());
        return vo;
    }

    /**
     * 重命名分组（不能重命名HOLDING分组）
     */
    public FundGroupVO renameGroup(Long groupId, Long userId, String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("分组名称不能为空");
        }
        newName = newName.trim();
        if (newName.length() > 50) {
            throw new IllegalArgumentException("分组名称不能超过50个字符");
        }

        FundGroup group = fundGroupMapper.findById(groupId);
        if (group == null || !group.getUserId().equals(userId)) {
            throw new IllegalArgumentException("分组不存在");
        }
        if (GROUP_TYPE_HOLDING.equals(group.getGroupType())) {
            throw new IllegalArgumentException("默认持仓分组不能重命名");
        }

        // 检查名称是否重复
        List<FundGroup> existingGroups = fundGroupMapper.findByUserId(userId);
        for (FundGroup g : existingGroups) {
            if (g.getName().equals(newName) && !g.getId().equals(groupId)) {
                throw new IllegalArgumentException("分组名称已存在");
            }
        }

        group.setName(newName);
        fundGroupMapper.update(group);

        FundGroupVO vo = new FundGroupVO();
        vo.setId(group.getId());
        vo.setName(group.getName());
        vo.setGroupType(group.getGroupType());
        vo.setSortOrder(group.getSortOrder());
        vo.setFundCount(fundGroupMapper.countFundsByGroupId(groupId));
        vo.setCreateTime(group.getCreateTime());

        logger.info("用户 {} 重命名分组 {} -> {} (id={})", userId, group.getName(), newName, groupId);
        return vo;
    }

    /**
     * 删除分组及其中所有基金（不能删除HOLDING分组）
     */
    public void deleteGroup(Long groupId, Long userId) {
        FundGroup group = fundGroupMapper.findById(groupId);
        if (group == null || !group.getUserId().equals(userId)) {
            throw new IllegalArgumentException("分组不存在");
        }
        if (GROUP_TYPE_HOLDING.equals(group.getGroupType())) {
            throw new IllegalArgumentException("默认持仓分组不能删除");
        }

        // 删除该分组下的所有基金记录
        fundMapper.deleteByGroupId(groupId, userId);
        // 删除分组本身
        fundGroupMapper.deleteById(groupId);

        // 重新整理sort_order
        List<FundGroup> groups = fundGroupMapper.findByUserId(userId);
        for (int i = 0; i < groups.size(); i++) {
            if (!groups.get(i).getSortOrder().equals(i + 1)) {
                fundGroupMapper.updateSortOrder(groups.get(i).getId(), i + 1);
            }
        }

        logger.info("用户 {} 删除了分组: {} (id={})", userId, group.getName(), groupId);
    }

    /**
     * 重新排序分组
     */
    public void reorderGroups(Long userId, List<Long> orderedGroupIds) {
        List<FundGroup> existingGroups = fundGroupMapper.findByUserId(userId);

        // 验证所有ID都属于该用户
        for (Long id : orderedGroupIds) {
            boolean found = existingGroups.stream().anyMatch(g -> g.getId().equals(id));
            if (!found) {
                throw new IllegalArgumentException("分组不存在: " + id);
            }
        }

        for (int i = 0; i < orderedGroupIds.size(); i++) {
            fundGroupMapper.updateSortOrder(orderedGroupIds.get(i), i + 1);
        }

        logger.info("用户 {} 重新排序分组: {}", userId, orderedGroupIds);
    }

    /**
     * 为新用户初始化默认分组（持仓 + 自选）
     */
    public void initDefaultGroups(Long userId) {
        // 检查是否已有分组
        List<FundGroup> existing = fundGroupMapper.findByUserId(userId);
        if (!existing.isEmpty()) {
            return;
        }

        FundGroup holdingGroup = new FundGroup();
        holdingGroup.setUserId(userId);
        holdingGroup.setName("持仓");
        holdingGroup.setGroupType(GROUP_TYPE_HOLDING);
        holdingGroup.setSortOrder(1);
        fundGroupMapper.insert(holdingGroup);

        FundGroup watchlistGroup = new FundGroup();
        watchlistGroup.setUserId(userId);
        watchlistGroup.setName("自选");
        watchlistGroup.setGroupType(GROUP_TYPE_WATCHLIST);
        watchlistGroup.setSortOrder(2);
        fundGroupMapper.insert(watchlistGroup);

        logger.info("为用户 {} 初始化默认分组: 持仓(id={}), 自选(id={})", userId, holdingGroup.getId(), watchlistGroup.getId());
    }
}
