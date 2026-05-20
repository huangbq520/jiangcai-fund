<template>
  <div class="fund-trend-chart">
    <div class="chart-header">
      <div class="period-tabs">
        <button
            v-for="period in periods"
            :key="period.value"
            :class="['period-tab', { active: currentPeriod === period.value }]"
            @click="handlePeriodChange(period.value)"
        >
          {{ period.label }}
        </button>
      </div>
      <div v-if="currentData" class="period-summary" :class="returnClass">
        <span class="summary-value">{{ formattedReturn }}</span>
        <span class="summary-label">{{ currentPeriodLabel }}</span>
      </div>
    </div>

    <div v-if="isLoading && !currentData" class="chart-loading">
      <svg viewBox="0 0 240 240" height="240" width="240" class="pl">
        <circle stroke-linecap="round" stroke-dashoffset="-330" stroke-dasharray="0 660" stroke-width="20" stroke="#000" fill="none" r="105" cy="120" cx="120" class="pl__ring pl__ring--a"></circle>
        <circle stroke-linecap="round" stroke-dashoffset="-110" stroke-dasharray="0 220" stroke-width="20" stroke="#000" fill="none" r="35" cy="120" cx="120" class="pl__ring pl__ring--b"></circle>
        <circle stroke-linecap="round" stroke-dasharray="0 440" stroke-width="20" stroke="#000" fill="none" r="70" cy="120" cx="85" class="pl__ring pl__ring--c"></circle>
        <circle stroke-linecap="round" stroke-dasharray="0 440" stroke-width="20" stroke="#000" fill="none" r="70" cy="120" cx="155" class="pl__ring pl__ring--d"></circle>
      </svg>
    </div>

    <div v-else-if="error && !currentData" class="chart-error">
      <span class="error-icon">!</span>
      <span>{{ error }}</span>
    </div>

    <div v-else class="chart-container">
      <div ref="chartRef" class="chart-canvas"></div>
      <div class="legend-panel">
        <button
            v-for="series in visibleLegend"
            :key="series.key"
            :class="['legend-item', { muted: !series.active }]"
            @click="toggleSeries(series.key)"
        >
          <span class="legend-line" :style="{ backgroundColor: series.color }"></span>
          <span class="legend-text">{{ series.name }}</span>
        </button>
      </div>

      <div v-if="currentData?.netWorthTrend?.length" class="nav-history-mini">
        <div class="mini-header">
          <span class="mini-title">净值历史</span>
          <span class="mini-subtitle">最近5条</span>
        </div>
        <table class="mini-table">
          <thead>
            <tr>
              <th>日期</th>
              <th>净值</th>
              <th>日涨幅</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in recentNavList" :key="item.date">
              <td class="col-date">{{ formatDate(item.date) }}</td>
              <td class="col-value">{{ item.netValue != null ? item.netValue.toFixed(4) : '--' }}</td>
              <td :class="['col-change', item.change >= 0 ? 'positive' : 'negative']">
                {{ item.change != null ? (item.change >= 0 ? '+' : '') + item.change.toFixed(2) + '%' : '--' }}
              </td>
            </tr>
          </tbody>
        </table>
        <button class="load-more-btn" @click="$emit('load-more', [...currentData.netWorthTrend].reverse())">
          加载更多历史数据
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick, reactive } from 'vue'
import { fundApi } from '../api'

const echartsLib = window.echarts

const props = defineProps({
  fundCode: { type: String, required: true }
})

const emit = defineEmits(['load-more'])

const chartRef = ref(null)
const chartInstance = ref(null)
const currentPeriod = ref('6month')
const dataCache = reactive(new Map())
const currentData = ref(null)
const isLoading = ref(true)
const error = ref(null)
const seriesActive = reactive({ fund: true, szzs: true, tysm: true })
let hideTimer = null
let datesRef = ref([])

const periods = [
  { label: '近1月', value: '1month' },
  { label: '近3月', value: '3month' },
  { label: '近6月', value: '6month' },
  { label: '近1年', value: '1year' },
  { label: '近3年', value: '3year' },
  { label: '成立来', value: 'all' }
]

