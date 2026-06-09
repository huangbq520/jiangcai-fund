<template>
  <div class="group-manager">
    <div class="group-row">
      <!-- 左侧：分组标签 -->
      <div class="group-tabs">
        <button
          v-for="group in allGroups"
          :key="group.id"
          :class="['group-tab', { active: modelValue === group.id }]"
          @click="switchGroup(group.id)"
        >
          {{ group.name }}
          <span class="group-count">{{ group.fundCount ?? 0 }}</span>
        </button>
      </div>

      <!-- 右侧：分组管理按钮 -->
      <button class="manage-btn" @click="openManageModal">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="3"/>
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
        </svg>
        
      </button>
    </div>

    <!-- 分组管理弹窗 -->
    <Teleport to="body">
      <div v-if="showManageModal" class="modal-overlay" @click.self="closeManageModal">
        <div class="modal-box manage-modal">
          <div class="modal-header">
            <h3>分组管理</h3>
            <button class="modal-close-btn" @click="closeManageModal">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>

          <div class="modal-body">
            <!-- 分组列表 -->
            <div class="group-list">
              <div
                v-for="group in allGroups"
                :key="group.id"
                class="group-list-item"
              >
                <div class="group-info">
                  <!-- 正常显示 -->
                  <template v-if="editingGroupId !== group.id">
                    <span class="group-name">{{ group.name }}</span>
                    <!-- <span class="group-badge">{{ group.groupType === 'HOLDING' ? '持仓' : '自选' }}</span> -->
                    <span class="group-count-badge">({{ group.fundCount ?? 0 }})</span>
                  </template>
                  <!-- 编辑状态 -->
                  <template v-else>
                    <input
                      v-model="editName"
                      type="text"
                      class="inline-edit-input"
                      maxlength="50"
                      @keyup.enter="confirmRename(group.id)"
                      @keyup.escape="cancelRename"
                    />
                  </template>
                </div>
                <div class="group-actions">
                  <template v-if="editingGroupId !== group.id">
                    <button
                      class="action-btn rename-btn"
                      @click="startRename(group)"
                      :disabled="group.groupType === 'HOLDING'"
                      :title="group.groupType === 'HOLDING' ? '默认分组不可重命名' : '重命名'"
                    >重命名</button>
                    <button
                      class="action-btn delete-btn"
                      @click="handleDeleteGroup(group)"
                      :disabled="group.groupType === 'HOLDING'"
                      :title="group.groupType === 'HOLDING' ? '默认分组不可删除' : '删除'"
                    >删除</button>
                  </template>
                  <template v-else>
                    <button class="action-btn save-btn" @click="confirmRename(group.id)">保存</button>
                    <button class="action-btn cancel-btn" @click="cancelRename">取消</button>
                  </template>
                </div>
              </div>
            </div>

            <!-- 空状态 -->
            <div v-if="allGroups.length === 0" class="empty-list">
              暂无分组
            </div>
          </div>

          <!-- 新建分组 -->
          <div class="modal-footer">
            <input
              v-model="newGroupName"
              type="text"
              placeholder="输入新分组名称"
              maxlength="50"
              class="new-group-input"
              @keyup.enter="handleCreateGroup"
            />
            <button class="create-btn" @click="handleCreateGroup" :disabled="!newGroupName.trim()">
              新建分组
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, ref, reactive } from 'vue'
import { useFundStore } from '../stores/fundStore'

const props = defineProps({
  modelValue: {
    type: Number,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'groups-changed'])

const fundStore = useFundStore()

// 所有分组（HOLDING + WATCHLIST），按 sortOrder 排序
const allGroups = computed(() => {
  const list = fundStore.groups || []
  return [...list].sort((a, b) => a.sortOrder - b.sortOrder)
})

// 切换分组
const switchGroup = (groupId) => {
  emit('update:modelValue', groupId)
}

// ========== 管理弹窗 ==========
const showManageModal = ref(false)
const editingGroupId = ref(null)
const editName = ref('')
const newGroupName = ref('')

const openManageModal = () => {
  showManageModal.value = true
}

const closeManageModal = () => {
  showManageModal.value = false
  cancelRename()
  newGroupName.value = ''
}

const startRename = (group) => {
  editingGroupId.value = group.id
  editName.value = group.name
}

const confirmRename = async (groupId) => {
  const name = editName.value.trim()
  if (!name) {
    alert('请输入分组名称')
    return
  }
  try {
    const response = await fundStore.renameGroup(groupId, name)
    if (response.code === 200) {
      await fundStore.fetchGroups()
      emit('groups-changed')
    } else {
      alert(response.message || '重命名失败')
    }
  } catch (e) {
    alert('重命名失败: ' + (e.message || '未知错误'))
  }
  editingGroupId.value = null
  editName.value = ''
}

const cancelRename = () => {
  editingGroupId.value = null
  editName.value = ''
}

const handleCreateGroup = async () => {
  const name = newGroupName.value.trim()
  if (!name) return
  try {
    const response = await fundStore.createGroup(name)
    if (response.code === 200) {
      newGroupName.value = ''
      // 切换到新创建的分组
      emit('update:modelValue', response.data.id)
      emit('groups-changed')
      closeManageModal()
    } else {
      alert(response.message || '创建失败')
    }
  } catch (e) {
    alert('创建失败: ' + (e.message || '未知错误'))
  }
}

const handleDeleteGroup = async (group) => {
  const confirmed = confirm(`确认删除分组"${group.name}"？该分组下的所有基金也会被移除。`)
  if (!confirmed) return
  try {
    const response = await fundStore.deleteGroup(group.id)
    if (response.code === 200) {
      // 如果删除的是当前激活的分组，切换到第一个
      if (props.modelValue === group.id) {
        const remaining = allGroups.value.filter(g => g.id !== group.id)
        const firstGroup = remaining.length > 0 ? remaining[0] : null
        emit('update:modelValue', firstGroup ? firstGroup.id : null)
      }
      await fundStore.fetchGroups()
      emit('groups-changed')
    } else {
      alert(response.message || '删除失败')
    }
  } catch (e) {
    alert('删除失败: ' + (e.message || '未知错误'))
  }
}
</script>

<style scoped>
.group-manager {
  background: white;
  border-radius: 16px;
  padding: 12px 24px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  margin-bottom: 0;
}

.group-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* ========== 分组标签 ========== */
.group-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
  flex: 1;
}

