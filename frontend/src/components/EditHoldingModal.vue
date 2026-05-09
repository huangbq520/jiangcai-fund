<template>
  <div class="modal-overlay" @click.self="handleClose">
    <div class="modal-content">
      <div class="modal-header">
        <h3>编辑持仓 - {{ holding.fundName }}</h3>
        <button class="close-btn" @click="handleClose">×</button>
      </div>

      <div class="modal-body">
        <div class="form-item">
          <label>基金代码</label>
          <input type="text" :value="holding.fundCode" disabled class="disabled-input" />
        </div>

        <div class="form-item">
          <label>持有份额</label>
          <input
            v-model="formData.holdShare"
            type="number"
            step="0.01"
            placeholder="请输入持有份额"
          />
        </div>

        <div class="form-item">
          <label>成本价</label>
          <input
            v-model="formData.costPrice"
            type="number"
            step="0.0001"
            placeholder="请输入成本价"
          />
        </div>

        <div class="form-item">
          <label>买入日期</label>
          <input
            v-model="formData.buyDate"
            type="date"
            placeholder="请选择买入日期"
          />
        </div>
      </div>

      <div class="modal-footer">
        <button class="cancel-btn" @click="handleClose">取消</button>
        <button class="submit-btn" @click="handleSubmit" :disabled="submitting">
          {{ submitting ? '保存中...' : '保存' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { fundApi } from '../api'

const props = defineProps({
  holding: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['close', 'update', 'summary-updated'])

const formData = ref({
  holdShare: '',
  costPrice: '',
  buyDate: ''
})

const submitting = ref(false)

watch(() => props.holding, (newHolding) => {
  if (newHolding) {
    formData.value = {
      holdShare: newHolding.holdShare ? String(newHolding.holdShare) : '',
      costPrice: newHolding.costPrice ? String(newHolding.costPrice) : '',
      buyDate: newHolding.buyDate || ''
    }
  }
}, { immediate: true })

const handleClose = () => {
  emit('close')
}

const handleSubmit = async () => {
  try {
    submitting.value = true

    const response = await fundApi.updateHolding(
      props.holding.fundCode,
      formData.value.holdShare || '0',
      formData.value.costPrice || '0',
      formData.value.buyDate || null
    )

    if (response.code === 200) {
      alert('保存成功')
      emit('update')
      if (response.data) {
        emit('summary-updated', response.data)
      }
    } else {
      alert(response.message || '保存失败')
    }
  } catch (err) {
    alert('保存失败，请稍后重试')
    console.error(err)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 12px;
  width: 400px;
  max-width: 90%;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-header h3 {
  margin: 0;
  font-size: 16px;
  color: #333;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  color: #999;
  cursor: pointer;
  padding: 0;
  line-height: 1;
}

.close-btn:hover {
  color: #666;
}

.modal-body {
  padding: 20px;
}

.form-item {
  margin-bottom: 16px;
}

.form-item:last-child {
  margin-bottom: 0;
}

.form-item label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  color: #666;
}

.form-item input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  font-size: 14px;
  box-sizing: border-box;
}

.form-item input:focus {
  outline: none;
  border-color: #667eea;
}

.disabled-input {
  background: #f5f5f5;
  color: #999;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid #f0f0f0;
}

.cancel-btn,
.submit-btn {
  padding: 10px 20px;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
}

.cancel-btn {
  background: #f0f0f0;
  color: #666;
  border: none;
}

.cancel-btn:hover {
  background: #e0e0e0;
}

.submit-btn {
  background: #667eea;
  color: white;
  border: none;
}

.submit-btn:hover:not(:disabled) {
  background: #5568d3;
}

.submit-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}
</style>