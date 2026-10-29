<script setup>
import AppHeader from '../components/AppHeader.vue'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { getDashboardStatistics, getProductionReportDetails } from '../api/statistics'
import { listProducts } from '../api/products'

const auth = useAuthStore()
const router = useRouter()
const today = new Date()
const isoDate = (date) => new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
const dateTo = ref(isoDate(today))
const dateFrom = ref(isoDate(new Date(today.getTime() - 29 * 86400000)))
const dashboard = ref(null)
const details = ref([])
const products = ref([])
const filters = reactive({ productId: '', status: '' })
const loading = ref(false)
const trendElement = ref(null)
const statusElement = ref(null)
const planElement = ref(null)
let charts = []
const canReadProducts = computed(() => auth.user?.permissions?.some((p) => ['product:read','product:manage'].includes(p)))
const canReadProcesses = computed(() => auth.user?.permissions?.some((p) => ['process:read','process:manage'].includes(p)))
const canReadOrders = computed(() => auth.user?.permissions?.some((p) => ['order:read','order:manage'].includes(p)))
const canReadPlans = computed(() => auth.user?.permissions?.some((p) => ['plan:read','plan:manage'].includes(p)))
const canReadReports = computed(() => auth.user?.permissions?.some((p) => ['report:read','report:read:own','report:create'].includes(p)))
const canReadQuality = computed(() => auth.user?.permissions?.some((p) => ['quality:read','quality:create','quality:dispose'].includes(p)))
const canReadEquipment = computed(() => auth.user?.permissions?.some((p) => ['equipment:read','equipment:manage','equipment:maintain'].includes(p)))
const navLinks = computed(() => [
  ['/', '工作台', true], ['/statistics', '生产统计', true], ['/products', '产品', canReadProducts.value],
  ['/processes', '工序', canReadProcesses.value], ['/orders', '生产订单', canReadOrders.value],
  ['/plans', '生产计划', canReadPlans.value], ['/reports', '生产报工', canReadReports.value],
  ['/quality', '质量管理', canReadQuality.value], ['/equipment', '设备管理', canReadEquipment.value],
])
const number = (value) => Number(value || 0).toLocaleString('zh-CN')
const qualityRate = computed(() => {
  const q = dashboard.value?.quality
  return q?.inspectionQty ? `${(q.goodQty * 100 / q.inspectionQty).toFixed(1)}%` : '—'
})
const planStatusLabels = { DRAFT: '草稿', RELEASED: '已发布', IN_PROGRESS: '执行中', COMPLETED: '已完成', CANCELLED: '已取消' }

async function load() {
  if (!dateFrom.value || !dateTo.value || dateFrom.value > dateTo.value) {
    ElMessage.warning('请选择有效的日期范围')
    return
  }
  loading.value = true
  try {
    const params = { dateFrom: dateFrom.value, dateTo: dateTo.value,
      productId: filters.productId || undefined, status: filters.status || undefined }
    const [summary, report] = await Promise.all([getDashboardStatistics(params), getProductionReportDetails(params)])
    dashboard.value = summary.data.data
    details.value = report.data.data
    await nextTick()
    drawCharts()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '统计数据加载失败')
  } finally { loading.value = false }
}

