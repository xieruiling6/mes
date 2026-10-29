<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  Box,
  Calendar,
  CircleCheck,
  EditPen,
  HomeFilled,
  List,
  Menu,
  Monitor,
  Setting,
  SwitchButton,
  Tickets,
  TrendCharts,
  SetUp,
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)
const permissions = computed(() => auth.user?.permissions || [])
const hasAny = (codes) => codes.some((code) => permissions.value.includes(code))

const pageLabels = {
  '/': '工作台',
  '/statistics': '生产统计',
  '/products': '产品管理',
  '/processes': '工序管理',
  '/orders': '生产订单',
  '/plans': '生产计划',
  '/work-orders': '生产工单',
  '/reports': '生产报工',
  '/quality': '质量管理',
  '/equipment': '设备管理',
  '/system': '系统管理',
}
const currentLabel = computed(() => pageLabels[route.path] || '生产运营中心')

const navGroups = computed(() => {
  const groups = [
    {
      label: '概览',
      items: [
        { to: '/', label: '工作台', icon: HomeFilled, visible: true },
        { to: '/statistics', label: '生产统计', icon: TrendCharts, visible: hasAny(['statistics:read']) },
      ],
    },
    {
      label: '主数据',
      items: [
        { to: '/products', label: '产品管理', icon: Box, visible: hasAny(['product:read', 'product:manage']) },
        { to: '/processes', label: '工序管理', icon: SetUp, visible: hasAny(['process:read', 'process:manage']) },
      ],
    },
    {
      label: '生产执行',
      items: [
        { to: '/orders', label: '生产订单', icon: Tickets, visible: hasAny(['order:read', 'order:manage']) },
        { to: '/plans', label: '生产计划', icon: Calendar, visible: hasAny(['plan:read', 'plan:manage']) },
        { to: '/work-orders', label: '生产工单', icon: List, visible: hasAny(['workorder:read', 'workorder:manage']) },
        { to: '/reports', label: '生产报工', icon: EditPen, visible: hasAny(['report:create', 'report:read', 'report:read:own']) },
      ],
    },
    {
      label: '质量与设备',
      items: [
        { to: '/quality', label: '质量管理', icon: CircleCheck, visible: hasAny(['quality:read', 'quality:create', 'quality:dispose']) },
        { to: '/equipment', label: '设备管理', icon: Monitor, visible: hasAny(['equipment:read', 'equipment:manage', 'equipment:maintain']) },
      ],
    },
    {
      label: '系统',
      items: [
        { to: '/system', label: '系统管理', icon: Setting, visible: hasAny(['system:user:manage', 'system:role:manage', 'system:permission:read']) },
      ],
    },
  ]
  return groups
    .map((group) => ({ ...group, items: group.items.filter((item) => item.visible) }))
    .filter((group) => group.items.length)
})

const roleLabels = {
  ADMIN: '系统管理员',
  PRODUCTION_MANAGER: '生产经理',
  PLANNER: '计划员',
  WORKSHOP_SUPERVISOR: '车间主管',
  OPERATOR: '操作员',
}
const roleText = computed(() => {
  const roles = auth.user?.roles || []
  return roles.length ? roles.map((role) => roleLabels[role] || role).join(' · ') : '未分配角色'
})
const avatarText = computed(() => (auth.user?.displayName || auth.user?.username || 'M').slice(0, 1))
const userMeta = computed(() => {
  const name = auth.user?.displayName || auth.user?.username || ''
  return roleText.value === name ? `@${auth.user?.username || ''}` : roleText.value
})
const todayLabel = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'short',
}).format(new Date()).replace(/(日)(周)/, '$1 $2')

watch(() => route.fullPath, () => { menuOpen.value = false })

async function signOut() {
  try { await auth.logout(); ElMessage.success('已退出登录') }
  catch { ElMessage.warning('会话已结束') }
  finally { await router.replace({ name: 'login' }) }
}
</script>

