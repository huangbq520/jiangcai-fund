<template>
  <div class="search-fund" :class="{ 'compact': compact, 'expanded': isExpanded }" @click="focusInput">
    <button v-if="compact" class="camera-btn" @click.stop="showOcrModal = true" title="拍照识别基金代码">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/>
        <circle cx="12" cy="13" r="4"/>
      </svg>
    </button>
    <input
      ref="inputRef"
      v-model="searchKeyword"
      type="text"
      :placeholder="compact ? '搜索基金...' : '搜索基金代码或名称'"
      @input="handleInput"
      @focus="handleFocus"
      @keydown.down.prevent="navigateDown"
      @keydown.up.prevent="navigateUp"
      @keydown.enter.prevent="selectHighlighted"
      @keydown.escape="closeDropdown"
      class="search-input"
    />
    <button @click="handleSearch" :disabled="loading" class="search-btn">
      {{ loading ? '搜索中' : '搜索' }}
    </button>

    <!-- Search Results Dropdown -->
    <div v-if="showDropdown && searchResults.length > 0" class="dropdown">
      <div
        v-for="(fund, index) in searchResults"
        :key="fund.fundCode"
        class="dropdown-item"
        :class="{ selected: selectedIndex === index }"
        @click="selectFund(fund)"
        @mouseenter="selectedIndex = index"
      >
        <div class="fund-basic">
          <span class="fund-name" v-html="highlightKeyword(fund.fundName)"></span>
          <span class="fund-code">{{ fund.fundCode }}</span>
        </div>
        <div class="fund-category">
          <span class="category-badge">{{ fund.categoryDesc }}</span>
        </div>
      </div>
    </div>

    <!-- No Results -->
    <div v-if="showDropdown && searchResults.length === 0 && hasSearched" class="dropdown no-results">
      未找到匹配的基金
    </div>

    <!-- Error Message -->
    <div v-if="error && !compact" class="error-msg">
      {{ error }}
    </div>

    <!-- Selected Fund Preview -->
    <div v-if="selectedFund && !showDropdown" class="selected-preview">
      <div class="preview-info">
        <span class="preview-name">{{ selectedFund.fundName }}</span>
        <span class="preview-code">{{ selectedFund.fundCode }}</span>
      </div>
      <button @click="handleAdd" class="add-btn">添加</button>
      <button @click="clearSelection" class="clear-btn">清除</button>
    </div>

    <OcrModal :visible="showOcrModal" @close="showOcrModal = false" @select-fund="handleOcrSelect" />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useFundStore } from '../stores/fundStore'
import { storeToRefs } from 'pinia'
import { useToast } from '../composables/useToast'
import OcrModal from './OcrModal.vue'

const props = defineProps({
  compact: {
    type: Boolean,
    default: false
  },
  groupId: {
    type: Number,
    default: null
  },
  groups: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['add-fund'])

const fundStore = useFundStore()
const { searchResults } = storeToRefs(fundStore)

const inputRef = ref(null)
const searchKeyword = ref('')
const selectedFund = ref(null)
const selectedIndex = ref(-1)
const loading = ref(false)
const error = ref('')
const showDropdown = ref(false)
const hasSearched = ref(false)
const isExpanded = ref(false)
const showOcrModal = ref(false)

let debounceTimer = null
let clickOutsideHandler = null

const toast = useToast()

const focusInput = () => {
  inputRef.value?.focus()
}

const handleInput = () => {
  error.value = ''
  selectedFund.value = null
  selectedIndex.value = -1

  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }

  debounceTimer = setTimeout(() => {
    if (searchKeyword.value.trim().length >= 1) {
      performSearch()
    } else {
      searchResults.value = []
      showDropdown.value = false
    }
  }, 300)
}

const handleFocus = () => {
  isExpanded.value = true
  if (searchResults.value.length > 0) {
    showDropdown.value = true
  }
}

const handleSearch = async () => {
  if (!searchKeyword.value.trim()) {
    error.value = '请输入基金代码或名称'
    return
  }

  await performSearch()
}

const performSearch = async () => {
  loading.value = true
  error.value = ''
  hasSearched.value = true
  showDropdown.value = true

  const response = await fundStore.searchFunds(searchKeyword.value)
  loading.value = false

  if (response.code === 200) {
    selectedIndex.value = searchResults.value.length > 0 ? 0 : -1
  } else {
    error.value = response.message || '未找到匹配的基金'
  }
}

const navigateDown = () => {
  if (selectedIndex.value < searchResults.value.length - 1) {
    selectedIndex.value++
  }
}

const navigateUp = () => {
  if (selectedIndex.value > 0) {
    selectedIndex.value--
  }
}

const selectHighlighted = () => {
  if (selectedIndex.value >= 0 && selectedIndex.value < searchResults.value.length) {
    selectFund(searchResults.value[selectedIndex.value])
  }
}

const selectFund = (fund) => {
  selectedFund.value = fund
  searchKeyword.value = fund.fundName
  showDropdown.value = false
  isExpanded.value = true
  error.value = ''
}

const closeDropdown = () => {
  showDropdown.value = false
}

const clearSelection = () => {
  selectedFund.value = null
  searchKeyword.value = ''
  searchResults.value = []
  selectedIndex.value = -1
  isExpanded.value = false
}

const handleAdd = async () => {
  if (!selectedFund.value) return

  const targetGroupId = props.groupId
  if (!targetGroupId) {
    toast.error('请先选择目标分组')
    return
  }

  try {
    const response = await fundStore.addFund(
      selectedFund.value.fundCode,
      selectedFund.value.fundName,
      targetGroupId
    )
    if (response.code === 200) {
      const group = props.groups.find(g => g.id === targetGroupId)
      const label = group ? `已添加到${group.name}` : '已添加'
      toast.success(label + '！')
      clearSelection()
      emit('add-fund')
    } else {
      toast.error(response.message || '添加失败')
    }
  } catch (err) {
    toast.error('添加失败，请稍后重试')
    console.error(err)
  }
}

