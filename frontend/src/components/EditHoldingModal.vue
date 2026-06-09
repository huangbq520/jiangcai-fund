<template>
  <Transition name="modal">
    <div class="modal-overlay" @click.self="handleClose">
      <div class="modal-content">
        <div class="modal-header">
          <h3>{{ holding.fundCode }} {{ holding.fundName }}</h3>
          <button class="close-btn" @click="handleClose">×</button>
        </div>

        <div class="scene-tabs">
          <button :class="['scene-tab', { active: scene === 'edit' }]" @click="switchScene('edit')">编辑</button>
          <button :class="['scene-tab', { active: scene === 'buy' }]" @click="switchScene('buy')">加仓</button>
          <button :class="['scene-tab', { active: scene === 'sell' }]" @click="switchScene('sell')">减仓</button>
        </div>

        <div class="nav-info">实时净值 {{ fmtNAV }}</div>

        <!-- ====== 减仓 ====== -->
        <div v-if="scene === 'sell'" class="mode-section">
          <div class="quick-row">
            <button v-for="opt in sellOptions" :key="opt.label" class="quick-btn"
              @click="applySellRatio(opt.ratio)">{{ opt.label }}</button>
          </div>
          <div class="form-item">
            <label>卖出份额</label>
            <input v-model="adjustShare" type="number" step="0.01" placeholder="或手动输入" />
            <span class="hint">持有 {{ holding.holdShare }} 份 · 市值 {{ fmtMoney(holding.currentValue) }}</span>
          </div>
          <div class="form-item">
            <label>日期</label>
            <input v-model="adjustDate" type="date" />
          </div>
          <div class="time-toggle">
            <span class="time-label">确认时间</span>
            <button :class="['time-btn', { active: before3pm }]" @click="before3pm = true">3点前</button>
            <button :class="['time-btn', { active: !before3pm }]" @click="before3pm = false">3点后</button>
          </div>
        </div>

        <!-- ====== 加仓 ====== -->
        <div v-if="scene === 'buy'" class="mode-section">
          <div class="form-item">
            <label>买入金额</label>
            <input v-model="buyAmount" type="number" step="0.01" placeholder="输入买入金额" />
          </div>
          <div class="form-item">
            <label>日期</label>
            <input v-model="adjustDate" type="date" />
          </div>
          <div class="time-toggle">
            <span class="time-label">确认时间</span>
            <button :class="['time-btn', { active: before3pm }]" @click="before3pm = true">3点前</button>
            <button :class="['time-btn', { active: !before3pm }]" @click="before3pm = false">3点后</button>
          </div>
          <div class="result-row">
            <span>≈ {{ computedBuyShare }} 份</span>
            <span>净值 {{ effectiveNavDisplay }}</span>
          </div>
        </div>

        <!-- ====== 编辑持仓 ====== -->
        <div v-if="scene === 'edit'" class="mode-section">
          <div class="mode-pills">
            <button :class="['pill', { active: mode === 'SHARES' }]" @click="switchMode('SHARES')">份额</button>
            <button :class="['pill', { active: mode === 'AMOUNT' }]" @click="switchMode('AMOUNT')">金额</button>
          </div>

          <Transition name="fade" mode="out-in">
            <div v-if="mode === 'SHARES'" key="shares" class="mode-panel">
              <div class="form-item">
                <label>持有份额</label>
                <input v-model="form.holdShare" type="number" step="0.01" />
              </div>
              <div class="form-item">
                <label>成本价</label>
                <input v-model="form.costPrice" type="number" step="0.0001" />
              </div>
              <div class="calc-row">
                <div class="calc-item">
                  <span class="calc-label">持仓金额</span>
                  <span class="calc-value">{{ fmtMoney(computedAmount) }}</span>
                </div>
                
                <div class="calc-item">
                  <span class="calc-label">持仓盈亏</span>
                  <span class="calc-value" :class="profitClass(computedProfit)">{{ fmtProfit(computedProfit) }}（{{ fmtPercent(computedProfitRate) }}）</span>
                </div>
              </div>
              
            </div>

            <div v-else key="amount" class="mode-panel">
              <div class="form-item">
                <label>持仓金额</label>
                <input v-model="form.holdAmount" type="number" step="0.01" />
              </div>
              <div class="form-row">
                <div class="form-item flex-1">
                  <label>盈亏金额</label>
                  <input v-model="form.profit" type="number" step="0.01" @focus="profitField='amount'" />
                </div>
                <span class="or-sep">或</span>
                <div class="form-item flex-1">
                  <label>收益率</label>
                  <input v-model="form.profitRate" type="number" step="0.01" @focus="profitField='rate'" />
                </div>
              </div>

              <div class="calc-row">
                <div class="calc-item"><span class="calc-label">→ 份额</span><span class="calc-value">{{ computedReverseShare }}</span></div>
                <div class="calc-item"><span class="calc-label">→ 成本价 <span class="cost-nav-source">{{ costNAVSource }}</span></span><span class="calc-value">{{ computedReverseCost }}</span></div>
              </div>
            </div>
          </Transition>

          <div class="form-item date-item">
            <label>买入日期</label>
            <input v-model="form.buyDate" type="date" :max="yesterday" />
          </div>
        </div>

        <div class="modal-footer">
          <button class="cancel-btn" @click="handleClose">取消</button>
          <button class="submit-btn" @click="handleSubmit" :disabled="submitting">
            {{ submitting ? '保存中...' : submitLabel }}
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { fundApi } from '../api'
import { useFundStore } from '../stores/fundStore'
import { useToast } from '../composables/useToast'

