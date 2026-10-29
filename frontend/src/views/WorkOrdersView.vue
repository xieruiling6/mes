<script setup>
import AppHeader from '../components/AppHeader.vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, View, CircleCheck, VideoPlay, VideoPause, RefreshRight, Finished, Tickets } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { listProducts } from '../api/products'
import {
  completeWorkOrder, createWorkOrderFromPlan, dispatchWorkOrder, getWorkOrder, listAvailablePlans,
  listWorkOrderReports, listWorkOrders, pauseWorkOrder, resumeWorkOrder, startWorkOrder,
} from '../api/workOrders'

const auth = useAuthStore()
const canManage = computed(() => auth.user?.permissions?.includes('workorder:manage'))
const loading = ref(false)
const rows = ref([])
const products = ref([])
const total = ref(0)
const plans = ref([])
const filters = reactive({ workOrderNo: '', productId: '', status: '', dates: [] })
const page = reactive({ current: 1, size: 10 })
const createVisible = ref(false)
const selectedPlan = ref(null)
const remark = ref('')
const creating = ref(false)
const detailVisible = ref(false)
const detail = ref(null)
const reportRows = ref([])
const reportTotal = ref(0)
const reportPage = reactive({ current: 1, size: 5 })
const statuses = [
  { value: 'PENDING', label: '待下发', type: 'info' }, { value: 'RELEASED', label: '已下发', type: 'warning' },
  { value: 'IN_PROGRESS', label: '生产中', type: 'primary' }, { value: 'PAUSED', label: '已暂停', type: 'danger' },
  { value: 'COMPLETED', label: '已完成', type: 'success' }, { value: 'CANCELLED', label: '已取消', type: 'info' },
]
const statusInfo = (status) => statuses.find((item) => item.value === status) || { label: status, type: 'info' }
const productOptions = computed(() => products.value.map((product) => ({
  id: product.id, label: `${product.productCode} · ${product.productName}`,
})))
const actionDefinitions = [
  { status: 'PENDING', action: 'dispatch', label: '下发', icon: CircleCheck, call: dispatchWorkOrder },
  { status: 'RELEASED', action: 'start', label: '开工', icon: VideoPlay, call: startWorkOrder },
  { status: 'IN_PROGRESS', action: 'pause', label: '暂停', icon: VideoPause, call: pauseWorkOrder },
  { status: 'PAUSED', action: 'resume', label: '恢复', icon: RefreshRight, call: resumeWorkOrder },
  { status: 'IN_PROGRESS', action: 'complete', label: '完成', icon: Finished, call: completeWorkOrder },
]

