<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const year = new Date().getFullYear()

async function submit() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login({ username: form.username.trim(), password: form.password })
    await router.replace({ name: 'home' })
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-visual" aria-label="工擎 MES 产品介绍">
      <div class="login-visual-top">
        <span class="login-brand-mark">工</span>
        <span>
          <strong>工擎 MES</strong>
          <small>制造执行系统</small>
        </span>
      </div>

      <div class="login-visual-copy">
        <p class="login-eyebrow">从订单到交付 · 全链路协同</p>
        <h1>让每一道工序<br><em>都有迹可循</em></h1>
        <p>统一管理生产订单、计划、工单与报工，把现场进度变成可执行、可追溯的生产数据。</p>
      </div>

      <ul class="login-features">
        <li>订单、计划、工单、报工全流程闭环</li>
        <li>数量校验、状态流转与操作审计</li>
        <li>角色权限分离，现场数据实时汇总</li>
      </ul>

      <p class="login-visual-footer">GONGQING MES · 生产运营中心</p>
    </section>

    <section class="login-form-panel">
      <div class="login-panel" aria-labelledby="login-title">
        <div class="login-panel-heading">
          <h2 id="login-title">登录系统</h2>
          <span>使用管理员分配的账号访问</span>
        </div>
        <el-form :model="form" size="large" @submit.prevent="submit">
          <el-form-item>
            <el-input v-model="form.username" autocomplete="username" placeholder="用户名" :prefix-icon="User" aria-label="用户名" @keyup.enter="submit" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="form.password" autocomplete="current-password" type="password" show-password placeholder="密码" :prefix-icon="Lock" aria-label="密码" @keyup.enter="submit" />
          </el-form-item>
          <el-button class="login-submit" type="primary" :loading="loading" @click="submit">登录</el-button>
        </el-form>
      </div>
      <p class="login-copyright">© {{ year }} 工擎 MES · 企业内部系统</p>
    </section>
  </main>
</template>
