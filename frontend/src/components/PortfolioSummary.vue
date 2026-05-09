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
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { fundApi } from '../api'

const emit = defineEmits(['refresh'])

const summary = ref({
  totalAsset: 0,
  todayProfit: 0,
  todayProfitRate: 0,
  totalProfit: 0,
  totalProfitRate: 0,
  fundCount: 0
})

const loading = ref(false)

let refreshTimer = null

const loadSummary = async () => {
  try {
    const response = await fundApi.getPortfolioSummary()
    if (response.code === 200 && response.data) {
      summary.value = response.data
    }
  } catch (err) {
    console.error('Failed to load portfolio summary:', err)
  }
}

const formatNumber = (value) => {
  if (value === null || value === undefined) return '¥0.00'
  const num = Number(value)
  if (num >= 0) {
    return '¥' + num.toLocaleString('zh-CN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    })
  } else {
    return '-' + '¥' + Math.abs(num).toLocaleString('zh-CN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    })
  }
}

const formatProfit = (value) => {
  if (value === null || value === undefined) return '+0.00'
  const num = Number(value)
  return (num >= 0 ? '+' : '') + num.toFixed(2)
}

const formatPercent = (value) => {
  if (value === null || value === undefined) return '0.00%'
  const num = Number(value)
  return (num >= 0 ? '+' : '') + num.toFixed(2) + '%'
}

const getProfitClass = (value) => {
  if (value === null || value === undefined || Number(value) === 0) return 'profit-zero'
  return Number(value) > 0 ? 'profit-positive' : 'profit-negative'
}

const startAutoRefresh = () => {
  refreshTimer = setInterval(() => {
    loadSummary()
  }, 30000)
}

onMounted(() => {
  loadSummary()
  startAutoRefresh()
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})

defineExpose({
  refresh: loadSummary
})
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
  background: linear-gradient(90deg, #667eea 0%, #764ba2 50%, #f093fb 100%);
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