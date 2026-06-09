<template>
  <div v-if="visible" class="quote-overlay" @click.self="emit('close')">
    <div class="quote-panel">
      <div class="quote-header">
        <div class="header-left">
          <h2>{{ indexName }}</h2>
          <span class="header-code">{{ indexCode }}</span>
        </div>
        <button class="close-btn" @click="emit('close')">&times;</button>
      </div>

      <div v-if="loading" class="quote-loading">
        <svg viewBox="0 0 240 240" height="240" width="240" class="pl">
          <circle stroke-linecap="round" stroke-dashoffset="-330" stroke-dasharray="0 660" stroke-width="20" stroke="#000" fill="none" r="105" cy="120" cx="120" class="pl__ring pl__ring--a"></circle>
          <circle stroke-linecap="round" stroke-dashoffset="-110" stroke-dasharray="0 220" stroke-width="20" stroke="#000" fill="none" r="35" cy="120" cx="120" class="pl__ring pl__ring--b"></circle>
          <circle stroke-linecap="round" stroke-dasharray="0 440" stroke-width="20" stroke="#000" fill="none" r="70" cy="120" cx="85" class="pl__ring pl__ring--c"></circle>
          <circle stroke-linecap="round" stroke-dasharray="0 440" stroke-width="20" stroke="#000" fill="none" r="70" cy="120" cx="155" class="pl__ring pl__ring--d"></circle>
        </svg>
        <span>加载中...</span>
      </div>

      <div v-else-if="error" class="quote-error">
        <span class="error-icon">!</span>
        <span>{{ error }}</span>
      </div>

      <template v-else>
        <div class="summary-section">
          <div class="price-row">
            <span class="current-price" :class="trendClass">{{ formatPrice(displayData?.close) }}</span>
            <span class="price-change" :class="trendClass">
              {{ formatChange(displayData?.change) }} &nbsp; {{ formatPercent(displayData?.changePercent) }}
            </span>
          </div>
          <div class="metrics-grid">
            <div class="metric-item">
              <span class="metric-label">开盘价</span>
              <span class="metric-value">{{ formatPrice(displayData?.open) }}</span>
            </div>
            <div class="metric-item">
              <span class="metric-label">最高价</span>
              <span class="metric-value up">{{ formatPrice(displayData?.high) }}</span>
            </div>
            <div class="metric-item">
              <span class="metric-label">最低价</span>
              <span class="metric-value down">{{ formatPrice(displayData?.low) }}</span>
            </div>
            <div class="metric-item">
              <span class="metric-label">成交量</span>
              <span class="metric-value">{{ formatVolume(displayData?.volume) }}</span>
            </div>
            <div class="metric-item">
              <span class="metric-label">成交额</span>
              <span class="metric-value">{{ formatAmount(displayData?.amount) }}</span>
            </div>
            <div class="metric-item">
              <span class="metric-label">振幅</span>
              <span class="metric-value">{{ formatPercent(displayData?.amplitude) }}</span>
            </div>
          </div>
          <div class="ma-row">
            <span class="ma-tag">均线</span>
            <span class="ma-tag ma5">M5 {{ displayMA.ma5 != null ? displayMA.ma5.toFixed(2) : '--' }}</span>
            <span class="ma-tag ma10">M10 {{ displayMA.ma10 != null ? displayMA.ma10.toFixed(2) : '--' }}</span>
            <span class="ma-tag ma20">M20 {{ displayMA.ma20 != null ? displayMA.ma20.toFixed(2) : '--' }}</span>
          </div>
        </div>

        <div class="period-bar">
          <button
            v-for="p in periodGroups"
            :key="p.value"
            :class="['period-btn', { active: currentKlt === p.value }]"
            @click="switchPeriod(p.value)"
          >
            {{ p.label }}
          </button>
        </div>

        <div class="chart-area">
          <div ref="mainChartRef" class="main-chart"></div>
          <div ref="volChartRef" class="vol-chart"></div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { marketApi } from '../api'

