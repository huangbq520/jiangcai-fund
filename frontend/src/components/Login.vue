<template>
  <form @submit.prevent="handleLogin" class="login-form">
    <div class="form-item">
      <label>邮箱</label>
      <input
        v-model="formData.email"
        type="email"
        placeholder="请输入邮箱"
        required
      />
    </div>

    <div class="form-item">
      <label>密码</label>
      <input
        v-model="formData.password"
        type="password"
        placeholder="请输入密码"
        required
      />
    </div>

    <div v-if="errorMsg" class="error-message">{{ errorMsg }}</div>

    <button type="submit" class="login-btn" :disabled="loading">
      {{ loading ? '登录中...' : '登录' }}
    </button>

    <div class="form-footer">
      <span>还没有账号？</span>
      <a @click="$emit('go-register')" class="link">立即注册</a>
    </div>
  </form>
</template>

<script setup>
import { ref } from 'vue'
import { useAuthStore } from '../stores/authStore'

const emit = defineEmits(['login-success', 'go-register'])

const authStore = useAuthStore()

const formData = ref({
  email: '',
  password: ''
})

const loading = ref(false)
const errorMsg = ref('')

const handleLogin = async () => {
  errorMsg.value = ''
  loading.value = true

  try {
    const response = await authStore.login(formData.value)
    if (response.code === 200) {
      emit('login-success', response.data.user)
    } else {
      errorMsg.value = response.message || '登录失败'
    }
  } catch (error) {
    errorMsg.value = error.response?.data?.message || '登录失败，请检查邮箱和密码'
  } finally {
    loading.value = false
  }
}
</script>

<style src="@/assets/auth-form.css"></style>

<style scoped>
.login-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-item input {
  padding: 14px 16px;
  border: 2px solid #e8e8e8;
  border-radius: 10px;
  font-size: 15px;
  transition: all 0.3s;
  background: #fafbfc;
}
</style>
