<template>
  <div class="market-index">
    <div
      v-for="index in indices"
      :key="index.code"
      class="index-item"
      :class="getIndexClass(index)"
    >
      <div class="index-name">{{ index.name }}</div>
      <div class="index-price">{{ formatPrice(index.price) }}</div>
      <div class="index-change">
        <span class="change-value">{{ formatChange(index.change) }}</span>
        <span class="change-percent">{{ formatPercent(index.changePercent) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useMarketStore } from '../stores/marketStore'
import { storeToRefs } from 'pinia'
import { useAutoRefresh, isTradingHours } from '../composables/useAutoRefresh'

const marketStore = useMarketStore()
const { indices } = storeToRefs(marketStore)

useAutoRefresh(() => marketStore.fetchIndices(), 30000, isTradingHours)

const formatPrice = (price) => {
  if (!price) return '--'
  return parseFloat(price).toFixed(2)
}

const formatChange = (change) => {
  if (!change && change !== 0) return '--'
  const val = parseFloat(change)
  return val >= 0 ? `+${val.toFixed(2)}` : val.toFixed(2)
}

const formatPercent = (percent) => {
  if (!percent && percent !== 0) return '--'
  const val = parseFloat(percent)
  return val >= 0 ? `+${val.toFixed(2)}%` : `${val.toFixed(2)}%`
}

const getIndexClass = (index) => {
  if (!index.change && index.change !== 0) return ''
  return parseFloat(index.change) >= 0 ? 'up' : 'down'
}

</script>

<style scoped>
.market-index {
  display: flex;
  gap: 16px;
  background: white;
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  margin-bottom: 16px;
  overflow-x: auto;
}

.index-item {
  flex: 1;
  min-width: 140px;
  padding: 12px 16px;
  border-radius: 8px;
  background: #f8f9fa;
  transition: all 0.3s;
}

.index-item.up {
  background: linear-gradient(135deg, #fff5f5 0%, #ffe8e8 100%);
}

.index-item.down {
  background: linear-gradient(135deg, #f0fff0 0%, #e8ffe8 100%);
}

.index-name {
  font-size: 13px;
  color: #666;
  margin-bottom: 6px;
  font-weight: 500;
}

.index-price {
  font-size: 20px;
  font-weight: 700;
  color: #333;
  margin-bottom: 4px;
}

.index-change {
  display: flex;
  gap: 8px;
  font-size: 13px;
}

.change-value {
  font-weight: 500;
}

.change-percent {
  font-weight: 500;
}

.index-item.up .change-value,
.index-item.up .change-percent {
  color: #e53935;
}

.index-item.down .change-value,
.index-item.down .change-percent {
  color: #009e5f;
}
</style>