const props = defineProps({ holding: { type: Object, required: true } })
const emit = defineEmits(['close', 'update'])

const scene = ref('edit')
const mode = ref('SHARES')
const profitField = ref('amount')
const submitting = ref(false)
const adjustShare = ref('')
const adjustDate = ref('')
const buyAmount = ref('')
const before3pm = ref(true)
const dateNav = ref(null)
const editCostNav = ref(null)
const costNavLoading = ref(false)
const toast = useToast()
const fundStore = useFundStore()

const form = reactive({
  holdShare: '', costPrice: '', buyDate: '',
  holdAmount: '', profit: '', profitRate: ''
})

const sellOptions = [
  { label: '1/4', ratio: 0.25 },
  { label: '1/2', ratio: 0.5 },
  { label: '全部', ratio: 1 }
]

const yesterday = computed(() => {
  const d = new Date()
  d.setDate(d.getDate() - 1)
  return d.toISOString().slice(0, 10)
})

const NAV = computed(() => parseFloat(props.holding?.currentNetValue || props.holding?.estimatedNetValue || props.holding?.unitNetValue || 0))

// 金额模式下计算成本使用的净值：必须使用买入日期的历史净值，不允许回退到实时净值
const costNAV = computed(() => {
  if (mode.value !== 'AMOUNT') return NAV.value
  if (editCostNav.value) return editCostNav.value
  // 返回 null 表示尚未获取到历史净值，UI 应显示加载中
  return null
})

const costNAVSource = computed(() => {
  if (mode.value !== 'AMOUNT') return ''
  if (costNavLoading.value) return '（查询中...）'
  if (editCostNav.value) return '（买入日净值）'
  if (!form.buyDate) return '（请选择买入日期）'
  return '（未找到该日净值，请更换日期）'
})

