<template>
  <div class="auth-page" :style="{ backgroundImage: `url(${backgroundImage})` }">
    <div class="auth-container">
      <!-- 左侧品牌区 -->
      <div class="brand-panel">
        <div class="brand-content">
          <img :src="logoImage" alt="Logo" class="brand-logo" />
          <h1 class="brand-name">酱菜养基</h1>
          <p class="brand-slogan">像腌酱菜一样，沉淀你的财富</p>
        </div>
      </div>

      <!-- 右侧表单区 -->
      <div class="form-panel">
        <div class="form-card">
          <template v-if="activeTab === 'login'">
            <h2 class="form-title">登录</h2>
            <Login
              key="login"
              @login-success="handleLoginSuccess"
              @go-register="activeTab = 'register'"
            />
          </template>

          <template v-else>
            <h2 class="form-title">注册</h2>
            <Register
              key="register"
              @register-success="handleRegisterSuccess"
              @go-login="activeTab = 'login'"
            />
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import Login from '../components/Login.vue'
import Register from '../components/Register.vue'
import backgroundImage from '@/assets/images/background.png'
import logoImage from '@/assets/images/logo-removebg-preview.png'

const router = useRouter()
const activeTab = ref('login')

const handleLoginSuccess = () => {
  router.push('/')
}

const handleRegisterSuccess = () => {
  router.push('/')
}
</script>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  padding: 20px;
}

.auth-container {
  display: flex;
  width: 100%;
  max-width: 960px;
  min-height: 560px;
  border-radius: 24px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.12);
}

/* ===== 左侧品牌区 ===== */
.brand-panel {
  flex: 0 0 42%;
  background: linear-gradient(160deg, #e8f2ff 0%, #d4e6ff 40%, #bed8f8 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 36px;
  position: relative;
  overflow: hidden;
}

.brand-panel::before {
  content: '';
  position: absolute;
  top: -60px;
  right: -60px;
  width: 200px;
  height: 200px;
  border-radius: 50%;
  background: rgba(22, 119, 255, 0.06);
}

.brand-panel::after {
  content: '';
  position: absolute;
  bottom: -40px;
  left: -40px;
  width: 160px;
  height: 160px;
  border-radius: 50%;
  background: rgba(22, 119, 255, 0.06);
}

.brand-content {
  text-align: center;
  position: relative;
  z-index: 1;
}

.brand-logo {
  width: 120px;
  height: 120px;
  object-fit: contain;
  margin-bottom: 24px;
  filter: drop-shadow(0 4px 12px rgba(22, 119, 255, 0.15));
}

.brand-name {
  font-size: 32px;
  font-weight: 700;
  color: #1a3a5c;
  margin: 0 0 12px 0;
  letter-spacing: 2px;
}

.brand-slogan {
  font-size: 15px;
  color: #5a7a9a;
  margin: 0;
  line-height: 1.6;
  letter-spacing: 1px;
}

/* ===== 右侧表单区 ===== */
.form-panel {
  flex: 1;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 36px;
}

.form-card {
  width: 100%;
  max-width: 360px;
}

.form-title {
  text-align: center;
  color: #333;
  margin: 0 0 28px 0;
  font-size: 26px;
  font-weight: 700;
}
</style>