const props = defineProps({
  indexCode: { type: String, required: true },
  indexName: { type: String, required: true },
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['close'])

const mainChartRef = ref(null)
const volChartRef = ref(null)
let mainChart = null
let volChart = null

const loading = ref(false)
const error = ref('')
const klineData = ref([])
let allKlineData = []
const currentKlt = ref('101')
const hoveredIdx = ref(-1)
const ma5Values = ref([])
const ma10Values = ref([])
const ma20Values = ref([])

const displayData = computed(() => {
  if (!klineData.value.length) return null
  const idx = hoveredIdx.value >= 0 && hoveredIdx.value < klineData.value.length
    ? hoveredIdx.value : klineData.value.length - 1
  return klineData.value[idx]
})

const displayMA = computed(() => {
  const idx = hoveredIdx.value >= 0 && hoveredIdx.value < klineData.value.length
    ? hoveredIdx.value : klineData.value.length - 1
  return {
    ma5: ma5Values.value[idx] ?? null,
    ma10: ma10Values.value[idx] ?? null,
    ma20: ma20Values.value[idx] ?? null
  }
})

const periodGroups = [
  { label: '1分', value: '1' },
  { label: '5分', value: '5' },
  { label: '15分', value: '15' },
  { label: '30分', value: '30' },
  { label: '60分', value: '60' },
  { label: '日K', value: '101' },
  { label: '周K', value: '102' },
  { label: '月K', value: '103' },
  { label: '季K', value: '104' },
  { label: '年K', value: '105' }
]

const trendClass = computed(() => {
  const d = displayData.value
  if (!d) return ''
  return Number(d.change) >= 0 ? 'up' : 'down'
})

const formatPrice = (val) => {
  if (val == null) return '--'
  return Number(val).toFixed(2)
}

const formatChange = (val) => {
  if (val == null) return '--'
  const n = Number(val)
  return n >= 0 ? `+${n.toFixed(2)}` : n.toFixed(2)
}

const formatPercent = (val) => {
  if (val == null) return '--'
  const n = Number(val)
  return n >= 0 ? `+${n.toFixed(2)}%` : `${n.toFixed(2)}%`
}

const formatVolume = (val) => {
  if (val == null) return '--'
  const n = Number(val)
  if (n >= 1e8) return (n / 1e8).toFixed(2) + '亿手'
  if (n >= 1e4) return (n / 1e4).toFixed(2) + '万手'
  return n.toLocaleString() + '手'
}

const formatAmount = (val) => {
  if (val == null) return '--'
  const n = Number(val)
  if (n >= 1e12) return (n / 1e12).toFixed(2) + '万亿元'
  if (n >= 1e8) return (n / 1e8).toFixed(2) + '亿元'
  if (n >= 1e4) return (n / 1e4).toFixed(2) + '万元'
  return n.toLocaleString() + '元'
}

const getDateRange = (klt) => {
  const now = new Date()
  const end = toDateStr(now)
  let start
  switch (klt) {
    case '1':
    case '5':
    case '15':
    case '30':
    case '60':
      start = new Date(now)
      start.setDate(start.getDate() - 5)
      break
    case '101':
      start = new Date(now)
      start.setMonth(start.getMonth() - 6)
      break
    case '102':
      start = new Date(now)
      start.setFullYear(start.getFullYear() - 2)
      break
    case '103':
      start = new Date(now)
      start.setFullYear(start.getFullYear() - 7)
      break
    case '104':
    case '105':
      start = new Date(now)
      start.setFullYear(start.getFullYear() - 5)
      break
    default:
      start = new Date(now)
      start.setMonth(start.getMonth() - 6)
  }
  return { start: toDateStr(start), end }
}

const toDateStr = (d) => {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}${m}${day}`
}

const calcMA = (data, period) => {
  const result = []
  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) {
      result.push(null)
    } else {
      let sum = 0
      for (let j = i - period + 1; j <= i; j++) sum += Number(data[j].close)
      result.push(+(sum / period).toFixed(2))
    }
  }
  return result
}

const fetchKline = async (klt) => {
  const { start, end } = getDateRange(klt)
  loading.value = true
  error.value = ''
  hoveredIdx.value = -1

  try {
    const res = await marketApi.getKline(props.indexCode, start, end, klt)
    loading.value = false
    if (res.code === 200 && res.data?.klines && res.data.klines.length > 0) {
      allKlineData = res.data.klines
      klineData.value = allKlineData.slice(-50)
      await nextTick()
      try {
        renderCharts()
      } catch (chartErr) {
        console.error('K线图表渲染失败:', chartErr)
      }
    } else {
      error.value = res.message || '暂无K线数据'
    }
  } catch (e) {
    console.error('获取K线数据失败:', e)
    loading.value = false
    error.value = '网络错误，请重试'
  }
}

const switchPeriod = (klt) => {
  if (currentKlt.value === klt) return
  currentKlt.value = klt
  fetchKline(klt)
}

// ---- Chart rendering ----
const colors = {
  up: '#e53935',
  down: '#009e5f',
  upBorder: '#c62828',
  downBorder: '#007a4d',
  upBg: '#fde8e8',
  downBg: '#e8f8e8'
}

const renderCharts = () => {
  const echartsLib = window.echarts
  if (!mainChartRef.value || !volChartRef.value || !echartsLib) {
    if (!echartsLib) console.warn('ECharts 未加载')
    return
  }
  if (!klineData.value.length) return

  disposeCharts()

  mainChart = echartsLib.init(mainChartRef.value)
  volChart = echartsLib.init(volChartRef.value)

  const dates = klineData.value.map(d => d.date)
  const ohlc = klineData.value.map(d => [d.open, d.close, d.low, d.high])
  const volumes = klineData.value.map(d => {
    const up = Number(d.close) >= Number(d.open)
    return {
      value: Number(d.volume),
      itemStyle: { color: up ? colors.up : colors.down }
    }
  })

  // 移动平均线：从全量数据计算后截取展示部分
  const displayCount = klineData.value.length
  ma5Values.value = calcMA(allKlineData, 5).slice(-displayCount)
  ma10Values.value = calcMA(allKlineData, 10).slice(-displayCount)
  ma20Values.value = calcMA(allKlineData, 20).slice(-displayCount)

  // Y轴范围（含MA线数据）
  const allPrices = klineData.value.flatMap(d => [Number(d.high), Number(d.low)])
  const maAll = [...ma5Values.value, ...ma10Values.value, ...ma20Values.value].filter(v => v != null)
  const yMin = Math.min(...allPrices, ...maAll)
  const yMax = Math.max(...allPrices, ...maAll)
  const yPad = (yMax - yMin) * 0.05

  const minSpanPct = Math.min(100, Math.ceil(20 / dates.length * 100))

  // 透明辅助 bar：每个 X 位置一个全高柱，让 chart.on('mousemove') 在任意位置触发
  const barTop = yMax + yPad
  const hitBars = klineData.value.map(() => barTop)

  const mainOption = {
    grid: { left: '8%', right: '2%', top: '3%', bottom: '12%' },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisTick: { show: false },
      axisLabel: { color: '#94a3b8', fontSize: 10, interval: Math.max(Math.floor(dates.length / 8), 0) },
      splitLine: { show: false }
    },
    yAxis: {
      type: 'value',
      min: yMin - yPad,
      max: yMax + yPad,
      scale: true,
      splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } },
      axisLabel: { color: '#94a3b8', fontSize: 10, formatter: v => v.toFixed(0) }
    },
    dataZoom: [
      { type: 'inside', start: 0, end: 100, minSpan: minSpanPct },
      { type: 'slider', start: 0, end: 100, bottom: '2%', height: 18,
        borderColor: '#e2e8f0', fillerColor: 'rgba(22,119,255,0.1)',
        handleStyle: { color: '#1677ff' }, textStyle: { fontSize: 10, color: '#94a3b8' },
        minSpan: minSpanPct }
    ],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      backgroundColor: 'rgba(15,23,42,0.92)',
      borderColor: 'transparent',
      borderRadius: 8,
      padding: [10, 14],
      textStyle: { color: '#e2e8f0', fontSize: 12 },
      formatter: (params) => {
        if (!params || !params.length) return ''
        // 跳过辅助 bar，用 K线系列的数据索引
        const klineParam = params.find(p => p.seriesName === 'K线') || params[0]
        const d = klineData.value[klineParam.dataIndex]
        if (!d) return ''
        const changeClass = Number(d.change) >= 0 ? '#e53935' : '#009e5f'
        const sign = Number(d.change) >= 0 ? '+' : ''
        return `<div style="font-weight:600;">${d.date}</div>
          <div style="margin-top:3px;color:#ffffff;">开 <b>${d.open}</b></div>
          <div style="color:#e53935;">高 <b>${d.high}</b></div>
          <div style="color:#009e5f;">低 <b>${d.low}</b></div>
          <div style="color:${changeClass};">涨跌幅 <b>${sign}${d.changePercent}%</b></div>
          <div style="color:${changeClass};">涨跌额 <b>${sign}${Number(d.change).toFixed(2)}</b></div>`
      }
    },
    series: [
      // 透明辅助 bar：每个 X 位置一个全高柱，覆盖整个绘图区域，确保任意位置都能触发 mousemove
      {
        name: '_hit',
        type: 'bar',
        data: hitBars,
        barWidth: '80%',
        z: -10,
        itemStyle: { color: 'transparent', borderColor: 'transparent', borderWidth: 0 },
        emphasis: { itemStyle: { color: 'transparent' } },
        tooltip: { show: false }
      },
      {
        name: 'K线',
        type: 'candlestick',
        data: ohlc,
        itemStyle: {
          color: colors.up,
          color0: colors.down,
          borderColor: colors.upBorder,
          borderColor0: colors.downBorder
        },
        barMaxWidth: 20,
        z: 1
      },
      {
        name: 'MA5',
        type: 'line',
        data: ma5Values.value,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1, color: '#f5a623' },
        itemStyle: { color: '#f5a623' },
        z: 2
      },
      {
        name: 'MA10',
        type: 'line',
        data: ma10Values.value,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1, color: '#4a90d9' },
        itemStyle: { color: '#4a90d9' },
        z: 2
      },
      {
        name: 'MA20',
        type: 'line',
        data: ma20Values.value,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1, color: '#b44bcb' },
        itemStyle: { color: '#b44bcb' },
        z: 2
      }
    ]
  }

  const volMax = Math.max(...volumes.map(v => v.value))

  const volOption = {
    grid: { left: '8%', right: '2%', top: '3%', bottom: '12%' },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisTick: { show: false },
      axisLabel: { show: false },
      splitLine: { show: false }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: volMax * 1.8,
      splitNumber: 3,
      splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } },
      axisLabel: { color: '#94a3b8', fontSize: 9, formatter: v => {
        if (v >= 1e8) return (v / 1e8).toFixed(1) + '亿'
        if (v >= 1e4) return (v / 1e4).toFixed(0) + '万'
        return v
      }}
    },
    dataZoom: [
      { type: 'inside', start: 0, end: 100, minSpan: minSpanPct },
      { type: 'slider', show: false, start: 0, end: 100, minSpan: minSpanPct }
    ],
    series: [{
      type: 'bar',
      data: volumes,
      barWidth: '60%'
    }]
  }

  mainChart.setOption(mainOption)
  volChart.setOption(volOption)

  // 使用 ECharts 原生 connect 同步 dataZoom，避免递归卡顿
  mainChart.group = 'klineGroup'
  volChart.group = 'klineGroup'
  echartsLib.connect('klineGroup')

  // mousemove：透明辅助 bar 覆盖每个 X 位置全高，确保任意位置都能触发
  // 注意：不手动 dispatch showTip/hideTip，echarts.connect() 已自动同步两图 tooltip
  const onMainMove = (params) => {
    if (params.dataIndex != null) hoveredIdx.value = params.dataIndex
  }
  mainChart.on('mousemove', onMainMove)
  volChart.on('mousemove', onMainMove)
  // 用父容器 mouseleave 统一处理离开，避免两图间切换时误重置
  const chartArea = mainChartRef.value.parentElement
  if (chartArea) {
    chartArea.addEventListener('mouseleave', () => { hoveredIdx.value = -1 })
  }
}

const disposeCharts = () => {
  const echartsLib = window.echarts
  if (mainChart && !mainChart.isDisposed()) {
    mainChart.group = null
    mainChart.dispose()
    mainChart = null
  }
  if (volChart && !volChart.isDisposed()) {
    volChart.group = null
    volChart.dispose()
    volChart = null
  }
  if (echartsLib) echartsLib.disconnect('klineGroup')
}

const handleResize = () => {
  if (mainChart && !mainChart.isDisposed()) mainChart.resize()
  if (volChart && !volChart.isDisposed()) volChart.resize()
}

watch(() => props.visible, (v) => {
  if (v && props.indexCode) {
    fetchKline(currentKlt.value)
  } else {
    disposeCharts()
  }
})

watch(() => props.indexCode, (newCode) => {
  if (props.visible && newCode) {
    currentKlt.value = '101'
    fetchKline('101')
  }
})

onMounted(() => {
  window.addEventListener('resize', handleResize)
  if (props.visible && props.indexCode) fetchKline(currentKlt.value)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  disposeCharts()
})
</script>

<style scoped>
.quote-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  z-index: 9999;
  padding-top: 60px;
  overflow-y: auto;
}

.quote-panel {
  background: white;
  border-radius: 20px;
  width: 95%;
  max-width: 1100px;
  max-height: calc(100vh - 100px);
  overflow-y: auto;
  box-shadow: 0 25px 60px rgba(0, 0, 0, 0.25);
}

.quote-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 28px;
  border-bottom: 1px solid #f0f0f0;
  position: sticky;
  top: 0;
  background: white;
  border-radius: 20px 20px 0 0;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.header-left h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #333;
}

.header-code {
  font-size: 13px;
  color: #1677ff;
  background: rgba(22, 119, 255, 0.08);
  padding: 2px 10px;
  border-radius: 4px;
  font-family: monospace;
}

.close-btn {
  width: 36px; height: 36px;
  border: none;
  background: #f5f7fa;
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
  background: #e53935;
  color: white;
}

.quote-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 0;
  color: #888;
  gap: 16px;
}

.quote-error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px;
  margin: 20px;
  color: #e53935;
  font-size: 15px;
  background: linear-gradient(135deg, #fff5f5, #ffe8e8);
  border-radius: 12px;
}

.error-icon {
  width: 28px; height: 28px;
  background: #e53935;
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 16px;
}

/* ---- Summary ---- */
.summary-section {
  padding: 20px 28px;
  border-bottom: 1px solid #f0f0f0;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 16px;
  margin-bottom: 20px;
}

.current-price {
  font-size: 36px;
  font-weight: 700;
  color: #333;
  letter-spacing: -1px;
}

.price-change {
  font-size: 16px;
  font-weight: 500;
}

.price-row .up { color: #e53935; }
.price-row .down { color: #009e5f; }

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}

.metric-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: #f8fafc;
  border-radius: 10px;
  padding: 12px 14px;
}

.metric-label {
  font-size: 11px;
  color: #94a3b8;
}

.metric-value {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.metric-value.up { color: #e53935; }
.metric-value.down { color: #009e5f; }

/* ---- MA Row ---- */
.ma-row {
  display: flex;
  gap: 14px;
  padding-top: 10px;
  margin-top: 10px;
  border-top: 1px solid #f1f5f9;
}

.ma-tag {
  font-size: 12px;
  font-weight: 500;
  padding: 1px 8px;
  border-radius: 3px;
  border-left: 1px solid;
}


.ma-tag.ma5  { color: #b8861a; border-left-color: #f5a623; background: rgba(245,166,35,0.05); }
.ma-tag.ma10 { color: #3874b4; border-left-color: #4a90d9; background: rgba(74,144,217,0.05); }
.ma-tag.ma20 { color: #8e3ea3; border-left-color: #b44bcb; background: rgba(180,75,203,0.05); }

/* ---- Period Bar ---- */
.period-bar {
  display: flex;
  gap: 4px;
  padding: 12px 28px;
  background: #f8fafc;
  border-bottom: 1px solid #f0f0f0;
  flex-wrap: wrap;
}

.period-btn {
  padding: 6px 14px;
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
}

.period-btn:hover {
  color: #334155;
  background: rgba(255,255,255,0.6);
}

.period-btn.active {
  background: white;
  color: #1677ff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  font-weight: 600;
}

/* ---- Charts ---- */
.chart-area {
  padding: 20px 24px 24px;
}

.main-chart {
  width: 100%;
  height: 420px;
}

.vol-chart {
  width: 100%;
  height: 140px;
}

/* ---- Loading Spinner ---- */
.pl {
  width: 6em; height: 6em;
}
.pl__ring {
  animation: ringA 2s linear infinite;
}
.pl__ring--a { stroke: #f42f25; }
.pl__ring--b { animation-name: ringB; stroke: #ffdd00; }
.pl__ring--c { animation-name: ringC; stroke: #255ff4; }
.pl__ring--d { animation-name: ringD; stroke: #255ff4; }

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
  from { stroke-dasharray: 0 440; stroke-width: 20; stroke-dashoffset: 0; }
  8% { stroke-dasharray: 40 400; stroke-width: 30; stroke-dashoffset: -5; }
  28% { stroke-dasharray: 40 400; stroke-width: 30; stroke-dashoffset: -175; }
  36%, 58% { stroke-dasharray: 0 440; stroke-width: 20; stroke-dashoffset: -220; }
  66% { stroke-dasharray: 40 400; stroke-width: 30; stroke-dashoffset: -225; }
  86% { stroke-dasharray: 40 400; stroke-width: 30; stroke-dashoffset: -395; }
  94%, to { stroke-dasharray: 0 440; stroke-width: 20; stroke-dashoffset: -440; }
}

@media (max-width: 768px) {
  .metrics-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  .main-chart { height: 280px; }
  .vol-chart { height: 100px; }
  .current-price { font-size: 28px; }
}
</style>
