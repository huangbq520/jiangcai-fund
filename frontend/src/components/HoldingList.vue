<template>
  <div class="holding-list" :class="{ 'is-batch-delete': isBatchDeleteMode }">
    <div class="list-header">
      <h2>我的持仓</h2>
      <div class="header-buttons">
        <button @click="refreshList" class="refresh-btn">
          <span>刷新</span>
        </button>
        <button 
          v-if="!isBatchDeleteMode"
          @click="startBatchDelete" 
          class="batch-btn"
        >
          <span>删除</span>
        </button>
        <div v-else class="batch-actions">
          <button 
            @click="handleBatchDelete" 
            class="delete-btn"
            :disabled="selectedFundCodes.size === 0"
          >
            <span>删除</span>
          </button>
          <button 
            @click="cancelBatchDelete" 
            class="cancel-btn"
          >
            <span>取消</span>
          </button>
        </div>
      </div>
    </div>

    <div v-if="fundStore.loading.holdings" class="loading">
      <svg viewBox="0 0 240 240" height="240" width="240" class="pl">
        <circle stroke-linecap="round" stroke-dashoffset="-330" stroke-dasharray="0 660" stroke-width="20" stroke="#000" fill="none" r="105" cy="120" cx="120" class="pl__ring pl__ring--a"></circle>
        <circle stroke-linecap="round" stroke-dashoffset="-110" stroke-dasharray="0 220" stroke-width="20" stroke="#000" fill="none" r="35" cy="120" cx="120" class="pl__ring pl__ring--b"></circle>
        <circle stroke-linecap="round" stroke-dasharray="0 440" stroke-width="20" stroke="#000" fill="none" r="70" cy="120" cx="85" class="pl__ring pl__ring--c"></circle>
        <circle stroke-linecap="round" stroke-dasharray="0 440" stroke-width="20" stroke="#000" fill="none" r="70" cy="120" cx="155" class="pl__ring pl__ring--d"></circle>
      </svg>
    </div>

    <div v-else-if="holdings.length === 0" class="empty">
      暂无持仓基金，请搜索添加
    </div>

    <div v-else class="table-wrapper">
      <table class="holding-table">
        <thead>
          <tr>
            <th v-if="isBatchDeleteMode" class="col-checkbox">
              <input 
                type="checkbox" 
                :checked="isAllSelected"
                @change="toggleSelectAll"
                class="checkbox"
              />
            </th>
            <th class="col-name">基金名称</th>
            <th class="col-code">代码</th>
            <th class="col-latest-change">最新涨幅</th>
            <th class="col-latest-net-value">最新净值</th>
            
            <th class="col-estimate">估算涨幅</th>
            <th class="col-profit">今日收益</th>
            <th class="col-yesterday">昨日收益</th>
            <th class="col-amount">持仓金额</th>
            <th class="col-rate">持仓收益率</th>
            <th class="col-week">近一周</th>
            <th class="col-month">近一月</th>
            <th class="col-three-month">近三月</th>
            <th class="col-six-month">近六月</th>
            <th class="col-year">近一年</th>
            <th class="col-cost-price">成本净值</th>
            <th class="col-cost-amount">持仓成本</th>
            <th class="col-action">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="holding in holdings"
            :key="holding.fundCode"
            class="table-row"
            @click="!isBatchDeleteMode && viewDetail(holding.fundCode)"
          >
            <td v-if="isBatchDeleteMode" class="col-checkbox" @click.stop>
              <input 
                type="checkbox" 
                :checked="selectedFundCodes.has(holding.fundCode)"
                @change="toggleSelectFund(holding.fundCode)"
                class="checkbox"
              />
            </td>
            <td class="col-name">
              <div class="fund-name">{{ holding.fundName || holding.fundCode }}</div>
            </td>
            <td class="col-code">{{ holding.fundCode }}</td>
            <td class="col-latest-change">
              <div class="latest-container">
                <span :class="getProfitClass(holding.yesterdayChange)">
                  {{ formatPercent(holding.yesterdayChange) }}
                </span>
                <span class="latest-date">{{ holding.latestNetValueDate || formatLatestDate(holding) }}</span>
              </div>
            </td>
            <td class="col-latest-net-value">
              <div class="latest-container">
                <span>{{ holding.currentNetValue || holding.unitNetValue }}</span>
                <span class="latest-date">{{ holding.latestNetValueDate || formatLatestDate(holding) }}</span>
              </div>
            </td>
            
            <td class="col-estimate">
              <span :class="getProfitClass(holding.estimatedChange)">
                {{ formatPercent(holding.estimatedChange) }}
              </span>
              <span class="estimate-time">{{ formatEstimateTime(holding.valuationTime) }}</span>
            </td>
            <td class="col-yesterday" :class="getProfitClass(holding.yesterdayProfit)">
              <div class="yesterday-container">
                <span class="yesterday-profit">
                  {{ shouldShowPlaceholder(holding) ? '--' : (holding.yesterdayProfit != null ? formatProfit(holding.yesterdayProfit) : '--') }}
                </span>
                <span 
                  v-if="!shouldShowPlaceholder(holding) && holding.yesterdayChange != null" 
                  :class="['yesterday-rate', getProfitClass(holding.yesterdayChange)]"
                >
                  {{ formatPercent(holding.yesterdayChange) }}
                </span>
              </div>
            </td>
            <td class="col-profit" :class="getProfitClass(holding.todayProfit)">
              {{ shouldShowPlaceholder(holding) || !isTodayProfitAvailable(holding) ? '--' : formatProfit(holding.todayProfit) }}
            </td>
            <td class="col-amount">{{ shouldShowPlaceholder(holding) ? '--' : formatNumber(holding.currentValue != null ? holding.currentValue : holding.holdAmount) }}</td>
            <td class="col-rate" :class="getProfitClass(holding.profitRate)">
              {{ shouldShowPlaceholder(holding) ? '--' : formatPercent(holding.profitRate) }}
            </td>
            <td class="col-week" :class="getProfitClass(holding.oneWeekChange)">
              {{ holding.oneWeekChange != null ? formatPercent(holding.oneWeekChange) : '--' }}
            </td>
            <td class="col-month" :class="getProfitClass(holding.oneMonthChange)">
              {{ holding.oneMonthChange != null ? formatPercent(holding.oneMonthChange) : '--' }}
            </td>
            <td class="col-three-month" :class="getProfitClass(holding.threeMonthChange)">
              {{ holding.threeMonthChange != null ? formatPercent(holding.threeMonthChange) : '--' }}
            </td>
            <td class="col-six-month" :class="getProfitClass(holding.sixMonthChange)">
              {{ holding.sixMonthChange != null ? formatPercent(holding.sixMonthChange) : '--' }}
            </td>
            <td class="col-year" :class="getProfitClass(holding.oneYearChange)">
              {{ holding.oneYearChange != null ? formatPercent(holding.oneYearChange) : '--' }}
            </td>
            <td class="col-cost-price">
              {{ shouldShowPlaceholder(holding) ? '--' : (holding.costPrice != null ? (Number(holding.costPrice)).toFixed(4) : '--') }}
            </td>
            <td class="col-cost-amount">
              {{ shouldShowPlaceholder(holding) ? '--' : (holding.costAmount != null ? formatNumber(holding.costAmount) : '--') }}
            </td>
            <td class="col-action" @click.stop>
              <button @click="editHolding(holding)" class="edit-btn">
                <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
                编辑
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <Teleport to="body">
      <EditHoldingModal
        v-if="showEditModal"
        :holding="selectedHolding"
        @close="closeEditModal"
        @update="handleUpdateHolding"
      />
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import EditHoldingModal from './EditHoldingModal.vue'
import { useFundStore } from '../stores/fundStore'
import { storeToRefs } from 'pinia'
import { formatNumber, formatProfit, formatPercent, getProfitClass } from '../composables/useFormat'
import { useAutoRefresh, isTradingHours } from '../composables/useAutoRefresh'