<template>
  <div class="sidebar-scrim" :class="{ show: menuOpen }" aria-hidden="true" @click="menuOpen = false" />

  <aside id="app-sidebar" class="app-sidebar" :class="{ open: menuOpen }" aria-label="主导航">
    <router-link to="/" class="sidebar-brand" @click="menuOpen = false">
      <span class="brand-emblem">工</span>
      <span class="brand-copy">
        <strong>工擎 MES</strong>
        <small>制造执行系统</small>
      </span>
    </router-link>

    <nav class="sidebar-nav">
      <div v-for="group in navGroups" :key="group.label" class="nav-group">
        <p class="nav-group-label">{{ group.label }}</p>
        <router-link
          v-for="item in group.items"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          @click="menuOpen = false"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
          <el-icon class="nav-arrow"><ArrowRight /></el-icon>
        </router-link>
      </div>
    </nav>

    <div class="sidebar-footer">
      <router-link to="/system" class="sidebar-user" title="个人设置与系统管理" @click="menuOpen = false">
        <span class="user-avatar">{{ avatarText }}</span>
        <span class="user-copy">
          <strong>{{ auth.user?.displayName }}</strong>
          <small>{{ userMeta }}</small>
        </span>
        <el-icon class="user-arrow"><ArrowRight /></el-icon>
      </router-link>
      <button type="button" class="sidebar-logout" @click="signOut">
        <el-icon><SwitchButton /></el-icon>
        <span>退出登录</span>
      </button>
    </div>
  </aside>

  <header class="app-topbar">
    <div class="topbar-left">
      <button
        type="button"
        class="menu-toggle"
        :aria-expanded="menuOpen"
        aria-controls="app-sidebar"
        aria-label="打开导航菜单"
        @click="menuOpen = !menuOpen"
      >
        <el-icon><Menu /></el-icon>
      </button>
      <div class="topbar-breadcrumb">
        <span>生产运营中心</span>
        <i>/</i>
        <strong>{{ currentLabel }}</strong>
      </div>
    </div>
    <div class="topbar-right">
      <span class="topbar-date">{{ todayLabel }}</span>
      <span class="topbar-divider" aria-hidden="true" />
      <span class="topbar-user">
        <span class="topbar-avatar">{{ avatarText }}</span>
        <span class="topbar-user-name">{{ auth.user?.displayName }}</span>
      </span>
    </div>
  </header>
</template>

<style scoped>
.sidebar-scrim {
  position: fixed;
  z-index: 55;
  inset: 0;
  display: none;
  background: rgba(5, 26, 28, 0.48);
  backdrop-filter: blur(3px);
}

.sidebar-scrim.show { display: block; }

.app-sidebar {
  position: fixed;
  z-index: 60;
  inset: 0 auto 0 0;
  display: flex;
  width: var(--sidebar-width);
  flex-direction: column;
  overflow-y: auto;
  padding: 22px 14px 16px;
  color: #c4dcda;
  background:
    radial-gradient(360px 280px at 8% -4%, rgba(45, 212, 191, 0.16), transparent 70%),
    linear-gradient(180deg, #0b2d33 0%, #082329 100%);
  box-shadow: 24px 0 60px -46px rgba(4, 28, 30, 0.85);
  transform: translateX(-100%);
  transition: transform 220ms ease;
}

.app-sidebar.open { transform: translateX(0); }

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 2px 8px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  text-decoration: none;
}

