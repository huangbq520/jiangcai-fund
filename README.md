# 江财基金估值持仓系统

基于 Spring Boot 的共同基金持仓管理系统，对接天天基金网（东方财富）行情接口，实现基金实时估值查询与投资组合管理。

## 功能特性

- 🔍 **基金搜索**：按代码或名称模糊搜索基金
- 📈 **实时估值**：获取基金当日最新净值与涨跌幅
- 📊 **历史净值图表**：展示最近 7/30/90/180 天净值走势
- 💼 **持仓管理**：买入、追加、部分卖出及清仓操作
- 📋 **投资组合概览**：总资产、当日收益、累计收益、持仓占比饼图

## 技术栈

| 层次 | 技术 |
|------|------|
| 后端框架 | Spring Boot 2.7.18 |
| 模板引擎 | Thymeleaf |
| 持久化 | Spring Data JPA / Hibernate |
| 数据库 | MySQL 8 |
| 前端图表 | ECharts |
| 外部数据 | 天天基金网（东方财富）API |
| 构建工具 | Maven |
| Java 版本 | Java 11 |

## 快速开始

### 1. 环境要求

- JDK 11+
- Maven 3.6+
- MySQL 8.x

### 2. 创建数据库

```sql
CREATE DATABASE jiangcai_fund CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

> 数据表由 Hibernate `ddl-auto: update` 自动创建，无需手动建表。

### 3. 修改数据库配置

编辑 `src/main/resources/application.yml`，将数据库连接信息修改为实际环境：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/jiangcai_fund?useUnicode=true&characterEncoding=utf8
    username: root
    password: your_password
```

### 4. 构建并运行

```bash
# 构建
mvn clean package -DskipTests

# 运行
java -jar target/fund-1.0.0.jar
```

或直接通过 Maven 运行：

```bash
mvn spring-boot:run
```

### 5. 访问页面

| 页面 | URL |
|------|-----|
| 基金查询 | http://localhost:8080/ |
| 持仓管理 | http://localhost:8080/hold |

## API 接口说明

### 基金查询

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/fund/search?keyword={关键词}` | 搜索基金 |
| GET | `/api/fund/quote?fundCode={代码}` | 获取实时估值 |
| GET | `/api/fund/history?fundCode={代码}&days={天数}` | 获取历史净值 |

### 持仓管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/hold/list` | 获取所有持仓 |
| GET | `/api/hold/overview` | 获取持仓概览（含当日收益） |
| POST | `/api/hold/add` | 买入基金 |
| POST | `/api/hold/sell` | 卖出基金 |
| POST | `/api/hold/delete` | 清空持仓 |
| POST | `/api/hold/refresh` | 刷新所有持仓最新净值 |

#### 买入示例

```bash
curl -X POST http://localhost:8080/api/hold/add \
  -H "Content-Type: application/json" \
  -d '{"fundCode":"000001","amount":1000,"price":1.5}'
```

#### 卖出示例

```bash
curl -X POST http://localhost:8080/api/hold/sell \
  -H "Content-Type: application/json" \
  -d '{"fundCode":"000001","amount":500}'
```

## 项目结构

```
src/main/
├── java/com/jiangcai/fund/
│   ├── JiangcaiFundApplication.java        # 启动类
│   ├── controller/
│   │   ├── FundController.java             # 页面路由 & 基金查询接口
│   │   └── FundHoldController.java         # 持仓管理 REST 接口
│   ├── service/
│   │   ├── FundDataService.java            # 对接东方财富行情接口
│   │   └── FundHoldService.java            # 持仓业务逻辑
│   ├── entity/
│   │   └── FundStockHolding.java           # 持仓 JPA 实体
│   ├── dto/
│   │   └── PortfolioOverview.java          # 投资组合概览 DTO
│   └── repository/
│       └── FundStockHoldingRepository.java # 数据访问层
└── resources/
    ├── application.yml                     # 应用配置
    └── templates/
        ├── index.html                      # 基金查询页面
        └── hold.html                       # 持仓管理页面
```

## 注意事项

- 行情数据来自天天基金网公开接口，仅供学习参考，请勿用于商业用途。
- 请勿将真实数据库密码提交到版本库，生产部署建议通过环境变量注入敏感配置。
