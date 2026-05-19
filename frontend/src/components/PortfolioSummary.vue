<template>
  <div class="portfolio-summary">
    <div class="summary-card">
      <div class="summary-item">
        <span class="label">总资产</span>
        <span class="value">{{ formatNumber(summary.totalAsset) }}</span>
      </div>
      <div class="summary-divider"></div>
      <div class="summary-item">
        <span class="label">当日总收益</span>
        <span class="value" :class="getProfitClass(summary.todayProfit)">
          {{ formatProfit(summary.todayProfit) }} ({{ formatPercent(summary.todayProfitRate) }})
        </span>
      </div>
      <div class="summary-divider"></div>
      <div class="summary-item">
        <span class="label">持仓收益率</span>
        <span class="value" :class="getProfitClass(summary.totalProfitRate)">
          {{ formatPercent(summary.totalProfitRate) }}
        </span>
      </div>
    </div>

    <AllocationPieChart />
  </div>
</template>

<script setup>
import { useFundStore } from '../stores/fundStore'
import { storeToRefs } from 'pinia'
import { formatNumber, formatProfit, formatPercent, getProfitClass } from '../composables/useFormat'
import { useAutoRefresh, isTradingHours } from '../composables/useAutoRefresh'
import AllocationPieChart from './AllocationPieChart.vue'

const fundStore = useFundStore()
const { summary } = storeToRefs(fundStore)

useAutoRefresh(() => fundStore.silentFetchSummary(), 30000, isTradingHours)
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
  color: #333;
  transition: color 0.3s;
}

.summary-divider {
  width: 1px;
  height: 50px;
  background: linear-gradient(180deg, transparent 0%, #e0e0e0 50%, transparent 100%);
  position: relative;
  z-index: 1;
}
</style>