.brand-emblem {
  display: grid;
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  place-items: center;
  border-radius: 12px;
  color: #042f2e;
  background: linear-gradient(135deg, #99f6e4 0%, #2dd4bf 52%, #14b8a6 100%);
  box-shadow: 0 8px 24px -10px rgba(45, 212, 191, 0.7), inset 0 1px 0 rgba(255, 255, 255, 0.5);
  font-size: 18px;
  font-weight: 800;
}

.brand-copy { display: flex; min-width: 0; flex-direction: column; }
.brand-copy strong { color: #f0fffc; font-size: 16px; font-weight: 700; letter-spacing: 0.02em; }
.brand-copy small { margin-top: 3px; color: #6f9c99; font-size: 10px; font-weight: 600; letter-spacing: 0.18em; }

.sidebar-nav { display: flex; flex-direction: column; gap: 13px; padding: 16px 2px 8px; }
.nav-group { display: grid; gap: 3px; }
.nav-group-label {
  margin: 0 10px 5px;
  color: #527b79;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 9px 11px;
  border-radius: 9px;
  color: #a2c0be;
  font-size: 13px;
  font-weight: 500;
  text-decoration: none;
  transition: color 160ms ease, background 160ms ease;
}

.nav-item > .el-icon { color: #6d9794; font-size: 16px; transition: color 160ms ease; }
.nav-item .nav-arrow { width: 13px; margin-left: auto; color: transparent; font-size: 12px; transform: translateX(-3px); transition: color 160ms ease, transform 160ms ease; }
.nav-item:hover { color: #edfffc; background: rgba(255, 255, 255, 0.055); }
.nav-item:hover > .el-icon { color: #9fe7dd; }
.nav-item:hover .nav-arrow { color: #6f9c99; transform: translateX(0); }

.nav-item.router-link-active {
  color: #f1fffd;
  background: linear-gradient(90deg, rgba(45, 212, 191, 0.19), rgba(45, 212, 191, 0.055));
  box-shadow: inset 0 0 0 1px rgba(94, 234, 212, 0.13);
}

.nav-item.router-link-active::before {
  position: absolute;
  top: 9px;
  bottom: 9px;
  left: -2px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: #2dd4bf;
  content: '';
}

.nav-item.router-link-active > .el-icon { color: #5eead4; }
.nav-item.router-link-active .nav-arrow { color: #5eead4; transform: translateX(0); }

.sidebar-footer { display: grid; gap: 7px; margin-top: auto; padding-top: 14px; border-top: 1px solid rgba(255, 255, 255, 0.08); }
.sidebar-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.035);
  text-decoration: none;
  transition: background 160ms ease, border-color 160ms ease;
}
.sidebar-user:hover { border-color: rgba(94, 234, 212, 0.18); background: rgba(255, 255, 255, 0.06); }
.user-avatar {
  display: grid;
  width: 31px;
  height: 31px;
  flex: 0 0 31px;
  place-items: center;
  border-radius: 9px;
  color: #99f6e4;
  background: rgba(45, 212, 191, 0.12);
  font-size: 12px;
  font-weight: 700;
}
.user-copy { display: flex; min-width: 0; flex: 1; flex-direction: column; }
.user-copy strong { overflow: hidden; color: #eafffb; font-size: 12px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.user-copy small { overflow: hidden; margin-top: 2px; color: #67918e; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.user-arrow { color: #5e8683; font-size: 12px; }
.sidebar-logout {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: 0;
  border-radius: 8px;
  color: #6f9794;
  background: transparent;
  font: inherit;
  font-size: 11.5px;
  cursor: pointer;
  transition: color 160ms ease, background 160ms ease;
}
.sidebar-logout:hover { color: #fecaca; background: rgba(248, 113, 113, 0.08); }

.app-topbar {
  position: sticky;
  z-index: 40;
  top: 0;
  display: flex;
  min-height: var(--topbar-height);
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 0 32px;
  border-bottom: 1px solid rgba(210, 226, 222, 0.8);
  background: rgba(248, 251, 250, 0.82);
  backdrop-filter: blur(16px);
}

.topbar-left, .topbar-right, .topbar-user { display: flex; align-items: center; }
.topbar-left { min-width: 0; gap: 12px; }
.topbar-breadcrumb { display: flex; min-width: 0; align-items: center; gap: 9px; font-size: 12px; }
.topbar-breadcrumb span { color: #7b9391; white-space: nowrap; }
.topbar-breadcrumb i { color: #b8c9c6; font-style: normal; }
.topbar-breadcrumb strong { overflow: hidden; color: #153c3b; font-size: 12.5px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }

.topbar-right { gap: 14px; }
.topbar-date { color: #7b9391; font-size: 11.5px; letter-spacing: 0.02em; white-space: nowrap; }
.topbar-divider { width: 1px; height: 17px; background: #d9e6e3; }
.topbar-user { gap: 8px; }
.topbar-avatar {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border: 1px solid #cfe7e1;
  border-radius: 9px;
  color: #0f766e;
  background: #e9f8f4;
  font-size: 11px;
  font-weight: 700;
}
.topbar-user-name { color: #365654; font-size: 12px; font-weight: 500; }

.menu-toggle {
  display: none;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border: 1px solid #d9e6e3;
  border-radius: 9px;
  color: #315c58;
  background: #fff;
  cursor: pointer;
}

@media (min-width: 1025px) {
  .app-sidebar { transform: translateX(0); }
}

@media (max-width: 1024px) {
  .menu-toggle { display: inline-flex; }
  .app-topbar { padding: 0 20px; }
}

@media (max-width: 680px) {
  .app-topbar { min-height: 60px; padding: 0 16px; }
  .topbar-breadcrumb span, .topbar-breadcrumb i, .topbar-date, .topbar-divider, .topbar-user-name { display: none; }
  .topbar-breadcrumb strong { font-size: 13px; }
}
</style>
