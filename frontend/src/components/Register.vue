<template>
  <form @submit.prevent="handleRegister" class="register-form">
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
      <label>昵称（选填）</label>
      <input
        v-model="formData.nickname"
        type="text"
        placeholder="请输入昵称"
      />
    </div>

    <div class="form-item">
      <label>验证码</label>
      <div class="verify-code-row">
        <input
          v-model="formData.verifyCode"
          type="text"
          placeholder="请输入验证码"
          required
        />
        <button
          type="button"
          class="send-code-btn"
          @click="handleSendCode"
          :disabled="countdown > 0"
        >
          {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
        </button>
      </div>
    </div>

    <div class="form-item">
      <label>密码</label>
      <input
        v-model="formData.password"
        type="password"
        placeholder="请输入密码（至少6位）"
        required
      />
    </div>

    <div class="form-item">
      <label>确认密码</label>
      <input
        v-model="formData.confirmPassword"
        type="password"
        placeholder="请再次输入密码"
        required
      />
    </div>

    <div v-if="errorMsg" class="error-message">{{ errorMsg }}</div>
    <div v-if="successMsg" class="success-message">{{ successMsg }}</div>

    <button type="submit" class="register-btn" :disabled="loading">
      {{ loading ? '注册中...' : '注册' }}
    </button>

    <div class="form-footer">
      <span>已有账号？</span>
      <a @click="$emit('go-login')" class="link">立即登录</a>
    </div>
  </form>
</template>

<script setup>
import { ref } from 'vue'
import { authApi } from '../api/auth'
import { useAuthStore } from '../stores/authStore'

const emit = defineEmits(['register-success', 'go-login'])

const authStore = useAuthStore()

const formData = ref({
  email: '',
  nickname: '',
  verifyCode: '',
  password: '',
  confirmPassword: ''
})

const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const countdown = ref(0)

const handleSendCode = async () => {
  if (!formData.value.email) {
    errorMsg.value = '请输入邮箱'
    return
  }

  errorMsg.value = ''
  successMsg.value = ''

  try {
    await authApi.sendVerifyCode(formData.value.email)
    successMsg.value = '验证码已发送'
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (error) {
    errorMsg.value = error.response?.data?.message || '发送验证码失败'
  }
}

const handleRegister = async () => {
  errorMsg.value = ''
  successMsg.value = ''

  if (formData.value.password.length < 6) {
    errorMsg.value = '密码长度不能少于6位'
    return
  }

  if (formData.value.password !== formData.value.confirmPassword) {
    errorMsg.value = '两次密码输入不一致'
    return
  }

  loading.value = true

  try {
    const response = await authStore.register({
      email: formData.value.email,
      nickname: formData.value.nickname,
      verifyCode: formData.value.verifyCode,
      password: formData.value.password,
      confirmPassword: formData.value.confirmPassword
    })

    if (response.code === 200) {
      emit('register-success', response.data.user)
    } else {
      errorMsg.value = response.message || '注册失败'
    }
  } catch (error) {
    errorMsg.value = error.response?.data?.message || '注册失败，请检查输入'
  } finally {
    loading.value = false
  }
}
</script>

<style src="@/assets/auth-form.css"></style>

<style scoped>
.register-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-item input {
  padding: 12px 16px;
  border: 2px solid #e8e8e8;
  border-radius: 10px;
  font-size: 15px;
  transition: all 0.3s;
  background: #fafbfc;
}

.verify-code-row {
  display: flex;
  gap: 10px;
}

.verify-code-row input {
  flex: 1;
}

.send-code-btn {
  padding: 10px 14px;
  background: linear-gradient(135deg, #1677ff 0%, #1677ff 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.3s;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.25);
}

.send-code-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.35);
}

.send-code-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
  box-shadow: none;
  transform: none;
}

.success-message {
  color: #009e5f;
  font-size: 14px;
  text-align: center;
  padding: 10px;
  background: #f0fff4;
  border-radius: 8px;
  border: 1px solid #b8e6c1;
}
</style>
