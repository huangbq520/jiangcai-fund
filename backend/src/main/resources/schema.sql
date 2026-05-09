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

-- 基金信息表（用户个人持仓）
CREATE TABLE IF NOT EXISTS `fund` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `fund_code` VARCHAR(10) NOT NULL COMMENT '基金代码',
    `fund_name` VARCHAR(50) COMMENT '基金名称',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    UNIQUE KEY `uk_user_fund` (`user_id`, `fund_code`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基金信息表';

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