const fmtNAV = computed(() => NAV.value ? NAV.value.toFixed(4) : '--')
const fmtMoney = (v) => { const n = Number(v) || 0; return '¥' + n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
const fmtProfit = (v) => { const n = Number(v) || 0; return (n >= 0 ? '+' : '') + n.toFixed(2) }
const fmtPercent = (v) => { const n = Number(v) || 0; return (n >= 0 ? '+' : '') + n.toFixed(2) + '%' }
const profitClass = (v) => Number(v) > 0 ? 'profit-up' : Number(v) < 0 ? 'profit-down' : ''

const computedAmount = computed(() => (parseFloat(form.holdShare) || 0) * (parseFloat(form.costPrice) || 0))
const computedValue = computed(() => (parseFloat(form.holdShare) || 0) * NAV.value)
const computedProfit = computed(() => computedValue.value - computedAmount.value)
const computedProfitRate = computed(() => computedAmount.value ? (computedProfit.value / computedAmount.value) * 100 : 0)

const costNavDisplay = computed(() => {
  if (costNavLoading.value) return '查询中...'
  if (editCostNav.value) return editCostNav.value.toFixed(4)
  if (form.buyDate) return '未找到'
  return '请选择日期'
})

const computedReverseShare = computed(() => {
  const nav = costNAV.value
  if (!nav) return '...'
  const a = parseFloat(form.holdAmount) || 0
  return a > 0 ? (a / nav).toFixed(2) : '0.00'
})
const computedReverseCost = computed(() => {
  const nav = costNAV.value
  if (!nav) return '...'
  const amt = parseFloat(form.holdAmount) || 0
  if (amt <= 0) return nav.toFixed(4)  // 未输入金额时显示净值本身作为成本价参考
  const p = parseFloat(form.profit) || 0
  const share = amt / nav
  return ((amt - p) / share).toFixed(4)
})

const computedBuyShare = computed(() => {
  const amt = parseFloat(buyAmount.value) || 0
  return effectiveNAV.value ? (amt / effectiveNAV.value).toFixed(2) : '0.00'
})
const computedBuyCost = computed(() => {
  const nav = effectiveNAV.value
  return nav ? nav.toFixed(4) : '0.0000'
})

watch([adjustDate, before3pm], async ([date, before]) => {
  if (!date || scene.value === 'edit') { dateNav.value = null; return }
  try {
    let qDate = date
    if (!before) {
      const d = new Date(date)
      d.setDate(d.getDate() + 1)
      qDate = d.toISOString().slice(0, 10)
    }
    const res = await fundApi.getNavAt(props.holding.fundCode, qDate)
    dateNav.value = (res.code === 200 && res.data?.nav) ? Number(res.data.nav) : null
  } catch { dateNav.value = null }
})

// 金额模式下监听买入日期变化，获取历史净值作为成本净值
watch(() => form.buyDate, async (date) => {
  if (!date || scene.value !== 'edit' || mode.value !== 'AMOUNT') {
    editCostNav.value = null
    return
  }
  editCostNav.value = null  // 先清除旧值，触发 loading 状态
  fetchCostNav(date)
})

const effectiveNAV = computed(() => dateNav.value || NAV.value)
const effectiveNavDisplay = computed(() => effectiveNAV.value ? effectiveNAV.value.toFixed(4) : '--')

const submitLabel = computed(() => scene.value === 'buy' ? '确认加仓' : scene.value === 'sell' ? '确认减仓' : '保存')

const resetForm = () => {
  const h = props.holding
  form.holdShare = h.holdShare ? String(h.holdShare) : ''
  form.costPrice = h.costPrice ? String(h.costPrice) : ''
  form.buyDate = h.buyDate || ''
  form.holdAmount = h.holdAmount ? String(h.holdAmount) : ''
  form.profit = ''
  form.profitRate = ''
  adjustShare.value = ''
  adjustDate.value = ''
  buyAmount.value = ''
  before3pm.value = true
  dateNav.value = null
  editCostNav.value = null
}

watch(() => props.holding, resetForm, { immediate: true })

const switchMode = (m) => {
  if (m === mode.value) return
  mode.value = m
  if (m === 'AMOUNT') {
    // 使用成本金额（份额 × 成本价），不是当前市值
    form.holdAmount = computedAmount.value ? computedAmount.value.toFixed(2) : ''
    form.profit = ''
    form.profitRate = ''
    // 触发买入日期对应的历史净值查询
    if (form.buyDate) {
      fetchCostNav(form.buyDate)
    }
  } else {
    form.holdShare = computedReverseShare.value
    form.costPrice = computedReverseCost.value
    editCostNav.value = null
    costNavLoading.value = false
  }
}

// 根据买入日期获取成本净值（含下一交易日回退）
const fetchCostNav = async (date) => {
  if (!date) { editCostNav.value = null; return }
  costNavLoading.value = true
  try {
    let res = await fundApi.getNavAt(props.holding.fundCode, date)
    if (res.code === 200 && res.data?.nav) {
      editCostNav.value = Number(res.data.nav)
      costNavLoading.value = false
      return
    }
    // 如果该日期无数据（周末/假日），向后查找下一个交易日
    const next = new Date(date)
    for (let i = 0; i < 7; i++) {
      next.setDate(next.getDate() + 1)
      const nextStr = next.toISOString().slice(0, 10)
      res = await fundApi.getNavAt(props.holding.fundCode, nextStr)
      if (res.code === 200 && res.data?.nav) {
        editCostNav.value = Number(res.data.nav)
        costNavLoading.value = false
        return
      }
    }
    editCostNav.value = null
    console.warn('未找到基金 ' + props.holding.fundCode + ' 在 ' + date + ' 及之后7天的历史净值')
  } catch (e) {
    editCostNav.value = null
    console.error('获取历史净值失败:', e)
  }
  costNavLoading.value = false
}

const switchScene = (s) => { scene.value = s; if (s === 'edit') resetForm() }

const applySellRatio = (ratio) => {
  const h = parseFloat(props.holding.holdShare) || 0
  adjustShare.value = String(Math.round(h * ratio * 100) / 100)
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    let response
    const adjArgs = adjustDate.value
      ? { adjustDate: adjustDate.value, before3pm: before3pm.value }
      : {}

    if (scene.value === 'buy') {
      response = await fundApi.adjustHolding(props.holding.fundCode, 'BUY',
        computedBuyShare.value, computedBuyCost.value, adjArgs)
    } else if (scene.value === 'sell') {
      response = await fundApi.adjustHolding(props.holding.fundCode, 'SELL',
        adjustShare.value, null, adjArgs)
    } else if (mode.value === 'SHARES') {
      response = await fundApi.updateHolding(props.holding.fundCode, {
        mode: 'SHARES', holdShare: form.holdShare || '0', costPrice: form.costPrice || '0',
        buyDate: form.buyDate || null
      })
    } else {
      response = await fundApi.updateHolding(props.holding.fundCode, {
        mode: 'AMOUNT', holdAmount: form.holdAmount || '0',
        profit: profitField.value === 'amount' ? form.profit : null,
        profitRate: profitField.value === 'rate' ? form.profitRate : null,
        buyDate: form.buyDate || null,
        costNav: editCostNav.value || null
      })
    }

    if (response.code === 200) {
      if (response.data && scene.value !== 'sell') {
        fundStore.updateHoldingInPlace(props.holding.fundCode, response.data)
      } else {
        fundStore.silentFetchHoldings()
        fundStore.recalcSummary()
      }
      toast.success(submitLabel.value + '成功')
      emit('update')
    } else {
      toast.error(response.message || '操作失败')
    }
  } catch (err) {
    toast.error('操作失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

const handleClose = () => emit('close')
</script>

<style scoped>
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal-content { background: #fff; border-radius: 16px; width: 440px; max-width: 92vw; box-shadow: 0 20px 60px rgba(0,0,0,0.2); overflow: hidden; }
.modal-header { display: flex; justify-content: space-between; align-items: center; padding: 20px 24px 0; }
.modal-header h3 { margin: 0; font-size: 17px; color: #1e293b; font-weight: 600; }
.close-btn { background: none; border: none; font-size: 22px; color: #94a3b8; cursor: pointer; width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center; transition: all 0.2s; }
.close-btn:hover { background: #f1f5f9; color: #334155; }

.scene-tabs { display: flex; gap: 0; margin: 16px 24px 0; background: #f1f5f9; border-radius: 10px; padding: 3px; }
.scene-tab { flex: 1; padding: 8px 0; border: none; background: transparent; border-radius: 8px; font-size: 13px; font-weight: 500; color: #64748b; cursor: pointer; transition: all 0.2s; }
.scene-tab.active { background: #fff; color: #1e293b; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }

.nav-info { text-align: center; padding: 12px 24px 0; font-size: 13px; color: #64748b; }

.mode-section { padding: 16px 24px; }

.mode-pills { display: flex; background: #f1f5f9; border-radius: 10px; padding: 3px; margin-bottom: 16px; }
.pill { flex: 1; padding: 8px 0; border: none; background: transparent; border-radius: 8px; font-size: 13px; font-weight: 500; color: #64748b; cursor: pointer; transition: all 0.2s; }
.pill.active { background: #fff; color: #1e293b; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }

.mode-panel { overflow: hidden; }

.quick-row { display: flex; gap: 8px; margin-bottom: 14px; }
.quick-btn { flex: 1; padding: 8px 0; border: 1px solid #e2e8f0; background: #fff; border-radius: 8px; font-size: 13px; color: #475569; cursor: pointer; transition: all 0.15s; }
.quick-btn:hover { border-color: #1677ff; color: #1677ff; background: #f8f9ff; }

.form-item { margin-bottom: 14px; }
.form-item label { display: block; margin-bottom: 6px; font-size: 13px; color: #64748b; font-weight: 500; }
.form-item input { width: 100%; padding: 10px 12px; border: 1px solid #e2e8f0; border-radius: 8px; font-size: 14px; box-sizing: border-box; background: #fafbfc; transition: border-color 0.2s; }
.form-item input:focus { outline: none; border-color: #1677ff; background: #fff; }
.hint { display: block; margin-top: 4px; font-size: 12px; color: #94a3b8; }

.form-row { display: flex; gap: 10px; align-items: center; }
.form-row .form-item { flex: 1; }
.flex-1 { flex: 1; }
.or-sep { color: #94a3b8; font-size: 12px; padding-top: 22px; }

.calc-row { display: flex; gap: 12px; margin-bottom: 14px; }
.calc-item { flex: 1; background: #f8fafc; border-radius: 8px; padding: 10px 12px; }

.calc-label { display: block; font-size: 11px; color: #94a3b8; margin-bottom: 4px; }
.calc-value { font-size: 15px; font-weight: 600; color: #334155; }

.result-row { display: flex; gap: 12px; font-size: 13px; color: #475569; margin-bottom: 14px; }
.result-row span { flex: 1; background: #f8fafc; border-radius: 6px; padding: 8px 10px; }

.date-item { margin-top: 16px; }

.time-toggle { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; }
.time-label { font-size: 13px; color: #64748b; font-weight: 500; margin-right: 4px; }
.time-btn { padding: 6px 14px; border: 1px solid #e2e8f0; background: #fff; border-radius: 8px; font-size: 13px; color: #64748b; cursor: pointer; transition: all 0.15s; }
.time-btn.active { background: #1677ff; color: #fff; border-color: #1677ff; }
.time-btn:hover:not(.active) { border-color: #1677ff; color: #1677ff; }


.cost-nav-label { font-size: 13px; color: #64748b; font-weight: 500; }
.cost-nav-value { font-size: 16px; font-weight: 700; color: #1677ff; }
.cost-nav-value.loading { color: #94a3b8; font-size: 13px; font-weight: 400; }
.cost-nav-value.missing { color: #f56c6c; font-size: 13px; font-weight: 500; }

.cost-nav-source { font-weight: 400; font-size: 10px; color: #94a3b8; }

.profit-up { color: #e53935 !important; }
.profit-down { color: #009e5f !important; }

.modal-footer { display: flex; justify-content: flex-end; gap: 10px; padding: 16px 24px 20px; border-top: 1px solid #f1f5f9; }
.cancel-btn { padding: 10px 20px; border-radius: 8px; background: #f1f5f9; color: #475569; border: none; font-size: 14px; cursor: pointer; transition: all 0.2s; }
.cancel-btn:hover { background: #e2e8f0; }
.submit-btn { padding: 10px 24px; border-radius: 8px; background: linear-gradient(135deg,#1677ff,#1677ff); color: #fff; border: none; font-size: 14px; font-weight: 500; cursor: pointer; box-shadow: 0 2px 8px rgba(102,126,234,0.3); transition: all 0.2s; }
.submit-btn:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(102,126,234,0.4); }
.submit-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.12s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.modal-enter-active, .modal-leave-active { transition: opacity 0.2s ease; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