.group-tab {
  flex: 0 0 auto;
  padding: 7px 10px;
  border: none;
  border-radius: 20px;
  background: #f5f5f5;
  font-size: 14px;
  font-weight: 500;
  color: #666;
  cursor: pointer;
  transition: all 0.25s ease;
  display: flex;
  align-items: center;
  gap: 6px;
}

.group-tab:hover {
  color: #1677ff;
  background: #e8f0ff;
}

.group-tab.active {
  color: #fff;
  background: #1677ff;
  box-shadow: 0 2px 12px rgba(22, 119, 255, 0.25);
}

.group-count {
  font-size: 11px;
  font-weight: 600;
  opacity: 0.7;
  background: rgba(0, 0, 0, 0.08);
  padding: 1px 7px;
  border-radius: 10px;
  line-height: 1.4;
  min-width: 18px;
  text-align: center;
}

.group-tab.active .group-count {
  background: rgba(255, 255, 255, 0.25);
  opacity: 1;
}

/* ========== 分组管理按钮 ========== */
.manage-btn {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 8px;
  border: 1px solid #d0d0d0;
  border-radius: 20px;
  background: white;
  font-size: 13px;
  font-weight: 500;
  color: #666;
  cursor: pointer;
  transition: all 0.25s ease;
  white-space: nowrap;
}

.manage-btn:hover {
  color: #1677ff;
  border-color: #1677ff;
  background: #f0f5ff;
}

/* ========== 管理弹窗 ========== */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-box {
  background: white;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  width: 480px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px 16px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-header h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: #333;
}

.modal-close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #999;
  cursor: pointer;
  transition: all 0.2s;
}

.modal-close-btn:hover {
  background: #f5f5f5;
  color: #333;
}

.modal-body {
  padding: 12px 24px;
  overflow-y: auto;
  flex: 1;
}

.group-list {
  display: flex;
  flex-direction: column;
}

.group-list-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f5f5f5;
}

.group-list-item:last-child {
  border-bottom: none;
}

.group-info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.group-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.group-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: #e8f0ff;
  color: #1677ff;
  font-weight: 500;
  white-space: nowrap;
}

.group-count-badge {
  font-size: 12px;
  color: #999;
  white-space: nowrap;
}

.inline-edit-input {
  padding: 6px 10px;
  border: 1px solid #1677ff;
  border-radius: 6px;
  font-size: 14px;
  outline: none;
  width: 160px;
  box-sizing: border-box;
}

.group-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.action-btn {
  padding: 5px 12px;
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  background: white;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.action-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.rename-btn:hover:not(:disabled) {
  color: #1677ff;
  border-color: #1677ff;
  background: #f0f5ff;
}

.delete-btn {
  color: #f56c6c;
  border-color: #fde2e2;
}

.delete-btn:hover:not(:disabled) {
  color: #fff;
  background: #f56c6c;
  border-color: #f56c6c;
}

.save-btn {
  color: #1677ff;
  border-color: #1677ff;
}

.save-btn:hover {
  color: #fff;
  background: #1677ff;
}

.cancel-btn {
  color: #999;
}

.cancel-btn:hover {
  background: #f5f5f5;
}

.empty-list {
  text-align: center;
  padding: 30px;
  color: #999;
  font-size: 14px;
}

/* ========== 新建分组 ========== */
.modal-footer {
  display: flex;
  gap: 10px;
  padding: 16px 24px 20px;
  border-top: 1px solid #f0f0f0;
}

.new-group-input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.new-group-input:focus {
  border-color: #1677ff;
}

.create-btn {
  padding: 8px 20px;
  border: none;
  border-radius: 8px;
  background: #1677ff;
  color: white;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.create-btn:hover:not(:disabled) {
  background: #4096ff;
}

.create-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