const highlightKeyword = (text) => {
  if (!text || !searchKeyword.value) return text
  const regex = new RegExp(`(${searchKeyword.value})`, 'gi')
  return text.replace(regex, '<span class="highlight">$1</span>')
}

const handleOcrSelect = (text) => {
  searchKeyword.value = text
  selectedFund.value = null
  showOcrModal.value = false
  performSearch()
}

const handleClickOutside = (event) => {
  const searchFund = document.querySelector('.search-fund')
  if (searchFund && !searchFund.contains(event.target)) {
    showDropdown.value = false
    if (!selectedFund.value) {
      isExpanded.value = false
    }
  }
}

onMounted(() => {
  clickOutsideHandler = handleClickOutside
  document.addEventListener('click', clickOutsideHandler)
})

onUnmounted(() => {
  if (clickOutsideHandler) {
    document.removeEventListener('click', clickOutsideHandler)
  }
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
})
</script>

<style scoped>
.search-fund {
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  position: relative;
  z-index: 100;
}

.search-fund.compact {
  background: transparent;
  padding: 0;
  box-shadow: none;
  border-radius: 20px;
  width: 240px;
  transition: width 0.3s ease;
  transform: none !important;
}

.search-fund.compact:focus-within,
.search-fund.compact.expanded {
  width: 440px;
}

.search-input {
  width: 100%;
  padding: 14px 100px 14px 18px;
  border: 2px solid #e8e8e8;
  border-radius: 12px;
  font-size: 16px;
  transition: all 0.3s;
  box-sizing: border-box;
  background: #fafbfc;
}

.search-fund.compact .camera-btn {
  position: absolute;
  left: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 22px;
  height: 22px;
  border: none;
  background: transparent;
  color: #999;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  transition: all 0.2s;
  z-index: 2;
  padding: 0;
}

.search-fund.compact .camera-btn:hover {
  color: #1677ff;
  background: rgba(22, 119, 255, 0.08);
}

.search-fund.compact .search-input {
  padding: 7px 44px 7px 36px;
  border-radius: 20px;
  font-size: 13px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(0, 0, 0, 0.08);
}

.search-input:focus {
  outline: none;
  border-color: #1677ff;
  background: white;
  box-shadow: 0 0 0 4px rgba(102, 126, 234, 0.1);
}

.search-fund.compact .search-input:focus {
  background: white;
  box-shadow: 0 0 0 4px rgba(22, 119, 255, 0.15);
}

.search-btn {
  position: absolute;
  right: 4px;
  top: 4px;
  bottom: 4px;
  padding: 10px 24px;
  background: linear-gradient(135deg, #1677ff 0%, #1677ff 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.search-fund.compact .search-btn {
  right: 4px;
  top: 4px;
  bottom: 4px;
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  box-shadow: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.search-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.search-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
  box-shadow: none;
}

.dropdown {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  right: 0;
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
  max-height: 320px;
  overflow-y: auto;
  z-index: 1000;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15);
}

.search-fund.compact .dropdown {
  top: calc(100% + 6px);
  border-radius: 12px;
  max-height: 280px;
}

.dropdown-item {
  padding: 14px 18px;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #f0f0f0;
  transition: all 0.2s;
}

.dropdown-item:last-child {
  border-bottom: none;
}

.dropdown-item:hover,
.dropdown-item.selected {
  background: linear-gradient(135deg, #f8f9ff 0%, #f0f4ff 100%);
}

.fund-basic {
  display: flex;
  align-items: center;
  gap: 12px;
}

.fund-name {
  font-size: 15px;
  font-weight: 500;
  color: #333;
}

.fund-code {
  font-size: 13px;
  color: #999;
}

.fund-category {
  display: flex;
  align-items: center;
}

.category-badge {
  font-size: 12px;
  padding: 4px 10px;
  background: linear-gradient(135deg, #e8f4ff 0%, #d4ecff 100%);
  color: #1677ff;
  border-radius: 20px;
  font-weight: 500;
}

.highlight {
  background: linear-gradient(135deg, #fff3cd 0%, #ffeeba 100%);
  color: #e53935;
  padding: 0 2px;
  border-radius: 2px;
}

.no-results {
  padding: 24px;
  text-align: center;
  color: #999;
  font-size: 14px;
}

.selected-preview {
  margin-top: 16px;
  padding: 18px 20px;
  background: linear-gradient(135deg, #f8f9ff 0%, #f0f4ff 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  gap: 16px;
  border: 1px solid #e8f0ff;
}

.search-fund.compact .selected-preview {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  right: 0;
  margin-top: 0;
  padding: 14px 16px;
  border-radius: 12px;
  z-index: 999;
}

.preview-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.preview-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.preview-code {
  font-size: 14px;
  color: #666;
  background: white;
  padding: 2px 8px;
  border-radius: 4px;
}

.add-btn {
  padding: 10px 24px;
  background: linear-gradient(135deg, #1677ff 0%, #1677ff 100%);
  color: white;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

.add-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.clear-btn {
  padding: 10px 18px;
  background: white;
  color: #666;
  border: 1px solid #e0e0e0;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
}

.clear-btn:hover {
  background: #f5f5f5;
  border-color: #d0d0d0;
}

.error-msg {
  margin-top: 12px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #fff5f5 0%, #ffe8e8 100%);
  color: #e53935;
  border-radius: 8px;
  font-size: 14px;
  border: 1px solid #ffd4d4;
}
</style>
