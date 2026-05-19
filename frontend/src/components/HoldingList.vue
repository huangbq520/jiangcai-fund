<template>
  <div class="holding-list">
    <div class="list-header">
      <h2>我的持仓</h2>
      <button @click="refreshList" class="refresh-btn">
        <span>刷新</span>
      </button>
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
            <th class="col-name">基金名称</th>
            <th class="col-code">代码</th>
            <th class="col-amount">持仓金额</th>
            <th class="col-estimate">估算涨幅</th>
            <th class="col-yesterday">昨日收益</th>
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
            <td class="col-amount">{{ formatNumber(holding.currentValue != null ? holding.currentValue : holding.holdAmount) }}</td>
            <td class="col-estimate">
              <span :class="getProfitClass(holding.estimatedChange)">
                {{ formatPercent(holding.estimatedChange) }}
              </span>
              <span class="estimate-time">{{ formatEstimateTime(holding.valuationTime) }}</span>
            </td>
            <td class="col-yesterday" :class="getProfitClass(holding.yesterdayProfit)">
              {{ holding.yesterdayProfit != null ? formatProfit(holding.yesterdayProfit) : '--' }}
            </td>
            <td class="col-profit" :class="getProfitClass(holding.todayProfit)">
              {{ formatProfit(holding.todayProfit) }}
            </td>
            <td class="col-rate" :class="getProfitClass(holding.profitRate)">
              {{ formatPercent(holding.profitRate) }}
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
import { ref } from 'vue'
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
  background: transparent;
  position: relative;
  padding: 5px 15px;
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
  table-layout: fixed;
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

.table-row {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
  background: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.table-row:hover {
  background: #f0f5ff;
  box-shadow: 0 4px 16px rgba(22, 119, 255, 0.12);
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
  font-weight: 700;
  color: #000000;
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
  width: 95px;
  text-align: right !important;
  font-weight: 500;
  font-size: 14px;
}

.col-profit {
  width: 95px;
  text-align: right !important;
  font-weight: 500;
  font-size: 14px;
}

.col-action {
  width: 80px;
}

.edit-btn {
  position: relative;
  transition: all 0.3s ease-in-out;
  box-shadow: 0px 10px 20px rgba(0, 0, 0, 0.2);
  padding-block: 0.5rem;
  padding-inline: 1.25rem;
  background-color: rgb(0 107 179);
  border-radius: 9999px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #ffff;
  gap: 10px;
  font-weight: bold;
  border: 3px solid #ffffff4d;
  outline: none;
  overflow: hidden;
  font-size: 15px;
}

.edit-btn .icon {
  width: 24px;
  height: 24px;
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