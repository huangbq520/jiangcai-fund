<template>
  <div class="watchlist-table" :class="{ 'is-batch-delete': isBatchDeleteMode }">
    <div class="list-header">
      <h2>{{ groupName }}</h2>
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

    <div v-if="loading" class="loading">
      <svg viewBox="0 0 240 240" height="240" width="240" class="pl">
        <circle stroke-linecap="round" stroke-dashoffset="-330" stroke-dasharray="0 660" stroke-width="20" stroke="#000" fill="none" r="105" cy="120" cx="120" class="pl__ring pl__ring--a"></circle>
        <circle stroke-linecap="round" stroke-dashoffset="-110" stroke-dasharray="0 220" stroke-width="20" stroke="#000" fill="none" r="35" cy="120" cx="120" class="pl__ring pl__ring--b"></circle>
        <circle stroke-linecap="round" stroke-dasharray="0 440" stroke-width="20" stroke="#000" fill="none" r="70" cy="120" cx="85" class="pl__ring pl__ring--c"></circle>
        <circle stroke-linecap="round" stroke-dasharray="0 440" stroke-width="20" stroke="#000" fill="none" r="70" cy="120" cx="155" class="pl__ring pl__ring--d"></circle>
      </svg>
    </div>

    <div v-else-if="groupFundsList.length === 0" class="empty">
      暂无基金，请搜索添加
    </div>

    <div v-else class="table-wrapper">
      <table class="watchlist-table-inner">
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
            <th class="col-estimate-net-value">估算净值</th>
            <!-- <th class="col-action">操作</th> -->
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in groupFundsList"
            :key="item.fundCode"
            class="table-row"
            @click="!isBatchDeleteMode && viewDetail(item.fundCode)"
          >
            <td v-if="isBatchDeleteMode" class="col-checkbox" @click.stop>
              <input
                type="checkbox"
                :checked="selectedFundCodes.has(item.fundCode)"
                @change="toggleSelectFund(item.fundCode)"
                class="checkbox"
              />
            </td>
            <td class="col-name">
              <div class="fund-name">{{ item.fundName || item.fundCode }}</div>
            </td>
            <td class="col-code">{{ item.fundCode }}</td>
            <td class="col-latest-change">
              <div class="change-cell">
                <span :class="getProfitClass(item.yesterdayChange)">
                  {{ formatPercent(item.yesterdayChange, { nullDisplay: '--' }) }}
                </span>
              </div>
            </td>
            <td class="col-latest-net-value">
              <div class="change-cell">
                <span>{{ item.currentNetValue || item.unitNetValue || '--' }}</span>
                <span class="sub-text" v-if="item.latestNetValueDate">{{ formatDate(item.latestNetValueDate) }}</span>
              </div>
            </td>
            <td class="col-estimate">
              <span :class="getProfitClass(item.estimatedChange)">
                {{ formatPercent(item.estimatedChange, { nullDisplay: '--' }) }}
              </span>
              <span class="sub-text" v-if="item.valuationTime">{{ formatTime(item.valuationTime) }}</span>
            </td>
            <td class="col-estimate-net-value">
              {{ item.estimatedNetValue || '--' }}
            </td>
            <!-- <td class="col-action" @click.stop>
              <button @click="deleteFund(item.fundCode)" class="delete-fund-btn" title="删除">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="3 6 5 6 21 6"/>
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                </svg>
              </button>
            </td> -->
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useFundStore } from '../stores/fundStore'
import { storeToRefs } from 'pinia'
import { formatPercent, getProfitClass } from '../composables/useFormat'
import { useAutoRefresh, isTradingHours } from '../composables/useAutoRefresh'

const props = defineProps({
  groupId: {
    type: Number,
    required: true
  }
})

const emit = defineEmits(['update', 'view-detail'])

const fundStore = useFundStore()
const { groupFunds } = storeToRefs(fundStore)

// 当前分组名称
const groupName = computed(() => {
  const g = (fundStore.groups || []).find(g => g.id === props.groupId)
  return g ? g.name : '自选'
})

const loading = ref(false)

