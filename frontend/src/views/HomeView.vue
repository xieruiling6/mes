<script setup>
import AppHeader from '../components/AppHeader.vue'
import { computed, onMounted, ref } from 'vue'
import {
  ArrowRight,
  Box,
  Calendar,
  CircleCheck,
  DataLine,
  EditPen,
  List,
  Monitor,
  Odometer,
  Setting,
  SetUp,
  Tickets,
  TrendCharts,
  Warning,
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { getDashboardStatistics } from '../api/statistics'
import { listProducts } from '../api/products'

const auth = useAuthStore()
const canReadProducts = computed(() => ['product:read', 'product:manage'].some((permission) => auth.user?.permissions?.includes(permission)))
const canReadProcesses = computed(() => ['process:read', 'process:manage'].some((permission) => auth.user?.permissions?.includes(permission)))
const canReadOrders = computed(() => ['order:read', 'order:manage'].some((permission) => auth.user?.permissions?.includes(permission)))
const canReadPlans = computed(() => ['plan:read', 'plan:manage'].some((permission) => auth.user?.permissions?.includes(permission)))
const canOpenWorkOrders = computed(() => ['workorder:read', 'workorder:manage'].some((permission) => auth.user?.permissions?.includes(permission)))
const canOpenReports = computed(() => ['report:create', 'report:read:own', 'report:read'].some((permission) => auth.user?.permissions?.includes(permission)))
const canOpenQuality = computed(() => ['quality:read', 'quality:create', 'quality:dispose'].some((permission) => auth.user?.permissions?.includes(permission)))
const canOpenEquipment = computed(() => ['equipment:read', 'equipment:manage', 'equipment:maintain'].some((permission) => auth.user?.permissions?.includes(permission)))
const canReadStatistics = computed(() => auth.user?.permissions?.includes('statistics:read'))
const canOpenSystem = computed(() => ['system:user:manage', 'system:role:manage', 'system:permission:read'].some((permission) => auth.user?.permissions?.includes(permission)))
const statistics = ref(null)
const products = ref([])
const selectedProduct = ref('')

const localIso = (date) => new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
const today = new Date()
const dateTo = localIso(today)
const dateFrom = localIso(new Date(today.getTime() - 29 * 86400000))
const dateLabel = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(today).replace('日星', '日 星')
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const displayName = computed(() => auth.user?.displayName || auth.user?.username || '生产伙伴')

const roleLabels = {
  ADMIN: '系统管理员',
  PRODUCTION_MANAGER: '生产经理',
  PLANNER: '计划员',
  WORKSHOP_SUPERVISOR: '车间主管',
  OPERATOR: '操作员',
}
const roles = computed(() => (auth.user?.roles || []).map((role) => roleLabels[role] || role))
const productionTotal = computed(() => (statistics.value?.dailyProduction || []).reduce((sum, item) => sum + item.productionQty, 0))
const inProgressOrders = computed(() => statistics.value?.workOrderStatuses?.find((item) => item.status === 'IN_PROGRESS')?.count ?? 0)

const shortcuts = computed(() => [
  { to: '/statistics', label: '生产统计', desc: '趋势、质量与计划执行分析', icon: TrendCharts, visible: canReadStatistics.value },
  { to: '/products', label: '产品管理', desc: '产品编码、规格与计量单位', icon: Box, visible: canReadProducts.value },
  { to: '/processes', label: '工序管理', desc: '维护制造工序基础资料', icon: SetUp, visible: canReadProcesses.value },
  { to: '/orders', label: '生产订单', desc: '创建并跟踪生产需求', icon: Tickets, visible: canReadOrders.value },
  { to: '/plans', label: '生产计划', desc: '安排日期、优先级与进度', icon: Calendar, visible: canReadPlans.value },
  { to: '/work-orders', label: '生产工单', desc: '下发、开工、暂停与完工', icon: List, visible: canOpenWorkOrders.value },
  { to: '/reports', label: '生产报工', desc: '登记产量、合格与不合格数', icon: EditPen, visible: canOpenReports.value },
  { to: '/quality', label: '质量管理', desc: '检验记录与不合格品处置', icon: CircleCheck, visible: canOpenQuality.value },
  { to: '/equipment', label: '设备管理', desc: '设备档案与维修履历', icon: Monitor, visible: canOpenEquipment.value },
  { to: '/system', label: '系统管理', desc: '用户、角色权限与账号安全', icon: Setting, visible: canOpenSystem.value },
].filter((item) => item.visible))

async function loadStatistics() {
  if (!canReadStatistics.value) return
  try {
    statistics.value = (await getDashboardStatistics({
      dateFrom, dateTo, productId: selectedProduct.value || undefined,
    })).data.data
  } catch { /* keep dashboard usable when statistics are unavailable */ }
}
async function loadProducts() {
  try { products.value = (await listProducts({ page: 1, size: 100 })).data.data.records }
  catch { /* statistics remain usable without the optional product filter */ }
}
onMounted(async () => {
  if (!canReadStatistics.value) return
  await Promise.all([loadProducts(), loadStatistics()])
})
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="app-main">
      <section class="home-hero">
        <div class="home-hero-copy">
          <p class="home-hero-kicker">生产运营中心</p>
          <h1>{{ greeting }}，{{ displayName }}</h1>
          <p class="home-hero-sub">从订单到报工，今天的生产进度、质量表现与现场动态都汇总在这里。</p>
          <div class="home-hero-meta">
            <span class="home-hero-date">
              <el-icon><Calendar /></el-icon>
              {{ dateLabel }}
            </span>
            <span>当前角色</span>
            <el-tag v-for="role in roles" :key="role" effect="plain">{{ role }}</el-tag>
            <span v-if="!roles.length">尚未分配角色</span>
          </div>
        </div>
        <div class="home-hero-side" aria-hidden="true">
          <span class="hero-mark">MES</span>
          <p>订单 · 计划 · 工单 · 报工<br>全链路透明协同</p>
          <span class="hero-online">系统已连接</span>
        </div>
      </section>

      <template v-if="canReadStatistics">
        <div class="dashboard-toolbar">
          <span class="dashboard-toolbar-title">近 30 日生产概况</span>
          <div class="dashboard-toolbar-actions">
            <el-select v-model="selectedProduct" clearable filterable placeholder="全部产品" class="dashboard-product-filter" @change="loadStatistics">
              <el-option v-for="product in products" :key="product.id" :value="product.id" :label="`${product.productCode} · ${product.productName}`" />
            </el-select>
            <router-link to="/statistics" class="dashboard-report-link">
              查看详细报表
              <el-icon><ArrowRight /></el-icon>
            </router-link>
          </div>
        </div>

        <section class="dashboard-stats" aria-label="近30日生产统计">
          <div class="dashboard-stat">
            <span class="dashboard-stat-icon"><el-icon><Tickets /></el-icon></span>
            <div class="dashboard-stat-copy">
              <span>生产订单</span>
              <strong>{{ statistics?.orders?.total ?? '—' }}</strong>
              <small>所选期间新建</small>
            </div>
          </div>
          <div class="dashboard-stat">
            <span class="dashboard-stat-icon"><el-icon><Odometer /></el-icon></span>
            <div class="dashboard-stat-copy">
              <span>生产中工单</span>
              <strong>{{ inProgressOrders }}</strong>
              <small>现场正在执行</small>
            </div>
          </div>
          <div class="dashboard-stat">
            <span class="dashboard-stat-icon"><el-icon><DataLine /></el-icon></span>
            <div class="dashboard-stat-copy">
              <span>有效报工产量</span>
              <strong>{{ productionTotal.toLocaleString('zh-CN') }}</strong>
              <small>按报工日期汇总</small>
            </div>
          </div>
          <div class="dashboard-stat">
            <span class="dashboard-stat-icon"><el-icon><Warning /></el-icon></span>
            <div class="dashboard-stat-copy">
              <span>质检不合格</span>
              <strong>{{ statistics?.quality?.unqualifiedQty ?? '—' }}</strong>
              <small>所选期间检验</small>
            </div>
          </div>
        </section>
      </template>

      <section class="quick-links" aria-label="快捷入口">
        <router-link v-for="item in shortcuts" :key="item.to" :to="item.to">
          <span class="quick-link-icon"><el-icon><component :is="item.icon" /></el-icon></span>
          <span class="quick-link-copy">
            <strong>{{ item.label }}</strong>
            <span>{{ item.desc }}</span>
          </span>
          <el-icon class="quick-link-arrow"><ArrowRight /></el-icon>
        </router-link>
      </section>
    </main>
  </div>
</template>
