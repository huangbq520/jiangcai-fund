<template>
  <div class="portfolio-summary">
    <div class="summary-card">
      <div class="summary-item">
        <span class="label">总资产</span>
        <span class="value" :class="showData ? 'value-neutral' : 'value-hidden'">
          {{ showData ? formatNumber(summary.totalAsset) : '****' }}
        </span>
      </div>
      <div class="summary-divider"></div>
      <div class="summary-item">
        <span class="label">当日总收益</span>
        <span class="value" :class="showData ? getProfitClass(summary.todayProfit) : 'value-hidden'">
          {{ showData ? formatProfit(summary.todayProfit) + ' (' + formatPercent(summary.todayProfitRate) + ')' : '****' }}
        </span>
      </div>
      <div class="summary-divider"></div>
      <div class="summary-item">
        <span class="label">持仓收益率</span>
        <span class="value" :class="showData ? getProfitClass(summary.totalProfitRate) : 'value-hidden'">
          {{ showData ? formatPercent(summary.totalProfitRate) : '****' }}
        </span>
      </div>
      <button class="privacy-toggle" @click="showData = !showData" :title="showData ? '隐藏金额' : '显示金额'">
        <svg v-if="showData" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
          <circle cx="12" cy="12" r="3"/>
        </svg>
        <svg v-else viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
          <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
          <path d="m14.12 14.12a3 3 0 1 1-4.24-4.24"/>
          <line x1="1" y1="1" x2="23" y2="23"/>
        </svg>
      </button>
    </div>

    <!-- 资产配置图表区域（可折叠） -->
    <div class="chart-section" :class="{ collapsed: !showChart }">
      <div class="chart-section-header" @click="showChart = !showChart">
        <h3>资产配置</h3>
        <button class="collapse-btn" :title="showChart ? '收起图表' : '展开图表'">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"
               :class="{ rotated: !showChart }">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
        </button>
      </div>
      <div v-show="showChart" class="chart-body">
        <AllocationPieChart @view-detail="handleViewDetail" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useFundStore } from '../stores/fundStore'
import { storeToRefs } from 'pinia'
import { formatNumber, formatProfit, formatPercent, getProfitClass } from '../composables/useFormat'
import { useAutoRefresh, isTradingHours } from '../composables/useAutoRefresh'
import AllocationPieChart from './AllocationPieChart.vue'

const emit = defineEmits(['view-detail'])

const fundStore = useFundStore()
const { summary } = storeToRefs(fundStore)

const showData = ref(false)
const showChart = ref(false)

useAutoRefresh(() => fundStore.silentFetchSummary(), 30000, isTradingHours)

const handleViewDetail = (fundCode) => {
  emit('view-detail', fundCode)
}
</script>

<style scoped>
.portfolio-summary {
  margin-bottom: 20px;
}

.summary-card {
  background: white;
  border-radius: 16px;
  padding: 24px 32px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  display: flex;
  align-items: center;
  justify-content: space-around;
  position: relative;
  overflow: hidden;
}

.summary-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, #1677ff 0%, #1677ff 50%, #69b1ff 100%);
}

.summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  flex: 1;
  position: relative;
  z-index: 1;
}

.summary-item .label {
  font-size: 14px;
  color: #888;
  font-weight: 500;
  letter-spacing: 0.5px;
}

.summary-item .value {
  font-size: 22px;
  font-weight: 700;
  transition: color 0.3s;
}

/* 非敏感数据（总资产）默认颜色 */
.summary-item .value.value-neutral {
  color: #333;
}

/* 隐私模式隐藏占位符 */
.summary-item .value.value-hidden {
  color: #000000;
  letter-spacing: 4px;
}

/* 确保全局收益颜色在 scoped 样式中生效 */
:deep(.profit-positive) { color: #e53935 !important; }
:deep(.profit-negative) { color: #009e5f !important; }
:deep(.profit-zero)     { color: #64748b !important; }

.summary-divider {
  width: 1px;
  height: 50px;
  background: linear-gradient(180deg, transparent 0%, #e0e0e0 50%, transparent 100%);
  position: relative;
  z-index: 1;
}

/* 隐私切换按钮 */
.privacy-toggle {
  position: absolute;
  top: 12px;
  right: 16px;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.04);
  color: #888;
  cursor: pointer;
  transition: all 0.25s ease;
}

.privacy-toggle:hover {
  background: rgba(0, 0, 0, 0.08);
  color: #333;
}

/* 资产配置图表区域 */
.chart-section {
  margin-top: 16px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  overflow: hidden;
  transition: all 0.3s ease;
}

.chart-section.collapsed {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.chart-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  cursor: pointer;
  user-select: none;
  transition: background 0.2s ease;
}

.chart-section-header:hover {
  background: rgba(0, 0, 0, 0.02);
}

.chart-section-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #888;
  cursor: pointer;
  transition: all 0.25s ease;
}

.collapse-btn:hover {
  background: rgba(0, 0, 0, 0.06);
  color: #333;
}

.collapse-btn svg {
  transition: transform 0.3s ease;
}

.collapse-btn svg.rotated {
  transform: rotate(-90deg);
}

.chart-body {
  padding: 0 0 24px 0;
}
</style>