// 从 store 缓存获取当前分组的基金列表
const groupFundsList = computed(() => {
  if (!props.groupId) return []
  return groupFunds.value[props.groupId] || []
})

const isBatchDeleteMode = ref(false)
const selectedFundCodes = ref(new Set())

const isAllSelected = computed(() => {
  return groupFundsList.value.length > 0 && selectedFundCodes.value.size === groupFundsList.value.length
})

// 加载数据
const loadData = async () => {
  if (!props.groupId) return
  loading.value = true
  await fundStore.fetchGroupFunds(props.groupId)
  loading.value = false
}

onMounted(() => {
  loadData()
})

// 当 groupId 变化时重新加载
watch(() => props.groupId, () => {
  loadData()
})

// 自动刷新当前激活的分组
useAutoRefresh(async () => {
  if (props.groupId) {
    await fundStore.silentFetchGroupFunds(props.groupId)
  }
}, 30000, isTradingHours)

const refreshList = async () => {
  await loadData()
  emit('update')
}

const viewDetail = (fundCode) => {
  emit('view-detail', fundCode)
}

const deleteFund = async (fundCode) => {
  if (!props.groupId) return
  const confirmed = confirm(`确认从此分组删除基金 ${fundCode}？`)
  if (!confirmed) return
  try {
    const response = await fundStore.deleteBatch([fundCode], props.groupId)
    if (response.code !== 200) {
      alert(response.message || '删除失败')
    }
    await fundStore.fetchGroups()
    await loadData()
  } catch (error) {
    console.error('删除基金失败:', error)
    alert('删除失败: ' + (error.message || '未知错误'))
  }
}

// ========== 格式化 ==========
const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const str = String(dateStr)
  if (str.includes('-')) {
    const parts = str.split('-')
    if (parts.length >= 3) {
      return `${parts[1]}-${parts[2]}`
    }
  }
  return str
}

const formatTime = (timeStr) => {
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

// ========== 批量删除 ==========
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
    selectedFundCodes.value = new Set(groupFundsList.value.map(item => item.fundCode))
  } else {
    selectedFundCodes.value.clear()
  }
}

const handleBatchDelete = async () => {
  if (selectedFundCodes.value.size === 0) {
    alert('请先选择要删除的基金')
    return
  }
  if (!props.groupId) return
  const fundCodesArray = [...selectedFundCodes.value]
  const confirmed = confirm(`确认删除选中 ${fundCodesArray.length} 条基金？`)
  if (!confirmed) return
  try {
    const response = await fundStore.deleteBatch(fundCodesArray, props.groupId)
    if (response.code === 200) {
      cancelBatchDelete()
      await fundStore.fetchGroupFunds(props.groupId)
      await fundStore.fetchGroups()
      alert('删除成功')
    } else {
      alert(response.message || '删除失败')
    }
  } catch (error) {
    console.error('批量删除失败:', error)
    alert('删除失败: ' + (error.message || '未知错误'))
  }
}
</script>

<style scoped>
.watchlist-table {
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
}

/* 头部 */
.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.list-header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
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
  z-index: 1;
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
  /* z-index: -1; */
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
  z-index: 1;
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
  /* z-index: -1; */
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

.watchlist-table-inner {
  width: 100%;
  border-collapse: collapse;
  table-layout: auto;
  min-width: 900px;
}

.watchlist-table-inner th,
.watchlist-table-inner td {
  padding: 14px 10px;
  text-align: center;
  border-bottom: 1px solid #f0f0f0;
}

.watchlist-table-inner th {
  background: #f8fafc;
  font-weight: 700;
  font-size: 13px;
  color: #000000;
  letter-spacing: 0.3px;
}

.watchlist-table-inner th.col-action {
  position: sticky;
  right: 0;
  z-index: 20;
  background-color: #f8fafc;
}

.watchlist-table-inner th.col-name {
  position: sticky;
  left: 0;
  z-index: 20;
  background-color: #f8fafc;
}