function drawCharts() {
  charts.forEach((chart) => chart.dispose())
  charts = [trendElement.value, statusElement.value, planElement.value].filter(Boolean).map((node) => echarts.init(node))
  const daily = dashboard.value?.dailyProduction || []
  charts[0]?.setOption({
    color: ['#0f766e', '#d97706', '#b4534b'],
    tooltip: { trigger: 'axis' }, legend: { top: 4, data: ['生产数量', '合格数量', '不合格数量'] },
    grid: { left: 48, right: 24, top: 48, bottom: 32 },
    xAxis: { type: 'category', data: daily.map((item) => item.reportDate), axisLabel: { hideOverlap: true } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '生产数量', type: 'line', smooth: true, data: daily.map((item) => item.productionQty) },
      { name: '合格数量', type: 'line', data: daily.map((item) => item.goodQty) },
      { name: '不合格数量', type: 'line', data: daily.map((item) => item.unqualifiedQty) },
    ],
  })
  const statuses = dashboard.value?.workOrderStatuses || []
  const labels = { PENDING: '待下发', RELEASED: '已下发', DISPATCHED: '已下发', IN_PROGRESS: '生产中', PAUSED: '已暂停', COMPLETED: '已完成', CANCELLED: '已取消' }
  charts[1]?.setOption({ tooltip: { trigger: 'item' }, color: ['#85969c','#3b8e90','#0f766e','#d97706','#5b8c5a','#b5bfc2'],
    legend: { bottom: 0, left: 'center', icon: 'circle', itemWidth: 8, itemHeight: 8, itemGap: 14, textStyle: { color: '#52606d', fontSize: 11 } },
    series: [{ type: 'pie', radius: ['38%', '62%'], center: ['50%', '43%'], itemStyle: { borderColor: '#fff', borderWidth: 2 }, label: { show: false }, labelLine: { show: false },
      data: statuses.map((item) => ({ name: labels[item.status] || item.status, value: item.count })) }] })
  const plans = dashboard.value?.planExecution || []
  charts[2]?.setOption({ color: ['#0f766e', '#d97706'], tooltip: { trigger: 'axis' }, legend: { top: 4 },
    grid: { left: 58, right: 18, top: 44, bottom: 26 }, xAxis: { type: 'category', data: plans.map((item) => planStatusLabels[item.status] || item.status) },
    yAxis: { type: 'value', minInterval: 1 }, series: [
      { name: '计划数量', type: 'bar', data: plans.map((item) => item.plannedQty) },
      { name: '有效报工数量', type: 'bar', data: plans.map((item) => item.reportedQty) },
    ] })
}
function resizeCharts() { charts.forEach((chart) => chart.resize()) }
async function signOut() { try { await auth.logout(); ElMessage.success('已退出登录') } catch { ElMessage.warning('会话已结束') } finally { await router.replace({ name: 'login' }) } }
async function loadProducts() {
  try {
    const { data } = await listProducts({ page: 1, size: 100 })
    products.value = data.data.records
  } catch { /* product filter stays empty when the account cannot read products */ }
}
onMounted(() => { loadProducts(); load(); window.addEventListener('resize', resizeCharts) })
onBeforeUnmount(() => { window.removeEventListener('resize', resizeCharts); charts.forEach((chart) => chart.dispose()) })
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="app-main wide-main statistics-main" v-loading="loading">
      <div class="page-heading">
        <div>
          <p class="page-kicker">生产分析</p>
          <h1>生产统计与报表</h1>
        </div>
      </div>
      <section class="statistics-toolbar" aria-label="统计筛选">
        <div class="statistics-filters">
          <span class="toolbar-label">筛选</span>
          <el-select v-model="filters.productId" clearable filterable placeholder="全部产品" class="stats-select"><el-option v-for="product in products" :key="product.id" :value="product.id" :label="`${product.productCode} · ${product.productName}`" /></el-select>
          <el-select v-model="filters.status" clearable placeholder="全部订单状态" class="stats-select"><el-option label="草稿" value="DRAFT" /><el-option label="已确认" value="CONFIRMED" /><el-option label="生产中" value="IN_PROGRESS" /><el-option label="已完成" value="COMPLETED" /><el-option label="已取消" value="CANCELLED" /></el-select>
          <el-date-picker v-model="dateFrom" type="date" value-format="YYYY-MM-DD" placeholder="开始日期"/><span>至</span><el-date-picker v-model="dateTo" type="date" value-format="YYYY-MM-DD" placeholder="结束日期"/><el-button type="primary" @click="load">查询</el-button>
        </div>
      </section>
      <section class="stat-cards">
        <div><span>订单数</span><strong>{{ number(dashboard?.orders?.total) }}</strong><small>所选期间新建</small></div>
        <div><span>工单数</span><strong>{{ number(dashboard?.workOrderStatuses?.reduce((sum, item) => sum + item.count, 0)) }}</strong><small>所选期间新建</small></div>
        <div><span>有效报工产量</span><strong>{{ number(dashboard?.dailyProduction?.reduce((sum, item) => sum + item.productionQty, 0)) }}</strong><small>按报工日期汇总</small></div>
        <div><span>质检合格率</span><strong>{{ qualityRate }}</strong><small>按检验数量计算</small></div>
        <div><span>质检不合格</span><strong>{{ number(dashboard?.quality?.unqualifiedQty) }}</strong><small>所选期间检验记录</small></div>
      </section>
      <section class="statistics-grid">
        <div class="chart-section trend-section"><h2>每日生产数量</h2><div ref="trendElement" class="chart-area"></div></div>
        <div class="chart-section"><h2>工单状态</h2><div ref="statusElement" class="chart-area"></div></div>
        <div class="chart-section"><h2>计划执行情况</h2><div ref="planElement" class="chart-area"></div><div class="plan-summary"><span v-for="item in dashboard?.planExecution || []" :key="item.status">{{ planStatusLabels[item.status] || item.status }}：{{ number(item.planCount) }} 份，报工 {{ number(item.reportedQty) }}/{{ number(item.plannedQty) }}</span></div></div>
        <div class="chart-section ranking-section"><h2>产品生产排行</h2><el-table :data="dashboard?.productRanking || []" size="small" empty-text="暂无报工数据"><el-table-column prop="productCode" label="产品编码" min-width="115"/><el-table-column prop="productName" label="产品名称" min-width="140"/><el-table-column label="生产数量" width="130" align="right"><template #default="scope">{{ number(scope.row.productionQty) }} {{ scope.row.unit }}</template></el-table-column><el-table-column label="合格 / 不合格" width="160" align="right"><template #default="scope">{{ number(scope.row.goodQty) }} / {{ number(scope.row.unqualifiedQty) }}</template></el-table-column></el-table></div>
      </section>
      <section class="chart-section report-detail"><h2>报工明细（最多500条）</h2><el-table :data="details" size="small" empty-text="所选期间暂无有效报工"><el-table-column prop="reportDate" label="日期" width="112"/><el-table-column prop="reportNo" label="报工单号" min-width="145"/><el-table-column prop="workOrderNo" label="工单号" min-width="140"/><el-table-column prop="productName" label="产品" min-width="150"/><el-table-column prop="reporterName" label="报工人员" width="115"/><el-table-column prop="productionQty" label="生产数量" width="110" align="right"/><el-table-column prop="goodQty" label="合格" width="90" align="right"/><el-table-column prop="unqualifiedQty" label="不合格" width="90" align="right"/></el-table></section>
    </main>
  </div>
</template>
