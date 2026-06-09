-- Fund Management System Database Schema
CREATE DATABASE IF NOT EXISTS jiangcai_fund DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE jiangcai_fund;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `email` VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
    `password` VARCHAR(255) NOT NULL COMMENT '密码',
    `nickname` VARCHAR(50) COMMENT '昵称',
    `status` TINYINT DEFAULT 1 COMMENT '状态：1正常 0禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 基金分组表（用户自定义分组）
CREATE TABLE IF NOT EXISTS `fund_group` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `name` VARCHAR(50) NOT NULL COMMENT '分组名称',
    `group_type` VARCHAR(10) NOT NULL DEFAULT 'WATCHLIST' COMMENT '分组行为类型: HOLDING-持仓型, WATCHLIST-自选型',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序顺序',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    UNIQUE KEY `uk_user_group_name` (`user_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基金分组表';

-- 基金信息表（用户个人持仓/自选）
CREATE TABLE IF NOT EXISTS `fund` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `fund_code` VARCHAR(10) NOT NULL COMMENT '基金代码',
    `fund_name` VARCHAR(50) COMMENT '基金名称',
    `group_id` BIGINT DEFAULT NULL COMMENT '分组ID，关联fund_group表',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    UNIQUE KEY `uk_user_fund_group_id` (`user_id`, `fund_code`, `group_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基金信息表';

-- ===================== 数据库迁移脚本 =====================
-- 以下为从旧版 group_type 字段迁移到新版 fund_group 表的SQL脚本
-- 执行前请备份数据库！

-- 步骤1: 创建fund_group表（如果尚未创建，已通过CREATE TABLE IF NOT EXISTS处理）

-- 步骤2: 为每个用户创建默认分组（持仓、自选）
-- INSERT INTO fund_group (user_id, name, group_type, sort_order)
-- SELECT u.id, '持仓', 'HOLDING', 1 FROM user u
-- WHERE NOT EXISTS (SELECT 1 FROM fund_group fg WHERE fg.user_id = u.id AND fg.group_type = 'HOLDING');
--
-- INSERT INTO fund_group (user_id, name, group_type, sort_order)
-- SELECT u.id, '自选', 'WATCHLIST', 2 FROM user u
-- WHERE NOT EXISTS (SELECT 1 FROM fund_group fg WHERE fg.user_id = u.id AND fg.group_type = 'WATCHLIST');

-- 步骤3: 如果fund表还有旧的group_type字段，先添加group_id字段
-- ALTER TABLE fund ADD COLUMN IF NOT EXISTS group_id BIGINT DEFAULT NULL AFTER group_type;
-- ALTER TABLE fund ADD INDEX IF NOT EXISTS idx_group_id (group_id);

-- 步骤4: 更新fund表的group_id，关联到对应的fund_group
-- UPDATE fund f
-- JOIN fund_group fg ON f.user_id = fg.user_id AND fg.group_type = f.group_type
-- SET f.group_id = fg.id
-- WHERE f.group_id IS NULL;

-- 步骤5: 删除旧的约束和字段
-- ALTER TABLE fund DROP INDEX IF EXISTS uk_user_fund_group;
-- ALTER TABLE fund DROP COLUMN IF EXISTS group_type;

-- 步骤6: 确保新的唯一约束存在
-- ALTER TABLE fund DROP INDEX IF EXISTS uk_user_fund_group_id;
-- ALTER TABLE fund ADD UNIQUE KEY IF NOT EXISTS uk_user_fund_group_id (user_id, fund_code, group_id);

-- 旧版兼容注释（已废弃）:
-- 原 group_type 字段说明: VARCHAR(10) DEFAULT 'HOLDING' COMMENT '分组类型: HOLDING-持仓, WATCHLIST-自选'
-- 原唯一约束: uk_user_fund_group (user_id, fund_code, group_type)

-- 用户基金持仓表（用于计算收益等功能）
CREATE TABLE IF NOT EXISTS `user_fund` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `fund_code` VARCHAR(10) NOT NULL COMMENT '基金代码',
    `fund_name` VARCHAR(50) COMMENT '基金名称',
    `hold_share` DECIMAL(10, 2) DEFAULT 0 COMMENT '持有份额',
    `hold_amount` DECIMAL(10, 2) DEFAULT 0 COMMENT '投入金额',
    `cost_price` DECIMAL(10, 4) DEFAULT 0 COMMENT '成本价',
    `buy_date` DATE COMMENT '买入日期',
    `today_buy_share` DECIMAL(10, 2) DEFAULT 0 COMMENT '当日买入份额',
    `today_sell_share` DECIMAL(10, 2) DEFAULT 0 COMMENT '当日卖出份额',
    `yesterday_share` DECIMAL(10, 2) DEFAULT 0 COMMENT '昨日收盘份额',
    `yesterday_net_value` DECIMAL(10, 4) DEFAULT 0 COMMENT '昨日确认净值',
    `profit_status` TINYINT DEFAULT 0 COMMENT '收益状态：0-估算 1-已确认',
    `profit_confirm_date` DATE DEFAULT NULL COMMENT '收益确认日期',
    `confirmed_net_value` DECIMAL(10, 4) DEFAULT NULL COMMENT '收盘确认的单位净值',
    `confirmed_profit` DECIMAL(10, 2) DEFAULT NULL COMMENT '已确认的当日收益',
    `last_sync_time` DATETIME DEFAULT NULL COMMENT '最后同步时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_fund_code` (`user_id`, `fund_code`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户基金持仓表';

-- 基金每日收益统计表
CREATE TABLE IF NOT EXISTS `fund_daily_profit` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `fund_code` VARCHAR(10) NOT NULL COMMENT '基金代码',
    `fund_name` VARCHAR(50) COMMENT '基金名称（冗余，方便查询）',
    `record_date` DATE NOT NULL COMMENT '统计日期（净值日期）',
    `daily_profit` DECIMAL(10, 2) DEFAULT 0 COMMENT '当日收益金额',
    `daily_return_rate` DECIMAL(10, 4) DEFAULT 0 COMMENT '当日收益率（%）',
    `net_value` DECIMAL(10, 4) DEFAULT 0 COMMENT '当日单位净值',
    `hold_share` DECIMAL(10, 2) DEFAULT 0 COMMENT '当日持有份额',
    `hold_amount` DECIMAL(10, 2) DEFAULT 0 COMMENT '当日持有市值',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_fund_date` (`user_id`, `fund_code`, `record_date`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_user_fund` (`user_id`, `fund_code`),
    INDEX `idx_record_date` (`record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基金每日收益统计表';
