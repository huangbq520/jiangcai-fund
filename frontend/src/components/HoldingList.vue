<template>
  <div class="holding-list">
    <div class="list-header">
      <h2>我的持仓</h2>
      <button @click="refreshList" class="refresh-btn">
        刷新
      </button>
    </div>

    <div v-if="loading" class="loading">
      加载中...
    </div>

    <div v-else-if="holdings.length === 0" class="empty">
      暂无持仓基金，请搜索添加
    </div>

    <div v-else class="table-wrapper">
      <table class="holding-table">
        <thead>
          <tr>
            <th class="col-name">基金名称</th>
            <th class="col-code">代码</th>
            <th class="col-amount">持仓金额</th>
            <th class="col-change">昨日净值</th>
            <th class="col-estimate">估算涨幅</th>
            <th class="col-profit">当日收益</th>
            <th class="col-rate">持仓收益率</th>
            <th class="col-action">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="holding in holdings"
            :key="holding.fundCode"
            class="table-row"
            @click="viewDetail(holding.fundCode)"
          >
            <td class="col-name">
              <div class="fund-name">{{ holding.fundName || holding.fundCode }}</div>
            </td>
            <td class="col-code">{{ holding.fundCode }}</td>
            <td class="col-amount">{{ formatNumber(holding.currentValue || holding.holdAmount) }}</td>
            <td class="col-change">
              {{ holding.yesterdayNetValue || '-' }}
            </td>
            <td class="col-estimate">
              <span :class="getChangeClass(holding.estimatedChange)">
                {{ formatPercent(holding.estimatedChange) }}
              </span>
              <span class="estimate-time">{{ formatEstimateTime(holding.valuationTime) }}</span>
            </td>
            <td class="col-profit" :class="getProfitClass(holding.todayProfit)">
              {{ formatProfit(holding.todayProfit) }}
            </td>
            <td class="col-rate" :class="getProfitClass(holding.profitRate)">
              {{ formatPercent(holding.profitRate) }}
            </td>
            <td class="col-action" @click.stop>
              <button @click="editHolding(holding)" class="edit-btn">编辑</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <EditHoldingModal
      v-if="showEditModal"
      :holding="selectedHolding"
      @close="closeEditModal"
      @update="handleUpdateHolding"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { fundApi } from '../api'
import EditHoldingModal from './EditHoldingModal.vue'

const emit = defineEmits(['update', 'view-detail'])

const holdings = ref([])
const loading = ref(false)
const showEditModal = ref(false)
const selectedHolding = ref(null)

let refreshTimer = null

const loadHoldings = async () => {
  loading.value = true
  try {
    const response = await fundApi.getHoldingList()
    if (response.code === 200) {
      holdings.value = response.data || []
    }
  } catch (err) {
    console.error('Failed to load holdings:', err)
  } finally {
    loading.value = false
  }
}

const refreshList = () => {
  loadHoldings()
  emit('update')
}

const editHolding = (holding) => {
  selectedHolding.value = { ...holding }
  showEditModal.value = true
}

const viewDetail = (fundCode) => {
  emit('view-detail', fundCode)
}

const closeEditModal = () => {
  showEditModal.value = false
  selectedHolding.value = null
}

const handleUpdateHolding = async () => {
  closeEditModal()
  await loadHoldings()
  emit('update')
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

const formatEstimateTime = (timeStr) => {
  if (!timeStr) return ''
  const str = String(timeStr)
  if (str === 'null' || str === '-' || str === 'undefined') return ''
  if (str.includes('-') && str.includes(' ')) {
    const parts = str.split(' ')
    if (parts.length >= 2) {
      const dateParts = parts[0].split('-')
      if (dateParts.length >= 3) {
        return `${dateParts[1]}-${dateParts[2]} ${parts[1]}`
      }
    }
  }
  return str
}

const getChangeClass = (value) => {
  if (value === null || value === undefined || Number(value) === 0) return 'profit-zero'
  return Number(value) > 0 ? 'profit-positive' : 'profit-negative'
}

const getProfitClass = (value) => {
  if (value === null || value === undefined || Number(value) === 0) return 'profit-zero'
  return Number(value) > 0 ? 'profit-positive' : 'profit-negative'
}

onMounted(() => {
  loadHoldings()
  refreshTimer = setInterval(loadHoldings, 30000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})

defineExpose({ refreshList })
</script>

<style scoped>
.holding-list {
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.list-header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.refresh-btn {
  padding: 8px 18px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf0 100%);
  color: #666;
  border: none;
  border-radius: 20px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.3s;
}

.refresh-btn:hover {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.loading, .empty {
  text-align: center;
  padding: 50px;
  color: #999;
  font-size: 15px;
}

.table-wrapper {
  overflow-x: auto;
  border-radius: 12px;
  border: 1px solid #f0f0f0;
}

.holding-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.holding-table th,
.holding-table td {
  padding: 14px 10px;
  text-align: center;
  border-bottom: 1px solid #f0f0f0;
}

.holding-table th {
  background: linear-gradient(135deg, #f8f9fa 0%, #f0f2f5 100%);
  font-weight: 600;
  font-size: 13px;
  color: #666;
  letter-spacing: 0.3px;
}

.table-row {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
  background: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.table-row:hover {
  background: linear-gradient(135deg, #f8f9ff 0%, #f5f7ff 100%);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.18);
  z-index: 10;
  position: relative;
}

.table-row:last-child td {
  border-bottom: none;
}

.table-row:last-child {
  border-bottom-left-radius: 12px;
  border-bottom-right-radius: 12px;
}

.col-name {
  text-align: left !important;
  width: 160px;
}

.col-name .fund-name {
  font-weight: 500;
  color: #333;
  font-size: 14px;
}

.col-code {
  width: 90px;
  color: #999;
  font-size: 13px;
}

.col-amount {
  width: 120px;
  text-align: right !important;
  font-weight: 500;
}

.col-change,
.col-estimate {
  width: 100px;
}

.col-estimate .estimate-time {
  display: block;
  font-size: 11px;
  color: #999;
  margin-top: 2px;
}

.col-rate {
  width: 100px;
}

.col-profit {
  width: 110px;
  text-align: right !important;
  font-weight: 500;
}

.col-action {
  width: 80px;
}

.edit-btn {
  padding: 6px 14px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 16px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 2px 6px rgba(102, 126, 234, 0.25);
}

.edit-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(102, 126, 234, 0.35);
}
</style>