.watchlist-table-inner th.col-checkbox {
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

.checkbox {
  width: 18px;
  height: 18px;
  cursor: pointer;
}

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

.col-name {
  text-align: left !important;
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

.col-latest-change,
.col-latest-net-value,
.col-estimate,
.col-estimate-net-value {
  width: 110px;
  font-size: 14px;
}

.change-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.sub-text {
  font-size: 11px;
  color: #999;
  line-height: 1.2;
  display: block;
  margin-top: 2px;
}

.col-action {
  width: 70px;
  position: sticky;
  right: 0;
  z-index: 10;
  background-color: white;
}

.delete-fund-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  background: white;
  color: #999;
  cursor: pointer;
  transition: all 0.25s ease;
}

.delete-fund-btn:hover {
  border-color: #f56c6c;
  color: #f56c6c;
  background: #fff5f5;
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

.pl__ring--a { stroke: #f42f25; }
.pl__ring--b { animation-name: ringB; stroke: #ffdd00; }
.pl__ring--c { animation-name: ringC; stroke: #255ff4; }
.pl__ring--d { animation-name: ringD; stroke: #2cf425; }

@keyframes ringA {
  from, 4% { stroke-dasharray: 0 660; stroke-width: 20; stroke-dashoffset: -330; }
  12% { stroke-dasharray: 60 600; stroke-width: 30; stroke-dashoffset: -335; }
  32% { stroke-dasharray: 60 600; stroke-width: 30; stroke-dashoffset: -595; }
  40%, 54% { stroke-dasharray: 0 660; stroke-width: 20; stroke-dashoffset: -660; }
  62% { stroke-dasharray: 60 600; stroke-width: 30; stroke-dashoffset: -665; }
  82% { stroke-dasharray: 60 600; stroke-width: 30; stroke-dashoffset: -925; }
  90%, to { stroke-dasharray: 0 660; stroke-width: 20; stroke-dashoffset: -990; }
}

@keyframes ringB {
  from, 12% { stroke-dasharray: 0 220; stroke-width: 20; stroke-dashoffset: -110; }
  20% { stroke-dasharray: 20 200; stroke-width: 30; stroke-dashoffset: -115; }
  40% { stroke-dasharray: 20 200; stroke-width: 30; stroke-dashoffset: -195; }
  48%, 62% { stroke-dasharray: 0 220; stroke-width: 20; stroke-dashoffset: -220; }
  70% { stroke-dasharray: 20 200; stroke-width: 30; stroke-dashoffset: -225; }
  90% { stroke-dasharray: 20 200; stroke-width: 30; stroke-dashoffset: -305; }
  98%, to { stroke-dasharray: 0 220; stroke-width: 20; stroke-dashoffset: -330; }
}

@keyframes ringC {
  from { stroke-dasharray: 0 440; stroke-width: 20; stroke-dashoffset: 0; }
  8% { stroke-dasharray: 40 400; stroke-width: 30; stroke-dashoffset: -5; }
  28% { stroke-dasharray: 40 400; stroke-width: 30; stroke-dashoffset: -175; }
  36%, 58% { stroke-dasharray: 0 440; stroke-width: 20; stroke-dashoffset: -220; }
  66% { stroke-dasharray: 40 400; stroke-width: 30; stroke-dashoffset: -225; }
  86% { stroke-dasharray: 40 400; stroke-width: 30; stroke-dashoffset: -395; }
  94%, to { stroke-dasharray: 0 440; stroke-width: 20; stroke-dashoffset: -440; }
}

@keyframes ringD {
  from, 8% { stroke-dasharray: 0 440; stroke-width: 20; stroke-dashoffset: 0; }
  16% { stroke-dasharray: 40 400; stroke-width: 30; stroke-dashoffset: -5; }
  36% { stroke-dasharray: 40 400; stroke-width: 30; stroke-dashoffset: -175; }
  44%, 50% { stroke-dasharray: 0 440; stroke-width: 20; stroke-dashoffset: -220; }
  58% { stroke-dasharray: 40 400; stroke-width: 30; stroke-dashoffset: -225; }
  78% { stroke-dasharray: 40 400; stroke-width: 30; stroke-dashoffset: -395; }
  86%, to { stroke-dasharray: 0 440; stroke-width: 20; stroke-dashoffset: -440; }
}
</style>
