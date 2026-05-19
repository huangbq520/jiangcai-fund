<template>
  <div class="user-menu" ref="menuRef">
    <button class="avatar-btn" @click="toggle" :class="{ active: visible }">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" class="avatar-icon">
        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
        <circle cx="12" cy="7" r="4"/>
      </svg>
    </button>

    <Transition name="dropdown">
      <div v-if="visible" class="dropdown-panel" @click.stop>
        <!-- 用户信息头部 -->
        <div class="panel-header">
          <div class="header-avatar">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" class="header-avatar-icon">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
              <circle cx="12" cy="7" r="4"/>
            </svg>
          </div>
          <div class="header-info">
            <span class="header-nickname">{{ user?.nickname || '用户' }}</span>
            <span class="header-email">{{ user?.email || '' }}</span>
          </div>
          <div class="sync-status" :class="{ syncing: isSyncing }">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="sync-icon" :class="{ spinning: isSyncing }">
              <polyline points="23 4 23 10 17 10"/>
              <polyline points="1 20 1 14 7 14"/>
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
            </svg>
            <span class="sync-text">{{ isSyncing ? '同步中' : '已同步' }}</span>
          </div>
        </div>

        <div class="panel-divider"></div>

        <!-- 菜单项 -->
        <div class="menu-items">
          <button class="menu-item" @click="handleClick('profit')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="menu-icon">
              <line x1="12" y1="20" x2="12" y2="10"/>
              <line x1="18" y1="20" x2="18" y2="4"/>
              <line x1="6" y1="20" x2="6" y2="16"/>
            </svg>
            <span>我的收益</span>
          </button>
          <button class="menu-item" @click="handleClick('help')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="menu-icon">
              <circle cx="12" cy="12" r="10"/>
              <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
              <line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
            <span>使用帮助</span>
          </button>
          <button class="menu-item" @click="handleClick('sync')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="menu-icon">
              <polyline points="23 4 23 10 17 10"/>
              <polyline points="1 20 1 14 7 14"/>
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
            </svg>
            <span>数据同步</span>
          </button>
          <button class="menu-item" @click="handleClick('settings')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="menu-icon">
              <circle cx="12" cy="12" r="3"/>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
            </svg>
            <span>系统设置</span>
          </button>
        </div>

        <div class="panel-divider"></div>

        <div class="menu-items">
          <button class="menu-item danger" @click="handleLogout">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="menu-icon">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
              <polyline points="16 17 21 12 16 7"/>
              <line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
            <span>登出</span>
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/authStore'
import { useFundStore } from '../stores/fundStore'
import { storeToRefs } from 'pinia'
import { useToast } from '../composables/useToast'

const router = useRouter()
const authStore = useAuthStore()
const fundStore = useFundStore()
const { user } = storeToRefs(authStore)

const toast = useToast()
const menuRef = ref(null)
const visible = ref(false)

const isSyncing = computed(() => fundStore.loading?.holdings || fundStore.loading?.summary)

const toggle = () => {
  visible.value = !visible.value
}

const handleClick = (key) => {
  visible.value = false
  const tips = {
    profit: '我的收益',
    help: '使用帮助',
    sync: '数据同步',
    settings: '系统设置'
  }
  toast.info(tips[key] + '功能开发中，敬请期待')
}

const handleLogout = () => {
  visible.value = false
  authStore.logout()
  router.push('/auth')
}

const handleClickOutside = (e) => {
  if (menuRef.value && !menuRef.value.contains(e.target)) {
    visible.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped>
.user-menu {
  position: relative;
}

.avatar-btn {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: 2px solid rgba(0, 0, 0, 0.1);
  background: rgba(255, 255, 255, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #666;
  transition: all 0.3s ease;
  padding: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.avatar-btn:hover,
.avatar-btn.active {
  border-color: #1677ff;
  color: #1677ff;
  background: rgba(22, 119, 255, 0.06);
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(22, 119, 255, 0.25);
}

.avatar-icon {
  width: 20px;
  height: 20px;
}

/* 下拉面板 */
.dropdown-panel {
  position: absolute;
  top: calc(100% + 12px);
  right: 0;
  width: 260px;
  background: white;
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15), 0 0 0 1px rgba(0, 0, 0, 0.05);
  overflow: hidden;
  z-index: 1000;
}

/* 头部 */
.panel-header {
  padding: 18px 18px 14px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.header-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, #e8f4ff 0%, #d4ecff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #1677ff;
}

.header-avatar-icon {
  width: 26px;
  height: 26px;
}

.header-info {
  text-align: center;
}

.header-nickname {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}

.header-email {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-top: 2px;
}

.sync-status {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #52c41a;
}

.sync-status.syncing {
  color: #1677ff;
}

.sync-icon {
  width: 14px;
  height: 14px;
}

.sync-icon.spinning {
  animation: spin 1.5s linear infinite;
}

.sync-text {
  font-weight: 500;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.panel-divider {
  height: 1px;
  background: #f0f0f0;
  margin: 0 14px;
}

/* 菜单项 */
.menu-items {
  padding: 6px 8px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 14px;
  color: #475569;
  cursor: pointer;
  transition: all 0.15s;
}

.menu-item:hover {
  background: #f8f9ff;
  color: #1677ff;
}

.menu-item.danger {
  color: #e53935;
}

.menu-item.danger:hover {
  background: #fff5f5;
  color: #e53935;
}

.menu-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

/* 过渡动效 */
.dropdown-enter-active {
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}
.dropdown-leave-active {
  transition: all 0.15s ease-in;
}
.dropdown-enter-from {
  opacity: 0;
  transform: translateY(-8px) scale(0.96);
}
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.98);
}
</style>