// 只有短期走势才显示沪深300和同类平均基准线
const periodsWithBenchmark = ['1month', '3month', '6month']

const colors = {
  rise: '#e53935',
  fall: '#009e5f',
  szzs: '#f97316',
  tysm: '#8b5cf6',
  grid: '#f1f5f9',
  crosshair: '#94a3b8'
}

const currentPeriodLabel = computed(() => {
  const period = periods.find(p => p.value === currentPeriod.value)
  return period ? period.label : ''
})

const formattedReturn = computed(() => {
  if (!currentData.value || currentData.value.periodReturn == null) return '--'
  const value = currentData.value.periodReturn
  return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`
})

const recentNavList = computed(() => {
  const trend = currentData.value?.netWorthTrend
  if (!trend?.length) return []
  const reversed = [...trend].reverse()
  const latest = reversed.slice(0, 5)
  return latest.map((item, i) => {
    const prev = reversed[i + 1]
    let change = null
    if (item.netValue != null && prev?.netValue != null && prev.netValue !== 0) {
      change = (item.netValue - prev.netValue) / prev.netValue * 100
    }
    return { date: item.date, netValue: item.netValue, change }
  })
})

const returnClass = computed(() => {
  if (!currentData.value || currentData.value.periodReturn == null) return ''
  return currentData.value.periodReturn >= 0 ? 'rise' : 'fall'
})

// 根据当前周期决定是否显示基准图例项
const visibleLegend = computed(() => {
  const legends = []
  const showBenchmark = periodsWithBenchmark.includes(currentPeriod.value)

  if (showBenchmark) {
    if (currentData.value?.compareIndices?.some(i => i.szzs != null)) {
      legends.push({ key: 'szzs', name: '沪深300', color: colors.szzs, active: seriesActive.szzs })
    }
    if (currentData.value?.compareIndices?.some(i => i.tysm != null)) {
      legends.push({ key: 'tysm', name: '同类平均', color: colors.tysm, active: seriesActive.tysm })
    }
  }
  legends.push({
    key: 'fund',
    name: '本基金',
    color: currentData.value ? getTrendColor(currentData.value) : colors.rise,
    active: seriesActive.fund
  })
  return legends
})

const getTrendColor = (data) => {
  if (!data?.netWorthTrend?.length) return colors.rise
  const valid = data.netWorthTrend.filter(item => item.netValue != null)
  if (valid.length < 2) return colors.rise
  return valid[valid.length - 1].netValue >= valid[0].netValue ? colors.rise : colors.fall
}

const toggleSeries = (key) => {
  seriesActive[key] = !seriesActive[key]
  renderChart()
}

const formatDate = (timestamp) => {
  if (!timestamp) return ''
  if (typeof timestamp === 'string') {
    if (timestamp.includes('-')) return timestamp
    const n = parseInt(timestamp)
    if (isNaN(n)) return ''
    const d = new Date(n)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  }
  if (typeof timestamp === 'number') {
    const d = new Date(timestamp)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  }
  return ''
}

// 双向填充缺失值，避免断点出现水平直线
const fillMissingValues = (values) => {
  let lastValid = null
  const forwardFilled = values.map(v => {
    if (v !== null && v !== undefined && isFinite(v)) {
      lastValid = v
      return v
    }
    return lastValid
  })

  let nextValid = null
  const backwardFilled = [...forwardFilled].reverse().map(v => {
    if (v !== null && v !== undefined && isFinite(v)) {
      nextValid = v
      return v
    }
    return nextValid
  }).reverse()

  return backwardFilled
}

const preloadAllPeriods = async () => {
  await Promise.all(periods.map(async (period) => {
    if (!dataCache.has(period.value)) {
      try {
        const res = await fundApi.getPerformanceData(props.fundCode, period.value)
        if (res.code === 200) dataCache.set(period.value, res.data)
      } catch {}
    }
  }))
}

const loadData = async (period, useCache = true) => {
  if (!props.fundCode) return

  if (useCache && dataCache.has(period)) {
    currentData.value = dataCache.get(period)
    isLoading.value = false
    await nextTick()
    renderChart()
    silentUpdateCache(period)
    return
  }

  isLoading.value = true
  error.value = null

  try {
    const res = await fundApi.getPerformanceData(props.fundCode, period)
    if (res.code === 200) {
      currentData.value = res.data
      dataCache.set(period, res.data)
    } else {
      error.value = res.message || '加载失败'
    }
  } catch {
    error.value = '网络错误，请重试'
  } finally {
    isLoading.value = false
    await nextTick()
    renderChart()
  }
}

const silentUpdateCache = async (currentPeriodValue) => {
  for (const period of periods) {
    if (period.value === currentPeriodValue || dataCache.has(period.value)) continue
    try {
      const res = await fundApi.getPerformanceData(props.fundCode, period.value)
      if (res.code === 200) dataCache.set(period.value, res.data)
    } catch {}
  }
}

const handlePeriodChange = (period) => {
  if (currentPeriod.value === period) return
  currentPeriod.value = period
  loadData(period, true)
}

// 十字线隐藏逻辑
const hideCrosshair = () => {
  if (!chartInstance.value) return
  chartInstance.value.dispatchAction({ type: 'hideTip' })
  chartInstance.value.dispatchAction({ type: 'downplay' })
}

const resetHideTimer = () => {
  clearTimeout(hideTimer)
  hideTimer = setTimeout(hideCrosshair, 2000)
}

// 构建tooltip显示项
const buildTooltipParams = (data, idx, szzsData, tysmData) => {
  const baseValue = data.netWorthTrend.find(item => item.netValue != null)?.netValue || 1
  const dates = datesRef.value
  const dateStr = dates[idx] || ''
  const params = [{ dateStr }]

  const fundNetVal = data.netWorthTrend[idx]?.netValue
  if (fundNetVal != null) {
    const pct = ((fundNetVal - baseValue) / baseValue * 100)
    params.push({ series: '本基金', value: pct, color: pct >= 0 ? colors.rise : colors.fall })
  }

  if (seriesActive.szzs && szzsData && szzsData[idx] != null) {
    params.push({ series: '沪深300', value: szzsData[idx], color: colors.szzs })
  }

  if (seriesActive.tysm && tysmData && tysmData[idx] != null) {
    params.push({ series: '同类平均', value: tysmData[idx], color: colors.tysm })
  }

  return params
}

const renderChart = () => {
  if (!chartRef.value || !echartsLib) return

  const data = currentData.value
  if (!data?.netWorthTrend?.length) {
    if (!error.value) error.value = '暂无走势数据'
    return
  }

  if (chartInstance.value) {
    chartInstance.value.dispose()
    chartInstance.value = null
  }

  chartInstance.value = echartsLib.init(chartRef.value)

  const baseValue = data.netWorthTrend.find(item => item.netValue != null)?.netValue || 1
  const dates = data.netWorthTrend.map(item => formatDate(item.date))
  datesRef.value = dates

  // 主基金收益率（以第一个有效净值为0%基准）
  const fundValues = data.netWorthTrend.map(item => {
    if (item.netValue == null) return null
    return ((item.netValue - baseValue) / baseValue * 100)
  })

  const trendColor = getTrendColor(data)
  const showBenchmark = periodsWithBenchmark.includes(currentPeriod.value)

  // 仅在允许基准的周期计算沪深300和同类平均数据
  let szzsNormalized = null
  let tysmNormalized = null

  if (showBenchmark) {
    const compareIndices = Array.isArray(data.compareIndices) ? [...data.compareIndices] : []
    // 补全对比数据长度
    while (compareIndices.length < data.netWorthTrend.length) {
      compareIndices.push({ szzs: null, tysm: null })
    }

    const szzsValues = data.netWorthTrend.map((_, i) => {
      const comp = compareIndices[i]
      return comp?.szzs ?? null
    })
    const tysmValues = data.netWorthTrend.map((_, i) => {
      const comp = compareIndices[i]
      return comp?.tysm ?? null
    })

    const szzsFilled = fillMissingValues(szzsValues)
    const tysmFilled = fillMissingValues(tysmValues)

    const szzsBase = szzsFilled.find(v => v != null) || 0
    const tysmBase = tysmFilled.find(v => v != null) || 0
    szzsNormalized = szzsFilled.map(v => v != null ? (v - szzsBase) : null)
    tysmNormalized = tysmFilled.map(v => v != null ? (v - tysmBase) : null)
  }

  // Y轴自适应范围
  const allVals = [
    ...fundValues.filter(v => v != null),
    ...(szzsNormalized || []).filter(v => v != null),
    ...(tysmNormalized || []).filter(v => v != null)
  ]
  if (allVals.length === 0) return

  const yMin = Math.min(...allVals)
  const yMax = Math.max(...allVals)
  const yPad = Math.max((yMax - yMin) * 0.15, 1)

  // ========== 构建 series 数组 ==========
  const series = []

  // 本基金曲线
  if (seriesActive.fund) {
    series.push({
      type: 'line',
      name: '本基金',
      data: fundValues,
      smooth: 0.4,
      symbol: 'none',
      lineStyle: { width: 2.5, color: trendColor },
      areaStyle: {
        color: new echartsLib.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: `${trendColor}40` },
          { offset: 1, color: `${trendColor}05` }
        ])
      }
    })
    series.push({
       type: 'scatter',
       name: '本基金高亮',
       data: [],
       symbol: 'circle',
       symbolSize: 10,
       itemStyle: {
         color: trendColor,
         borderColor: '#ffffff',
         borderWidth: 2,
         shadowBlur: 12,
         shadowColor: trendColor
       },
       z: 10
     })
  }

  // 沪深300曲线（仅短期+开关开启+数据有效）
  if (showBenchmark && seriesActive.szzs && szzsNormalized?.some(v => v != null)) {
    series.push({
      type: 'line',
      name: '沪深300',
      data: szzsNormalized,
      smooth: 0.4,
      symbol: 'none',
      lineStyle: { width: 1.2, color: colors.szzs },
      emphasis: {
        focus: 'series',
        symbol: 'circle',
        symbolSize: 8,
        scale: 1.5,
        itemStyle: { borderColor: '#fff', borderWidth: 2 }
      }
    })
  }

  // 同类平均曲线
  if (showBenchmark && seriesActive.tysm && tysmNormalized?.some(v => v != null)) {
    series.push({
      type: 'line',
      name: '同类平均',
      data: tysmNormalized,
      smooth: 0.4,
      symbol: 'none',
      lineStyle: { width: 1.2, color: colors.tysm },
      emphasis: {
        focus: 'series',
        symbol: 'circle',
        symbolSize: 8,
        scale: 1.5,
        itemStyle: { borderColor: '#fff', borderWidth: 2 }
      }
    })
  }

  // ========== ECharts 配置 ==========
  const option = {
    animation: true,
    animationDuration: 800,
    animationEasing: 'cubicOut',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'transparent',
      borderWidth: 0,
      padding: 0,
      extraCssText: 'pointer-events: none; box-shadow: none;',
      show: true,
      axisPointer: {
        type: 'cross',
        snap: true,
        crossStyle: {
          color: colors.crosshair,
          width: 1,
          type: 'dashed'
        },
        label: {
          show: true,
          fontSize: 10,
          color: '#e2e8f0',
          backgroundColor: '#0f172a',
          padding: [2, 6],
          formatter: (params) => {
            if (params.axisDimension === 'y') {
              const val = params.value
              return `${val >= 0 ? '+' : ''}${val.toFixed(2)}%`
            } else {
              return params.value
            }
          }
        }
      },
      formatter: (params) => {
        if (!params || params.length === 0) return ''
        const mainFundParam = params.find(p => p.seriesName === '本基金')
        if (!mainFundParam) return ''
        const idx = mainFundParam.dataIndex
        const tooltipData = buildTooltipParams(data, idx, szzsNormalized, tysmNormalized)
        if (tooltipData.length <= 1) return ''

        const dateStr = tooltipData[0].dateStr
        let html = `<div style="background:rgba(15,23,42,0.92);border-radius:8px;padding:10px 14px;line-height:1.8;white-space:nowrap;">`
        html += `<div style="font-weight:600;margin-bottom:6px;color:#e2e8f0;">${dateStr}</div>`

        for (let i = 1; i < tooltipData.length; i++) {
          const item = tooltipData[i]
          const sign = item.value >= 0 ? '+' : ''
          html += `<div style="display:flex;align-items:center;gap:8px;margin:2px 0;">
            <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${item.color};flex-shrink:0;"></span>
            <span style="color:#94a3b8;font-size:12px;flex:1;">${item.series}</span>
            <span style="color:${item.color};font-weight:600;font-size:12px;">${sign}${item.value.toFixed(2)}%</span>
          </div>`
        }
        html += `</div>`
        return html
      },
      position: (point, params, dom, rect, size) => {
        const x = point[0], y = point[1]
        const contentWidth = 160, contentHeight = 90
        const chartWidth = size.chartWidth, chartHeight = size.chartHeight
        let left = x + 15
        let top = y - contentHeight / 2
        if (left + contentWidth > chartWidth - 10) left = x - contentWidth - 15
        if (top < 10) top = 10
        if (top + contentHeight > chartHeight - 10) top = chartHeight - contentHeight - 10
        return [left, top]
      }
    },
    grid: {
      left: '3%', right: '4%', bottom: '10%', top: '8%', containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLine: { lineStyle: { color: colors.grid, width: 1 } },
      axisTick: { show: false },
      axisLabel: {
        color: '#64748b',
        fontSize: 10,
        interval: Math.floor(dates.length / 6),
        formatter: (val) => val.slice(5)
      },
      splitLine: { show: false }
    },
    yAxis: {
      type: 'value',
      min: yMin - yPad,
      max: yMax + yPad,
      scale: true,
      splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed', width: 1 } },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: '#64748b',
        fontSize: 10,
        formatter: (val) => `${val >= 0 ? '+' : ''}${val.toFixed(1)}%`
      }
    },
    series
  }

  chartInstance.value.setOption(option, true)

  const fundSeriesIndex = 0
  const highlightSeriesIndex = 1

  chartInstance.value.getZr().on('mousemove', (params) => {
    if (!chartInstance.value || !data) return
    clearTimeout(hideTimer)
    const point = [params.offsetX, params.offsetY]
    const option = chartInstance.value.getOption()
    const xAxisData = option.xAxis[0].data
    const fundData = option.series[fundSeriesIndex]?.data
    if (!fundData || !xAxisData) return

    const nearestIdx = findNearestFundPoint(point, xAxisData, fundData, chartInstance.value)
    if (nearestIdx !== -1) {
      chartInstance.value.dispatchAction({ type: 'showTip', seriesIndex: fundSeriesIndex, dataIndex: nearestIdx })
      chartInstance.value.setOption({
        series: [{
          name: '本基金高亮',
          data: [[nearestIdx, fundData[nearestIdx]]]
        }]
      })
    }
  })

  chartInstance.value.getZr().on('mouseout', () => {
    hideTimer = setTimeout(() => {
      if (chartInstance.value) {
        chartInstance.value.dispatchAction({ type: 'hideTip' })
        chartInstance.value.setOption({
          series: [{
            name: '本基金高亮',
            data: []
          }]
        })
      }
    }, 2000)
  })
}

const findNearestFundPoint = (point, xAxisData, fundData, chartInstance) => {
  const xAxis = chartInstance.convertFromPixel({ xAxisIndex: 0 }, point[0])
  if (xAxis == null || isNaN(xAxis)) return -1
  const idx = Math.round(xAxis)
  if (idx < 0 || idx >= xAxisData.length) return -1
  if (fundData[idx] == null) {
    let searchIdx = idx
    while (searchIdx >= 0 && fundData[searchIdx] == null) searchIdx--
    if (searchIdx < 0) {
      searchIdx = idx
      while (searchIdx < fundData.length && fundData[searchIdx] == null) searchIdx++
    }
    if (searchIdx >= fundData.length) return -1
    return searchIdx
  }
  return idx
}

const handleResize = () => {
  if (chartInstance.value) chartInstance.value.resize()
}

watch(() => props.fundCode, (newCode) => {
  if (!newCode) return
  dataCache.clear()
  isLoading.value = true
  currentData.value = null
  error.value = null
  if (chartInstance.value) {
    chartInstance.value.dispose()
    chartInstance.value = null
  }
  loadData(currentPeriod.value, false)
  preloadAllPeriods()
})

onMounted(() => {
  if (props.fundCode) {
    loadData(currentPeriod.value, false)
    preloadAllPeriods()
  }
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  clearTimeout(hideTimer)
  if (chartInstance.value) {
    chartInstance.value.dispose()
    chartInstance.value = null
  }
})
</script>

<style scoped>
.fund-trend-chart {
  width: 100%;
  background: linear-gradient(180deg, #ffffff 0%, #fafbfc 100%);
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.period-tabs {
  display: flex;
  gap: 6px;
  background: #f1f5f9;
  padding: 4px;
  border-radius: 10px;
}

.period-tab {
  padding: 6px 14px;
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.period-tab:hover {
  color: #334155;
  background: rgba(255, 255, 255, 0.6);
}

.period-tab.active {
  background: #ffffff;
  color: #1e293b;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.period-summary {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.summary-value {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.5px;
}

.summary-label {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
}

.period-summary.rise .summary-value { color: #e53935; }
.period-summary.fall .summary-value { color: #009e5f; }

.chart-container { position: relative; }

.chart-canvas {
  width: 100%;
  height: 320px;
  cursor: crosshair;
}

.chart-loading,
.chart-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 320px;
  color: #94a3b8;
  font-size: 13px;
  gap: 14px;
}

.loading-ring {
  width: 36px;
  height: 36px;
  border: 3px solid #e2e8f0;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.error-icon {
  width: 36px;
  height: 36px;
  background: #fef2f2;
  color: #e53935;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
}

.legend-panel {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.legend-item:hover { background: #f8fafc; }
.legend-item.muted { opacity: 0.4; }

.legend-line { width: 20px; height: 3px; border-radius: 2px; }

.legend-text { font-size: 12px; color: #475569; font-weight: 500; }

/* ===== 净值历史小列表 ===== */
.nav-history-mini {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #f1f5f9;
}

.mini-header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 10px;
}

.mini-title {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
}

.mini-subtitle {
  font-size: 11px;
  color: #94a3b8;
}

.mini-table {
  width: 100%;
  border-collapse: collapse;
}

.mini-table th {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 500;
  text-align: left;
  padding: 6px 8px;
  border-bottom: 1px solid #f1f5f9;
}

.mini-table td {
  font-size: 13px;
  padding: 7px 8px;
  border-bottom: 1px solid #f8fafc;
  color: #475569;
}

.col-date { color: #64748b !important; }
.col-value { font-weight: 500; font-variant-numeric: tabular-nums; }
.col-change { font-weight: 500; text-align: right !important; }
.col-change.positive { color: #e53935; }
.col-change.negative { color: #009e5f; }

.load-more-btn {
  display: block;
  width: 100%;
  margin-top: 10px;
  padding: 10px 0;
  border: 1px dashed #d0d5dd;
  border-radius: 8px;
  background: #fafbfc;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.load-more-btn:hover {
  border-color: #1677ff;
  color: #1677ff;
  background: #f0f5ff;
}

.chart-loading {
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