const emit = defineEmits(['update', 'view-detail'])

const fundStore = useFundStore()
const { holdings } = storeToRefs(fundStore)

const showEditModal = ref(false)
const selectedHolding = ref(null)
const isBatchDeleteMode = ref(false)
const selectedFundCodes = ref(new Set())

const isAllSelected = computed(() => {
  return holdings.value.length > 0 && selectedFundCodes.value.size === holdings.value.length
})

useAutoRefresh(() => fundStore.silentFetchHoldings(), 30000, isTradingHours)

const refreshList = () => {
  fundStore.fetchHoldings()
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
  await fundStore.fetchHoldings()
  emit('update')
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

const shouldShowPlaceholder = (holding) => {
  const holdAmount = holding.currentValue != null ? holding.currentValue : holding.holdAmount
  return !holdAmount || holdAmount === 0
}

// 今日收益是否可用：仅当最新净值日期为今天时才计算
const isTodayProfitAvailable = (holding) => {
  if (!holding.latestNetValueDate) return false
  const today = new Date()
  const mm = String(today.getMonth() + 1).padStart(2, '0')
  const dd = String(today.getDate()).padStart(2, '0')
  return holding.latestNetValueDate === `${mm}-${dd}`
}

const calculateYesterdayRate = (holding) => {
  if (!holding.yesterdayProfit || !holding.holdAmount || holding.holdAmount === 0) {
    return null
  }
  // 计算昨天的收益率，使用昨日收益 / (当前市值 - 昨日收益)
  const yesterdayValue = (holding.currentValue != null ? holding.currentValue : holding.holdAmount) - holding.yesterdayProfit
  if (!yesterdayValue || yesterdayValue === 0) {
    return null
  }
  return (holding.yesterdayProfit / yesterdayValue) * 100
}

const formatLatestDate = (holding) => {
  // 尝试多种方式获取日期
  if (holding.valuationTime) {
    const str = String(holding.valuationTime)
    if (str.includes('-') && str.includes(' ')) {
      const parts = str.split(' ')
      if (parts.length >= 1) {
        const dateParts = parts[0].split('-')
        if (dateParts.length >= 3) {
          return `${dateParts[1]}-${dateParts[2]}`
        }
      }
    }
  }
  
  // 默认使用今天的日期
  const today = new Date()
  const month = String(today.getMonth() + 1).padStart(2, '0')
  const day = String(today.getDate()).padStart(2, '0')
  return `${month}-${day}`
}

const startBatchDelete = () => {
  isBatchDeleteMode.value = true
  selectedFundCodes.value.clear()
}

const cancelBatchDelete = () => {
  isBatchDeleteMode.value = false
  selectedFundCodes.value.clear()
}

const toggleSelectFund = (fundCode) => {
  const newSet = new Set(selectedFundCodes.value)
  if (newSet.has(fundCode)) {
    newSet.delete(fundCode)
  } else {
    newSet.add(fundCode)
  }
  selectedFundCodes.value = newSet
}

const toggleSelectAll = (event) => {
  if (event.target.checked) {
    selectedFundCodes.value = new Set(holdings.value.map(h => h.fundCode))
  } else {
    selectedFundCodes.value.clear()
  }
}

const handleBatchDelete = async () => {
  if (selectedFundCodes.value.size === 0) {
    alert('请先选择要删除的基金')
    return
  }

  const fundCodesArray = [...selectedFundCodes.value]
  console.log('准备删除的基金代码:', fundCodesArray)

  const confirmed = confirm(`确认删除选中 ${fundCodesArray.length} 条持仓数据？删除后数据不可恢复。`)
  if (!confirmed) {
    return
  }

  try {
    console.log('开始调用删除接口...')
    const response = await fundStore.deleteBatch(fundCodesArray)
    console.log('删除接口响应:', response)
    
    if (response.code === 200) {
      console.log('删除成功，刷新列表')
      cancelBatchDelete()
      await fundStore.fetchHoldings()
      await fundStore.fetchSummary()
      emit('update')
      alert('删除成功')
    } else {
      alert(response.message || '删除失败')
    }
  } catch (error) {
    console.error('删除失败，详细错误:', error)
    alert('删除失败: ' + (error.message || '未知错误'))
  }
}
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

.header-buttons {
  display: flex;
  gap: 12px;
  align-items: center;
}

.batch-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.batch-btn,
.delete-btn,
.cancel-btn {
  background: transparent;
  position: relative;
  padding: 1px 5px;
  display: flex;
  align-items: center;
  font-size: 17px;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
  border: 1px solid #f56c6c;
  border-radius: 25px;
  outline: none;
  overflow: hidden;
  color: #f56c6c;
  transition: color 0.3s 0.1s ease-out, opacity 0.2s;
  text-align: center;
}

.batch-btn span,
.delete-btn span,
.cancel-btn span {
  margin: 10px;
}

.delete-btn {
  border-color: #f56c6c;
  color: #f56c6c;
}

.delete-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.cancel-btn {
  border-color: #909399;
  color: #909399;
}

.batch-btn::before,
.delete-btn::before,
.cancel-btn::before {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  margin: auto;
  content: '';
  border-radius: 50%;
  display: block;
  width: 20em;
  height: 20em;
  left: -5em;
  text-align: center;
  transition: box-shadow 0.5s ease-out;
  z-index: -1;
}

.batch-btn:hover {
  color: #fff;
  border: 1px solid #f56c6c;
}

.batch-btn:hover::before {
  box-shadow: inset 0 0 0 10em #f56c6c;
}

.delete-btn:not(:disabled):hover {
  color: #fff;
  border: 1px solid #f56c6c;
}

.delete-btn:not(:disabled):hover::before {
  box-shadow: inset 0 0 0 10em #f56c6c;
}

.cancel-btn:hover {
  color: #fff;
  border: 1px solid #909399;
}

.cancel-btn:hover::before {
  box-shadow: inset 0 0 0 10em #909399;
}

.col-checkbox {
  width: 50px;
  text-align: center;
}

.checkbox {
  width: 18px;
  height: 18px;
  cursor: pointer;
}

.list-header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.refresh-btn {
  background: transparent;
  position: relative;
  padding: 1px 5px;
  display: flex;
  align-items: center;
  font-size: 17px;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
  border: 1px solid rgb(40, 144, 241);
  border-radius: 25px;
  outline: none;
  overflow: hidden;
  color: rgb(40, 144, 241);
  transition: color 0.3s 0.1s ease-out;
  text-align: center;
}

.refresh-btn span {
  margin: 10px;
}

.refresh-btn::before {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  margin: auto;
  content: '';
  border-radius: 50%;
  display: block;
  width: 20em;
  height: 20em;
  left: -5em;
  text-align: center;
  transition: box-shadow 0.5s ease-out;
  z-index: -1;
}

.refresh-btn:hover {
  color: #fff;
  border: 1px solid rgb(40, 144, 241);
}

.refresh-btn:hover::before {
  box-shadow: inset 0 0 0 10em rgb(40, 144, 241);
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
  table-layout: auto;
  min-width: 2000px;
}

.holding-table th,
.holding-table td {
  padding: 14px 10px;
  text-align: center;
  border-bottom: 1px solid #f0f0f0;
}

.holding-table th {

  background: #f8fafc;
  font-weight: 700;
  font-size: 13px;
  color: #000000;
  letter-spacing: 0.3px;
}

.holding-table th.col-action {
  position: sticky;
  right: 0;
  z-index: 20;
  background-color: #f8fafc;
}

.holding-table th.col-name {
  position: sticky;
  left: 0;
  z-index: 20;
  background-color: #f8fafc;
}

.holding-table th.col-checkbox {
  position: sticky;
  left: 0;
  z-index: 25;
  background-color: #f8fafc;
}

.col-checkbox {
  width: 50px;
  text-align: center;
  position: sticky;
  left: 0;
  z-index: 15;
  background-color: white;
}

/* 当有复选框列时，基金名称列在复选框列的右侧 */
.is-batch-delete .holding-table th.col-name,
.is-batch-delete .col-name {
  left: 50px;
}

.table-row {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
  background: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.table-row:hover {
  background: #f0f5ff;
  box-shadow: 0 4px 16px rgba(22, 119, 255, 0.12);
}

/* 确保固定列的背景色与行背景同步变化 */
.table-row .col-name,
.table-row .col-action,
.table-row .col-checkbox {
  transition: background-color 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.table-row:hover .col-name,
.table-row:hover .col-action,
.table-row:hover .col-checkbox {
  background-color: #f0f5ff;
}

.table-row:last-child td {
  border-bottom: none;
}

.table-row:last-child {
  border-bottom-left-radius: 12px;
  border-bottom-right-radius: 12px;
}

.col-name {
  /* text-align: left !important; */
  width: 160px;
  position: sticky;
  left: 0;
  z-index: 10;
  background-color: white;
}

.col-name .fund-name {
  font-weight: 700;
  color: #000000;
  font-size: 14px;
}

.col-code {
  width: 90px;
  color: #999;
  font-size: 13px;
}

.col-cost-price,
.col-cost-amount {
  width: 110px;
  font-size: 14px;
}

.col-latest-change,
.col-latest-net-value {
  width: 100px;
  font-size: 14px;
}

.latest-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.latest-date {
  font-size: 11px;
  color: #999;
  line-height: 1.2;
}

.col-amount {
  width: 120px;
  /* text-align: right !important; */
  font-weight: 500;
}

.col-change,
.col-estimate {
  width: 100px;
  font-size: 14px;
}

.col-estimate .estimate-time {
  display: block;
  font-size: 11px;
  color: #999;
  margin-top: 2px;
}

.col-rate {
  width: 100px;
  font-size: 14px;
}

.col-yesterday {
  width: 110px;
  /* text-align: right !important; */
  font-weight: 500;
  font-size: 14px;
}

.yesterday-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.yesterday-rate {
  font-size: 11px;
  line-height: 1.2;
  color: #999;
}

.col-profit {
  width: 95px;
  /* text-align: right !important; */
  font-weight: 500;
  font-size: 14px;
}

.col-action {
  width: 90px;
  position: sticky;
  right: 0;
  z-index: 10;
  background-color: white;
}

.col-week,
.col-month,
.col-three-month,
.col-six-month,
.col-year {
  width: 85px;
  font-size: 14px;
}

.edit-btn {
  position: relative;
  transition: all 0.3s ease-in-out;
  box-shadow: 0px 10px 20px rgba(0, 0, 0, 0.2);
  padding-block: 0.35rem;
  padding-inline: 0.8rem;
  background-color: rgb(0 107 179);
  border-radius: 9999px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #ffff;
  gap: 6px;
  font-weight: bold;
  border: 2px solid #ffffff4d;
  outline: none;
  overflow: hidden;
  font-size: 13px;
  white-space: nowrap;
  min-width: auto;
  width: auto;
}

.edit-btn .icon {
  width: 16px;
  height: 16px;
  transition: all 0.3s ease-in-out;
}

.edit-btn:hover {
  transform: scale(1.05);
  border-color: #fff9;
}

.edit-btn:hover .icon {
  transform: translate(4px);
}

.edit-btn:hover::before {
  animation: shine 1.5s ease-out infinite;
}

.edit-btn::before {
  content: "";
  position: absolute;
  width: 100px;
  height: 100%;
  background-image: linear-gradient(
    120deg,
    rgba(255, 255, 255, 0) 30%,
    rgba(255, 255, 255, 0.8),
    rgba(255, 255, 255, 0) 70%
  );
  top: 0;
  left: -100px;
  opacity: 0.6;
}

@keyframes shine {
  0% {
    left: -100px;
  }

  60% {
    left: 100%;
  }

  to {
    left: 100%;
  }
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

.pl__ring--d {
  animation-name: ringD;
  stroke: #2cf425;
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

@keyframes ringD {
  from,
  8% {
    stroke-dasharray: 0 440;
    stroke-width: 20;
    stroke-dashoffset: 0;
  }

  16% {
    stroke-dasharray: 40 400;
    stroke-width: 30;
    stroke-dashoffset: -5;
  }

  36% {
    stroke-dasharray: 40 400;
    stroke-width: 30;
    stroke-dashoffset: -175;
  }

  44%,
  50% {
    stroke-dasharray: 0 440;
    stroke-width: 20;
    stroke-dashoffset: -220;
  }

  58% {
    stroke-dasharray: 40 400;
    stroke-width: 30;
    stroke-dashoffset: -225;
  }

  78% {
    stroke-dasharray: 40 400;
    stroke-width: 30;
    stroke-dashoffset: -395;
  }

  86%,
  to {
    stroke-dasharray: 0 440;
    stroke-width: 20;
    stroke-dashoffset: -440;
  }
}

</style>