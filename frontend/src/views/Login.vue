<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import axios from 'axios'
import { clearSession, saveSession } from '../utils/session'

const router = useRouter()
const loading = ref(false)

const formState = reactive({
  username: '',
  password: ''
})

const onFinish = async () => {
  if (!formState.username || !formState.password) {
    message.warning('请输入用户名和密码')
    return
  }

  loading.value = true

  try {
    const res = await axios.post('/api/auth/login', {
      username: formState.username,
      password: formState.password
    }, { timeout: 10000 })

    if (res.data.token) {
      clearSession()
      saveSession({
        token: res.data.token,
        username: res.data.username || formState.username,
        roles: res.data.roles || [],
        permissions: res.data.permissions || []
      })
      message.success('登录成功')
      router.push('/dashboard')
      return
    }

    message.error(res.data?.message || '登录失败')
  } catch (error) {
    if (axios.isAxiosError(error)) {
      if (error.response) {
        message.error(error.response.data?.message || '用户名或密码错误')
      } else if (error.request) {
        message.error('无法连接到服务器')
      } else {
        message.error('登录失败')
      }
      return
    }

    message.error('登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
    </div>
    
    <!-- 登录卡片 -->
    <div class="login-card">
      <!-- Logo -->
      <div class="card-header">
        <div class="logo">
          <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
            <rect width="32" height="32" rx="8" fill="#2563eb"/>
            <path d="M8 16C8 11.5817 11.5817 8 16 8V8C20.4183 8 24 11.5817 24 16V24H16C11.5817 24 8 20.4183 8 16V16Z" fill="white"/>
            <circle cx="16" cy="16" r="4" fill="#2563eb"/>
          </svg>
          <span class="logo-text">GovCMS</span>
        </div>
      </div>

      <!-- 标题 -->
      <div class="card-title">
        <h1>欢迎登录</h1>
        <p>输入您的账号信息继续</p>
      </div>

      <!-- 表单 -->
      <form @submit.prevent="onFinish" class="login-form">
        <div class="form-field">
          <label for="username">用户名</label>
          <input 
            id="username"
            v-model="formState.username"
            type="text" 
            placeholder="输入用户名"
            class="form-input"
            autocomplete="username"
          />
        </div>
        
        <div class="form-field">
          <label for="password">密码</label>
          <input 
            id="password"
            v-model="formState.password"
            type="password" 
            placeholder="输入密码"
            class="form-input"
            autocomplete="current-password"
          />
        </div>
        
        <div class="form-options">
          <label class="checkbox-wrapper">
            <input type="checkbox" class="checkbox" />
            <span class="checkbox-label">记住我</span>
          </label>
          <a href="#" class="link">忘记密码？</a>
        </div>
        
        <button 
          type="submit" 
          class="submit-btn"
          :class="{ loading }"
          :disabled="loading"
        >
          <span v-if="!loading">登 录</span>
          <span v-else class="spinner"></span>
        </button>
      </form>

      <!-- 提示 -->
      <div class="card-footer">
        <span>演示账号: admin / admin123</span>
      </div>
    </div>

    <!-- 底部 -->
    <div class="page-footer">
      <span>© 2026 GovCMS 政府内容管理系统</span>
    </div>
  </div>
</template>

<style>
*, *::before, *::after {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
}
</style>

<style scoped>
.login-page {
  min-height: 100vh;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  position: relative;
  overflow: hidden;
}

/* 背景装饰 */
.bg-decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.gradient-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
}

.orb-1 {
  width: 600px;
  height: 600px;
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
  top: -200px;
  left: -100px;
}

.orb-2 {
  width: 500px;
  height: 500px;
  background: linear-gradient(135deg, #e0e7ff, #c7d2fe);
  bottom: -150px;
  right: -100px;
}

/* 登录卡片 */
.login-card {
  width: 100%;
  max-width: 400px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 40px;
  position: relative;
  z-index: 1;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05);
}

/* Logo */
.card-header {
  display: flex;
  justify-content: center;
  margin-bottom: 32px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-text {
  font-size: 20px;
  font-weight: 600;
  color: #1e293b;
}

/* 标题 */
.card-title {
  text-align: center;
  margin-bottom: 32px;
}

.card-title h1 {
  font-size: 24px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 8px;
}

.card-title p {
  font-size: 14px;
  color: #64748b;
}

/* 表单 */
.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-field label {
  font-size: 14px;
  font-weight: 500;
  color: #374151;
}

.form-input {
  width: 100%;
  height: 48px;
  padding: 0 16px;
  font-size: 15px;
  color: #1e293b;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  outline: none;
  transition: all 0.2s;
}

.form-input::placeholder {
  color: #94a3b8;
}

.form-input:focus {
  background: #ffffff;
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

/* 表单选项 */
.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.checkbox-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.checkbox {
  width: 18px;
  height: 18px;
  accent-color: #2563eb;
}

.checkbox-label {
  font-size: 14px;
  color: #64748b;
}

.link {
  font-size: 14px;
  color: #2563eb;
  text-decoration: none;
  font-weight: 500;
}

.link:hover {
  text-decoration: underline;
}

/* 提交按钮 */
.submit-btn {
  width: 100%;
  height: 48px;
  background: #2563eb;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 8px;
}

.submit-btn:hover:not(:disabled) {
  background: #1d4ed8;
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 底部提示 */
.card-footer {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #f1f5f9;
  text-align: center;
}

.card-footer span {
  font-size: 13px;
  color: #94a3b8;
}

/* 页面底部 */
.page-footer {
  position: absolute;
  bottom: 24px;
  font-size: 13px;
  color: #94a3b8;
}

/* 响应式 */
@media (max-width: 480px) {
  .login-card {
    padding: 32px 24px;
  }
}
</style>
