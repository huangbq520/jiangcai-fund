<template>
  <div class="modal-overlay" @click.self="handleClose">
    <div class="modal-content">
      <div class="modal-header">
        <h2>基金详情 <span class="fund-code">{{ fundCode }}</span></h2>
        <button @click="handleClose" class="close-btn">×</button>
      </div>

      <div class="modal-body">
        <div v-if="loading" class="loading">
          <svg viewBox="0 0 240 240" height="240" width="240" class="pl">
            <circle stroke-linecap="round" stroke-dashoffset="-330" stroke-dasharray="0 660" stroke-width="20" stroke="#000" fill="none" r="105" cy="120" cx="120" class="pl__ring pl__ring--a"></circle>
            <circle stroke-linecap="round" stroke-dashoffset="-110" stroke-dasharray="0 220" stroke-width="20" stroke="#000" fill="none" r="35" cy="120" cx="120" class="pl__ring pl__ring--b"></circle>
            <circle stroke-linecap="round" stroke-dasharray="0 440" stroke-width="20" stroke="#000" fill="none" r="70" cy="120" cx="85" class="pl__ring pl__ring--c"></circle>
            <circle stroke-linecap="round" stroke-dasharray="0 440" stroke-width="20" stroke="#000" fill="none" r="70" cy="120" cx="155" class="pl__ring pl__ring--d"></circle>
          </svg>
        </div>

        <div v-else-if="error" class="error">
          <span class="error-icon">!</span>
          {{ error }}
        </div>

        <template v-else-if="detail">
          <div class="info-card">
            <div class="card-accent"></div>
            <span class="val-time">{{ detail.gztime || '' }}</span>
            <h3>基本信息</h3>
            <div class="info-grid">
              <div class="info-item">
                <span class="label">基金名称</span>
                <span class="value fund-name">{{ detail.fundName || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">基金代码</span>
                <span class="value code">{{ detail.fundCode || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">单位净值</span>
                <span class="value">{{ detail.dwjz || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">估算净值</span>
                <span class="value">{{ detail.gsz || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">估算涨跌幅</span>
                <span class="value" :class="getChangeClassLocal(detail.gszzl)">
                  {{ formatPercentLocal(detail.gszzl) }}
                </span>
              </div>
              <div class="info-item">
                <span class="label">净值日期</span>
                <span class="value">{{ detail.jzrq || '-' }}</span>
              </div>
            </div>
            <div class="holding-divider"></div>
            <div class="info-grid">
              <div class="info-item">
                <span class="label">持仓金额</span>
                <span class="value">{{ holdingInfo.holdAmount || '--' }}</span>
              </div>
              <div class="info-item">
                <span class="label">当日收益</span>
                <span class="value" :class="profitClass(holdingInfo.todayProfit)">{{ holdingInfo.todayProfit || '--' }}</span>
              </div>
              <div class="info-item">
                <span class="label">持有收益</span>
                <span class="value" :class="profitClass(holdingInfo.profitRate)">{{ holdingInfo.profitRate || '--' }}</span>
              </div>
            </div>
          </div>

          <div class="chart-card">
            <div class="card-accent"></div>
            <div class="chart-tabs">
              <button
                :class="['chart-tab', { active: chartTab === 'trend' }]"
                @click="chartTab = 'trend'"
              >业绩走势</button>
              <button
                :class="['chart-tab', { active: chartTab === 'myprofit' }]"
                @click="chartTab = 'myprofit'"
              >我的收益</button>
            </div>
            <FundTrendChart v-if="chartTab === 'trend'" :fundCode="fundCode" />
            <MyProfitTab v-if="chartTab === 'myprofit'" :fundCode="fundCode" />
          </div>

          <div class="holdings-card">
            <div class="card-accent"></div>
            <h3>持仓股票 (前10)</h3>
            <table v-if="detail.holdings && detail.holdings.length > 0" class="holdings-table">
              <thead>
                <tr>
                  <th class="col-index">序号</th>
                  <th class="col-code">股票代码</th>
                  <th class="col-name">股票名称</th>
                  <th class="col-change">涨跌幅</th>
                  <th class="col-weight">占比</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(holding, index) in detail.holdings" :key="index" class="holding-row">
                  <td class="col-index">{{ index + 1 }}</td>
                  <td class="col-code">{{ holding.stockCode }}</td>
                  <td class="col-name">{{ holding.stockName }}</td>
                  <td class="col-change" :class="getChangeClassLocal(holding.change)">
                    {{ formatChange(holding.change) }}
                  </td>
                  <td class="col-weight">
                    <span class="weight-badge">{{ holding.weight }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-else class="no-data">
              <span class="no-data-icon">📭</span>
              <span>暂无持仓数据</span>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { fundApi } from '../api'
import FundTrendChart from './FundTrendChart.vue'
import MyProfitTab from './MyProfitTab.vue'
import { formatPercent, getProfitClass } from '../composables/useFormat'
import { useFundStore } from '../stores/fundStore'
import { storeToRefs } from 'pinia'

const props = defineProps({
  fundCode: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['close'])

const fundStore = useFundStore()
const { holdings } = storeToRefs(fundStore)

const loading = ref(false)
const error = ref('')
const detail = ref(null)
const chartTab = ref('trend')

const holdingInfo = computed(() => {
  const h = holdings.value?.find(h => h.fundCode === props.fundCode)
  if (!h) return {}
  return {
    holdAmount: (h.currentValue != null ? '¥' + Number(h.currentValue).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) : h.holdAmount ? '¥' + Number(h.holdAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) : null),
    todayProfit: h.todayProfit != null ? (Number(h.todayProfit) >= 0 ? '+' : '') + Number(h.todayProfit).toFixed(2) : null,
    profitRate: h.profitRate != null ? (Number(h.profitRate) >= 0 ? '+' : '') + Number(h.profitRate).toFixed(2) + '%' : null
  }
})

const profitClass = (val) => {
  if (val === null || val === undefined) return ''
  return Number(val) > 0 ? 'positive' : Number(val) < 0 ? 'negative' : ''
}

const loadDetail = async () => {
  loading.value = true
  error.value = ''

  try {
    const response = await fundApi.getFundData(props.fundCode)
    if (response.code === 200 && response.data) {
      detail.value = response.data
    } else {
      error.value = response.message || '加载失败'
    }
  } catch (err) {
    error.value = '加载失败，请稍后重试'
    console.error(err)
  } finally {
    loading.value = false
  }
}

const handleClose = () => {
  emit('close')
}

const formatPercentLocal = (value) => formatPercent(value, { nullDisplay: '-' })
const getChangeClassLocal = (value) => getProfitClass(value, { prefix: '', zeroClass: false })
const formatChange = (value) => {
  if (value === null || value === undefined) return '-'
  const n = Number(value)
  return (n >= 0 ? '+' : '') + n.toFixed(2) + '%'
}

watch(() => props.fundCode, () => {
  loadDetail()
})

onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  z-index: 9999;
  padding-top: 80px;
  overflow-y: auto;
}

.modal-content {
  background: white;
  border-radius: 20px;
  width: 90%;
  max-width: 920px;
  max-height: calc(100vh - 120px);
  overflow-y: auto;
  box-shadow: 0 25px 60px rgba(0, 0, 0, 0.25);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 28px;
  border-bottom: 1px solid #f0f0f0;
  position: sticky;
  top: 0;
  background: white;
  border-radius: 20px 20px 0 0;
  z-index: 10;
}

.modal-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #333;
}

.fund-code {
  color: #888;
  font-weight: 400;
  font-size: 16px;
  margin-left: 8px;
}

.close-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf0 100%);
  border-radius: 50%;
  font-size: 22px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666;
  transition: all 0.3s;
}

.close-btn:hover {
  background: linear-gradient(135deg, #e53935 0%, #c0392b 100%);
  color: white;
  transform: scale(1.05);
}

.modal-body {
  padding: 24px 28px 32px;
}

.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px;
  color: #888;
  gap: 16px;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #f0f0f0;
  border-top-color: #1677ff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px;
  color: #e53935;
  font-size: 15px;
  background: linear-gradient(135deg, #fff5f5 0%, #ffe8e8 100%);
  border-radius: 12px;
  border: 1px solid #ffd4d4;
}

.error-icon {
  width: 28px;
  height: 28px;
  background: #e53935;
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 16px;
}

.info-card, .chart-card, .holdings-card {
  margin-bottom: 24px;
  padding: 20px 24px 24px;
  background: #fafbfc;
  border-radius: 16px;
  border: 1px solid #f0f0f0;
  position: relative;
  overflow: hidden;
  transition: box-shadow 0.3s;
}

.info-card:hover, .chart-card:hover, .holdings-card:hover {
  box-shadow: 0 8px 32px rgba(102, 126, 234, 0.12);
}

.card-accent {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, #1677ff 0%, #1677ff 50%, #69b1ff 100%);
}

.val-time {
  position: absolute;
  top: 16px;
  right: 20px;
  font-size: 12px;
  color: #94a3b8;
}

.holding-divider {
  border-top: 1px dashed #e2e8f0;
  margin: 8px 0 16px;
}

.info-card h3, .chart-card h3, .holdings-card h3 {
  margin: 0 0 20px 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
  padding-top: 8px;
}

.chart-tabs {
  display: flex;
  gap: 0;
  margin-bottom: 16px;
  padding-top: 8px;
  border-bottom: 2px solid #f1f5f9;
}

.chart-tab {
  padding: 10px 20px;
  border: none;
  background: transparent;
  font-size: 14px;
  font-weight: 500;
  color: #94a3b8;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: all 0.2s;
}

.chart-tab:hover {
  color: #475569;
}

.chart-tab.active {
  color: #1677ff;
  border-bottom-color: #1677ff;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 20px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.info-item .label {
  font-size: 12px;
  color: #888;
  font-weight: 500;
  letter-spacing: 0.3px;
}

.info-item .value {
  font-size: 15px;
  color: #333;
  font-weight: 600;
}

.info-item .value.fund-name {
  color: #333;
  font-size: 15px;
}

.info-item .value.code {
  color: #1677ff;
  background: rgba(102, 126, 234, 0.1);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 13px;
  display: inline-block;
  width: fit-content;
}

.positive {
  color: #e53935 !important;
}

.negative {
  color: #009e5f !important;
}

.holdings-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
}

.holdings-table th {
  background: linear-gradient(135deg, #f8f9fa 0%, #f0f2f5 100%);
  font-weight: 600;
  font-size: 13px;
  color: #666;
  padding: 14px 16px;
  text-align: left;
}

.holdings-table th:first-child {
  border-radius: 10px 0 0 10px;
}

.holdings-table th:last-child {
  border-radius: 0 10px 10px 0;
}

.holdings-table td {
  padding: 14px 16px;
  border-bottom: 1px solid #f0f0f0;
  color: #555;
  font-size: 14px;
}

.holding-row {
  transition: all 0.3s;
}

.holding-row:hover {
  background: linear-gradient(135deg, #f8f9ff 0%, #f5f7ff 100%);
}

.holding-row:last-child td {
  border-bottom: none;
}

.holding-row:last-child td:first-child {
  border-radius: 0 0 0 10px;
}

.holding-row:last-child td:last-child {
  border-radius: 0 0 10px 0;
}

.col-index {
  width: 60px;
  color: #999 !important;
  font-size: 13px !important;
}

.col-code {
  width: 100px;
  color: #1677ff !important;
  font-weight: 500 !important;
}

.col-name {
  font-weight: 500 !important;
}

.col-change {
  width: 90px;
  text-align: right !important;
  font-weight: 500;
}

.col-weight {
  text-align: right !important;
}

.weight-badge {
  background: linear-gradient(135deg, #f0f4ff 0%, #e8ecff 100%);
  color: #1677ff;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 500;
}

.no-data {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 50px;
  color: #999;
  gap: 12px;
}

.no-data-icon {
  font-size: 40px;
  opacity: 0.7;
}

.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 50px;
}

.pl {
  width: 6em;
  height: 6em;
}

.pl__ring {
  animation: ringA 2s linear infinite;
}

.pl__ring--a {
  stroke: #f42f25;
}

.pl__ring--b {
  animation-name: ringB;
  stroke: #ffdd00;
}

.pl__ring--c {
  animation-name: ringC;
  stroke: #255ff4;
}

@keyframes ringA {
  from,
  4% {
    stroke-dasharray: 0 660;
    stroke-width: 20;
    stroke-dashoffset: -330;
  }

  12% {
    stroke-dasharray: 60 600;
    stroke-width: 30;
    stroke-dashoffset: -335;
  }

  32% {
    stroke-dasharray: 60 600;
    stroke-width: 30;
    stroke-dashoffset: -595;
  }

  40%,
  54% {
    stroke-dasharray: 0 660;
    stroke-width: 20;
    stroke-dashoffset: -660;
  }

  62% {
    stroke-dasharray: 60 600;
    stroke-width: 30;
    stroke-dashoffset: -665;
  }

  82% {
    stroke-dasharray: 60 600;
    stroke-width: 30;
    stroke-dashoffset: -925;
  }

  90%,
  to {
    stroke-dasharray: 0 660;
    stroke-width: 20;
    stroke-dashoffset: -990;
  }
}

@keyframes ringB {
  from,
  12% {
    stroke-dasharray: 0 220;
    stroke-width: 20;
    stroke-dashoffset: -110;
  }

  20% {
    stroke-dasharray: 20 200;
    stroke-width: 30;
    stroke-dashoffset: -115;
  }

  40% {
    stroke-dasharray: 20 200;
    stroke-width: 30;
    stroke-dashoffset: -195;
  }

  48%,
  62% {
    stroke-dasharray: 0 220;
    stroke-width: 20;
    stroke-dashoffset: -220;
  }

  70% {
    stroke-dasharray: 20 200;
    stroke-width: 30;
    stroke-dashoffset: -225;
  }

  90% {
    stroke-dasharray: 20 200;
    stroke-width: 30;
    stroke-dashoffset: -305;
  }

  98%,
  to {
    stroke-dasharray: 0 220;
    stroke-width: 20;
    stroke-dashoffset: -330;
  }
}

@keyframes ringC {
  from {
    stroke-dasharray: 0 440;
    stroke-width: 20;
    stroke-dashoffset: 0;
  }

  8% {
    stroke-dasharray: 40 400;
    stroke-width: 30;
    stroke-dashoffset: -5;
  }

  28% {
    stroke-dasharray: 40 400;
    stroke-width: 30;
    stroke-dashoffset: -175;
  }

  36%,
  58% {
    stroke-dasharray: 0 440;
    stroke-width: 20;
    stroke-dashoffset: -220;
  }

  66% {
    stroke-dasharray: 40 400;
    stroke-width: 30;
    stroke-dashoffset: -225;
  }

  86% {
    stroke-dasharray: 40 400;
    stroke-width: 30;
    stroke-dashoffset: -395;
  }

  94%,
  to {
    stroke-dasharray: 0 440;
    stroke-width: 20;
    stroke-dashoffset: -440;
  }
}
</style>