async function load() {
  loading.value = true
  const [dateFrom, dateTo] = filters.dates || []
  try {
    const { data } = await listWorkOrders({ page: page.current, size: page.size,
      workOrderNo: filters.workOrderNo || undefined, productId: filters.productId || undefined,
      status: filters.status || undefined, dateFrom: dateFrom || undefined, dateTo: dateTo || undefined })
    rows.value = data.data.records
    total.value = data.data.total
  } catch (error) { ElMessage.error(error.response?.data?.message || '工单列表加载失败') }
  finally { loading.value = false }
}
async function openCreate() {
  try {
    const { data } = await listAvailablePlans()
    plans.value = data.data
    if (!plans.value.length) { ElMessage.info('当前没有可创建工单的已发布计划'); return }
    selectedPlan.value = plans.value[0].planId
    remark.value = ''
    createVisible.value = true
  } catch (error) { ElMessage.error(error.response?.data?.message || '可用计划加载失败') }
}
async function create() {
  creating.value = true
  try {
    await createWorkOrderFromPlan(selectedPlan.value, { remark: remark.value || null })
    ElMessage.success('生产工单已创建')
    createVisible.value = false
    await load()
  } catch (error) { ElMessage.error(error.response?.data?.message || '创建失败，请刷新计划后重试') }
  finally { creating.value = false }
}
async function openDetail(row) {
  try {
    const { data } = await getWorkOrder(row.id)
    detail.value = data.data
    detailVisible.value = true
    reportPage.current = 1
    await loadReports()
  } catch (error) { ElMessage.error(error.response?.data?.message || '工单详情加载失败') }
}
async function loadReports() {
  if (!detail.value) return
  try {
    const { data } = await listWorkOrderReports(detail.value.id, { page: reportPage.current, size: reportPage.size })
    reportRows.value = data.data.records
    reportTotal.value = data.data.total
  } catch (error) { ElMessage.error(error.response?.data?.message || '报工记录加载失败') }
}
async function transition(row, definition) {
  const labels = { dispatch: '下发工单', start: '开始生产', pause: '暂停生产', resume: '恢复生产', complete: '完成工单' }
  try {
    await ElMessageBox.confirm(`确认${labels[definition.action]}“${row.workOrderNo}”？`, labels[definition.action], { type: 'warning' })
    await definition.call(row.id, { expectedUpdatedAt: row.updatedAt })
    ElMessage.success(`${labels[definition.action]}成功`)
    await load()
    if (detail.value?.id === row.id) await openDetail({ id: row.id })
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.response?.data?.message || '状态更新失败，请刷新后重试')
  }
}
function search() { page.current = 1; load() }
function reset() { Object.assign(filters, { workOrderNo: '', productId: '', status: '', dates: [] }); search() }
onMounted(() => {
  load()
  listProducts({ page: 1, size: 100 }).then(({ data }) => { products.value = data.data.records })
    .catch((error) => ElMessage.error(error.response?.data?.message || '产品筛选项加载失败'))
})
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="app-main wide-main">
      <div class="page-heading"><div><p class="page-kicker">生产执行</p><h1>生产工单</h1></div><el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate">从计划创建工单</el-button></div>
      <section class="filter-row work-order-filters">
        <el-input v-model="filters.workOrderNo" clearable placeholder="工单编号" class="wo-number" @keyup.enter="search" />
        <el-select v-model="filters.productId" clearable placeholder="全部产品" class="wo-product"><el-option v-for="option in productOptions" :key="option.id" :label="option.label" :value="option.id" /></el-select>
        <el-select v-model="filters.status" clearable placeholder="全部状态" class="filter-status"><el-option v-for="status in statuses" :key="status.value" :label="status.label" :value="status.value" /></el-select>
        <el-date-picker v-model="filters.dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="计划开始" end-placeholder="计划结束" range-separator="至" class="wo-dates" />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button :icon="Refresh" @click="reset">重置</el-button>
      </section>
      <el-table v-loading="loading" :data="rows" row-key="id" class="data-table" empty-text="暂无生产工单">
        <el-table-column prop="workOrderNo" label="工单编号" min-width="205" />
        <el-table-column prop="planNo" label="关联计划" min-width="160" />
        <el-table-column label="产品" min-width="190"><template #default="scope"><strong>{{ scope.row.productName }}</strong><small class="wo-subtext">{{ scope.row.productCode }}</small></template></el-table-column>
        <el-table-column label="计划数量" width="120"><template #default="scope">{{ scope.row.plannedQty }} {{ scope.row.unit }}</template></el-table-column>
        <el-table-column prop="startDate" label="计划开始" width="120" />
        <el-table-column prop="endDate" label="计划完成" width="120" />
        <el-table-column label="报工进度" min-width="150"><template #default="scope"><span>{{ scope.row.completedQty }} / {{ scope.row.plannedQty }} {{ scope.row.unit }}</span><el-progress :percentage="Math.min(100, Math.round(scope.row.completedQty * 100 / scope.row.plannedQty))" :show-text="false" /></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="statusInfo(scope.row.status).type" effect="plain">{{ statusInfo(scope.row.status).label }}</el-tag></template></el-table-column>
        <el-table-column label="操作" :width="canManage ? 210 : 72" fixed="right"><template #default="scope">
          <el-button link type="primary" :icon="View" title="工单详情" aria-label="工单详情" @click="openDetail(scope.row)" />
          <template v-if="canManage"><el-button v-for="action in actionDefinitions.filter((item) => item.status === scope.row.status)" :key="action.action" link :type="action.action === 'complete' ? 'success' : 'primary'" :icon="action.icon" :title="action.label" :aria-label="action.label" @click="transition(scope.row, action)" /></template>
        </template></el-table-column>
      </el-table>
      <div class="table-footer"><span>共 {{ total }} 条</span><el-pagination v-model:current-page="page.current" v-model:page-size="page.size" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" :total="total" @current-change="load" @size-change="search" /></div>
    </main>

    <el-dialog v-model="createVisible" title="从生产计划创建工单" width="580px" destroy-on-close>
      <el-form label-width="100px"><el-form-item label="生产计划"><el-select v-model="selectedPlan" filterable style="width: 100%"><el-option v-for="plan in plans" :key="plan.planId" :value="plan.planId" :label="`${plan.planNo} · ${plan.productName} · ${plan.planQty} ${plan.unit}`" /></el-select></el-form-item>
        <el-form-item v-if="plans.find((plan) => plan.planId === selectedPlan)" label="关联订单">{{ plans.find((plan) => plan.planId === selectedPlan).orderNo }}</el-form-item>
        <el-form-item v-if="plans.find((plan) => plan.planId === selectedPlan)" label="工单数量">{{ plans.find((plan) => plan.planId === selectedPlan).planQty }} {{ plans.find((plan) => plan.planId === selectedPlan).unit }}</el-form-item>
        <el-form-item label="备注"><el-input v-model="remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="creating" @click="create">创建工单</el-button></template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="生产工单详情" width="850px">
      <template v-if="detail"><el-descriptions :column="2" border>
        <el-descriptions-item label="工单编号">{{ detail.workOrderNo }}</el-descriptions-item><el-descriptions-item label="状态"><el-tag :type="statusInfo(detail.status).type">{{ statusInfo(detail.status).label }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="生产计划">{{ detail.planNo }}</el-descriptions-item><el-descriptions-item label="生产订单">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="产品">{{ detail.productCode }} · {{ detail.productName }}</el-descriptions-item><el-descriptions-item label="规格">{{ detail.specification || '—' }}</el-descriptions-item>
        <el-descriptions-item label="计划数量">{{ detail.plannedQty }} {{ detail.unit }}</el-descriptions-item><el-descriptions-item label="报工进度">{{ detail.completedQty }} / {{ detail.plannedQty }} {{ detail.unit }}</el-descriptions-item>
        <el-descriptions-item label="计划开始">{{ detail.startDate }}</el-descriptions-item><el-descriptions-item label="计划完成">{{ detail.endDate }}</el-descriptions-item>
        <el-descriptions-item label="合格数量">{{ detail.goodQty }} {{ detail.unit }}</el-descriptions-item><el-descriptions-item label="报废数量">{{ detail.scrapQty }} {{ detail.unit }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createdByName }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
      <div class="report-heading"><h2>关联报工记录</h2><span>{{ reportTotal }} 条</span></div>
      <el-table :data="reportRows" size="small" empty-text="暂无报工记录"><el-table-column prop="reportNo" label="报工编号" min-width="180" /><el-table-column prop="reportDate" label="日期" width="115" /><el-table-column prop="reporterName" label="报工人" width="110" /><el-table-column prop="goodQty" label="合格量" width="90" /><el-table-column prop="scrapQty" label="报废量" width="90" /><el-table-column label="状态" width="95"><template #default="scope"><el-tag :type="scope.row.reportStatus === 'VALID' ? 'success' : 'info'" effect="plain">{{ scope.row.reportStatus === 'VALID' ? '有效' : '已作废' }}</el-tag></template></el-table-column></el-table>
      <el-pagination v-if="reportTotal > reportPage.size" v-model:current-page="reportPage.current" :page-size="reportPage.size" layout="prev, pager, next" :total="reportTotal" @current-change="loadReports" class="report-pagination" />
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.work-order-filters { flex-wrap: wrap; }
.wo-number { width: 190px; }
.wo-product { width: 240px; }
.wo-dates { width: 270px; }
.wo-subtext { display: block; margin-top: 3px; color: var(--muted); font-size: 12px; }
.report-heading { display: flex; align-items: baseline; gap: 12px; margin: 24px 0 12px; }
.report-heading h2 { margin: 0; font-size: 16px; }
.report-heading span { color: var(--muted); font-size: 13px; }
.report-pagination { justify-content: flex-end; margin-top: 12px; }
@media (max-width: 760px) { .wo-number, .wo-product, .wo-dates { width: 100%; } }
</style>
