<template>
  <div class="allocation-chart">
    <div v-if="!hasData" class="empty-state">暂无持仓数据</div>
    <div v-else ref="chartRef" class="chart-canvas"></div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useFundStore } from '../stores/fundStore'
import { storeToRefs } from 'pinia'

const emit = defineEmits(['view-detail'])

const echartsLib = window.echarts

const fundStore = useFundStore()
const { holdings } = storeToRefs(fundStore)

const chartRef = ref(null)
let chartInstance = null

const hasData = computed(() => holdings.value && holdings.value.length > 0)

const colors = [
  '#1677ff', '#52c41a', '#faad14', '#f5222d', '#722ed1',
  '#13c2c2', '#eb2f96', '#fa8c16', '#2f54eb', '#10239e'
]

const renderChart = () => {
  if (!chartRef.value || !echartsLib || !hasData.value) return

  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }

  chartInstance = echartsLib.init(chartRef.value)

  const chartData = holdings.value
    .filter(h => {
      const val = h.currentValue || h.holdAmount
      return val && Number(val) > 0
    })
    .map((h, i) => ({
      name: h.fundName || h.fundCode,
      value: Number(h.currentValue || h.holdAmount),
      itemStyle: { color: colors[i % colors.length] }
    }))

  if (chartData.length === 0) return

  const option = {
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(15,23,42,0.92)',
      borderWidth: 0,
      textStyle: { color: '#e2e8f0', fontSize: 13 },
      formatter: (params) => {
        return `<div style="font-weight:600;margin-bottom:4px">${params.name}</div>
          <div>金额: ¥${params.value.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}</div>
          <div>占比: ${params.percent}%</div>`
      }
    },
    legend: {
      orient: 'horizontal',
      bottom: 0,
      textStyle: { color: '#64748b', fontSize: 11 }
    },
    series: [
      {
        type: 'pie',
        radius: ['55%', '80%'],
        center: ['50%', '48%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 4,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: true,
          position: 'outside',
          formatter: '{b}\n{d}%',
          fontSize: 11,
          color: '#475569'
        },
        emphasis: {
          label: { fontSize: 14, fontWeight: 'bold' },
          scaleSize: 8
        },
        data: chartData
      }
    ]
  }

  chartInstance.setOption(option)
  
  // 添加点击事件
  chartInstance.off('click')
  chartInstance.on('click', (params) => {
    const holding = holdings.value.find(h => (h.fundName || h.fundCode) === params.name)
    if (holding) {
      emit('view-detail', holding.fundCode)
    }
  })
}

const handleResize = () => {
  if (chartInstance) chartInstance.resize()
}

watch(holdings, () => {
  setTimeout(renderChart, 100)
}, { deep: true })

onMounted(() => {
  renderChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<style scoped>
.allocation-chart {
  padding: 0 24px;
}

.chart-canvas {
  width: 100%;
  height: 340px;
}

.empty-state {
  text-align: center;
  padding: 60px 0;
  color: #999;
  font-size: 14px;
}
</style>
