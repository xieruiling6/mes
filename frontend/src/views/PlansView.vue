<script setup>
import AppHeader from '../components/AppHeader.vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, View, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { listProducts } from '../api/products'
import { cancelPlan, createPlan, getPlan, listAvailableOrders, listPlans, releasePlan, updatePlan } from '../api/plans'

const auth = useAuthStore()
const canManage = computed(() => auth.user?.permissions?.includes('plan:manage'))
const canPublish = computed(() => auth.user?.permissions?.includes('plan:publish'))
const canCancel = computed(() => auth.user?.permissions?.includes('plan:cancel'))
const canReadProducts = computed(() => ['product:read', 'product:manage'].some((p) => auth.user?.permissions?.includes(p)))
const canReadProcesses = computed(() => ['process:read', 'process:manage'].some((p) => auth.user?.permissions?.includes(p)))
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const products = ref([])
const orders = ref([])
const filters = reactive({ planNo: '', orderNo: '', productId: '', status: '', priority: null, startDates: [], endDates: [] })
const page = reactive({ current: 1, size: 10 })
const dialog = reactive({ visible: false, saving: false, mode: 'create', id: null, expectedUpdatedAt: null })
const formRef = ref()
const form = reactive({ productionOrderId: null, planQty: null, startDate: '', endDate: '', priority: 0, remark: '' })
const detail = ref(null)
const detailVisible = ref(false)
const statuses = [
  { value: 'DRAFT', label: '草稿', type: 'info' }, { value: 'RELEASED', label: '已发布', type: 'warning' },
  { value: 'IN_PROGRESS', label: '生产中', type: 'primary' }, { value: 'COMPLETED', label: '已完成', type: 'success' },
  { value: 'CANCELLED', label: '已取消', type: 'danger' },
]
const statusInfo = (status) => statuses.find((item) => item.value === status) || { label: status, type: 'info' }
const workOrderStatuses = [
  { value: 'PENDING', label: '待下发' }, { value: 'RELEASED', label: '已下发' }, { value: 'IN_PROGRESS', label: '生产中' },
  { value: 'PAUSED', label: '已暂停' }, { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' },
]
const workOrderStatusLabel = (status) => workOrderStatuses.find((item) => item.value === status)?.label || status
const rules = {
  productionOrderId: [{ required: true, message: '请选择已确认订单', trigger: 'change' }],
  planQty: [{ required: true, message: '订单数量不能为空', trigger: 'change' }, { type: 'number', min: 1, message: '数量必须大于零', trigger: 'change' }],
  startDate: [{ required: true, message: '请选择计划开始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择计划完成日期', trigger: 'change' }],
  priority: [{ type: 'number', min: 0, max: 32767, message: '优先级范围为 0 到 32767', trigger: 'change' }],
}

async function load() {
  loading.value = true
  const [startDateFrom, startDateTo] = filters.startDates || []
  const [endDateFrom, endDateTo] = filters.endDates || []
  try {
    const { data } = await listPlans({ page: page.current, size: page.size, planNo: filters.planNo || undefined,
      orderNo: filters.orderNo || undefined, productId: filters.productId || undefined, status: filters.status || undefined,
      priority: filters.priority ?? undefined, startDateFrom: startDateFrom || undefined, startDateTo: startDateTo || undefined,
      endDateFrom: endDateFrom || undefined, endDateTo: endDateTo || undefined })
    rows.value = data.data.records
    total.value = data.data.total
  } catch (error) { ElMessage.error(error.response?.data?.message || '计划列表加载失败') }
  finally { loading.value = false }
}
async function loadProducts() {
  const { data } = await listProducts({ page: 1, size: 100, status: 'ACTIVE' })
  products.value = data.data.records
}
async function openCreate() {
  try {
    const [orderResponse] = await Promise.all([listAvailableOrders(), loadProducts()])
    orders.value = orderResponse.data.data
    if (!orders.value.length) { ElMessage.info('当前没有可排产订单'); return }
    dialog.mode = 'create'; dialog.id = null; dialog.expectedUpdatedAt = null
    Object.assign(form, { productionOrderId: null, planQty: null, startDate: '', endDate: '', priority: 0, remark: '' })
    dialog.visible = true
  } catch (error) { ElMessage.error(error.response?.data?.message || '可排产订单加载失败') }
}
function selectOrder(id) {
  const order = orders.value.find((item) => item.orderId === id)
  if (order) form.planQty = order.orderQty
}
async function openEdit(row) {
  try {
    const { data } = await getPlan(row.id)
    const plan = data.data
    dialog.mode = 'edit'; dialog.id = row.id; dialog.expectedUpdatedAt = plan.updatedAt
    Object.assign(form, { productionOrderId: plan.productionOrderId, planQty: plan.planQty,
      startDate: plan.startDate, endDate: plan.endDate, priority: plan.priority, remark: plan.remark || '' })
    dialog.visible = true
  } catch (error) { ElMessage.error(error.response?.data?.message || '计划详情加载失败') }
}
async function save() {
  try { await formRef.value.validate() } catch { return }
  dialog.saving = true
  try {
    const payload = { planQty: form.planQty, startDate: form.startDate, endDate: form.endDate,
      priority: form.priority ?? 0, remark: form.remark || null }
    if (dialog.mode === 'create') await createPlan({ ...payload, productionOrderId: form.productionOrderId })
    else await updatePlan(dialog.id, { ...payload, expectedUpdatedAt: dialog.expectedUpdatedAt })
    ElMessage.success(dialog.mode === 'create' ? '生产计划已创建' : '生产计划已更新')
    dialog.visible = false
    await load()
  } catch (error) { ElMessage.error(error.response?.data?.message || '保存失败，请刷新计划后重试') }
  finally { dialog.saving = false }
}
async function showDetail(row) {
  try { const { data } = await getPlan(row.id); detail.value = data.data; detailVisible.value = true }
  catch (error) { ElMessage.error(error.response?.data?.message || '计划详情加载失败') }
}
async function changeStatus(row, action) {
  const release = action === 'release'
  try {
    await ElMessageBox.confirm(release ? `发布计划“${row.planNo}”？` : `取消计划“${row.planNo}”？`,
      release ? '发布生产计划' : '取消生产计划', { type: 'warning' })
    const request = { expectedUpdatedAt: row.updatedAt }
    if (release) await releasePlan(row.id, request)
    else await cancelPlan(row.id, request)
    ElMessage.success(release ? '计划已发布，可创建生产工单' : '计划已取消')
    await load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error.response?.data?.message || '状态更新失败') }
}
function search() { page.current = 1; load() }
function reset() { Object.assign(filters, { planNo: '', orderNo: '', productId: '', status: '', priority: null, startDates: [], endDates: [] }); search() }
onMounted(() => {
  load()
  loadProducts().catch((error) => ElMessage.error(error.response?.data?.message || '产品筛选项加载失败'))
})
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="app-main wide-main">
      <div class="page-heading"><div><p class="page-kicker">生产业务</p><h1>生产计划</h1></div><el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate">新建计划</el-button></div>
      <section class="filter-row plan-filters">
        <el-input v-model="filters.planNo" clearable placeholder="计划编号" class="plan-filter" @keyup.enter="search" />
        <el-input v-model="filters.orderNo" clearable placeholder="订单编号" class="plan-filter" @keyup.enter="search" />
        <el-select v-model="filters.productId" clearable filterable placeholder="全部产品" class="plan-product"><el-option v-for="product in products" :key="product.id" :label="`${product.productCode} · ${product.productName}`" :value="product.id" /></el-select>
        <el-select v-model="filters.status" clearable placeholder="全部状态" class="filter-status"><el-option v-for="status in statuses" :key="status.value" :label="status.label" :value="status.value" /></el-select>
        <el-input-number v-model="filters.priority" :min="0" :max="32767" :precision="0" controls-position="right" placeholder="优先级" class="plan-priority-filter" />
        <el-date-picker v-model="filters.startDates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="开始日期至" range-separator="至" class="plan-dates" />
        <el-date-picker v-model="filters.endDates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="完成日期" end-placeholder="完成日期至" range-separator="至" class="plan-dates" />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button :icon="Refresh" @click="reset">重置</el-button>
      </section>
      <el-table v-loading="loading" :data="rows" row-key="id" class="data-table" empty-text="暂无生产计划">
        <el-table-column prop="planNo" label="计划编号" min-width="190" />
        <el-table-column prop="orderNo" label="订单编号" min-width="150" />
        <el-table-column label="产品" min-width="190"><template #default="scope"><strong>{{ scope.row.productName }}</strong><small class="plan-subtext">{{ scope.row.productCode }}</small></template></el-table-column>
        <el-table-column label="计划数量" width="120"><template #default="scope">{{ scope.row.planQty }} {{ scope.row.unit }}</template></el-table-column>
        <el-table-column prop="startDate" label="计划开始" width="120" />
        <el-table-column prop="endDate" label="计划完成" width="120" />
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column label="完成" width="130"><template #default="scope">{{ scope.row.completedQty }} / {{ scope.row.planQty }} {{ scope.row.unit }}</template></el-table-column>
        <el-table-column label="状态" width="105"><template #default="scope"><el-tag :type="statusInfo(scope.row.status).type" effect="plain">{{ statusInfo(scope.row.status).label }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right"><template #default="scope">
          <el-button link type="primary" :icon="View" title="计划详情" aria-label="计划详情" @click="showDetail(scope.row)" />
          <el-button v-if="canManage && scope.row.status === 'DRAFT'" link type="primary" :icon="Edit" title="编辑计划" aria-label="编辑计划" @click="openEdit(scope.row)" />
          <el-button v-if="canPublish && scope.row.status === 'DRAFT'" link type="success" :icon="CircleCheck" title="发布计划" aria-label="发布计划" @click="changeStatus(scope.row, 'release')" />
          <el-button v-if="canCancel && ['DRAFT','RELEASED'].includes(scope.row.status)" link type="danger" :icon="CircleClose" title="取消计划" aria-label="取消计划" @click="changeStatus(scope.row, 'cancel')" />
        </template></el-table-column>
      </el-table>
      <div class="table-footer"><span>共 {{ total }} 条</span><el-pagination v-model:current-page="page.current" v-model:page-size="page.size" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" :total="total" @current-change="load" @size-change="search" /></div>
    </main>

    <el-dialog v-model="dialog.visible" :title="dialog.mode === 'create' ? '新建生产计划' : '编辑生产计划'" width="580px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="105px">
        <el-form-item v-if="dialog.mode === 'create'" label="生产订单" prop="productionOrderId"><el-select v-model="form.productionOrderId" filterable placeholder="选择已确认订单" style="width: 100%" @change="selectOrder"><el-option v-for="order in orders" :key="order.orderId" :label="`${order.orderNo} · ${order.productName} · ${order.orderQty} ${order.unit}`" :value="order.orderId" /></el-select></el-form-item>
        <el-form-item v-else label="生产订单">{{ detail?.orderNo || rows.find((row) => row.id === dialog.id)?.orderNo }}</el-form-item>
        <el-form-item label="计划数量" prop="planQty"><el-input-number v-model="form.planQty" :min="1" :precision="0" controls-position="right" :disabled="dialog.mode === 'create' && !!form.productionOrderId" style="width: 100%" /></el-form-item>
        <el-form-item label="计划日期" required><div class="plan-date-fields"><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" /><el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="完成日期" /></div></el-form-item>
        <el-form-item label="优先级" prop="priority"><el-input-number v-model="form.priority" :min="0" :max="32767" :precision="0" controls-position="right" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog.visible = false">取消</el-button><el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="detailVisible" title="生产计划详情" width="680px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="计划编号">{{ detail.planNo }}</el-descriptions-item><el-descriptions-item label="状态"><el-tag :type="statusInfo(detail.status).type">{{ statusInfo(detail.status).label }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="订单编号">{{ detail.orderNo }}</el-descriptions-item><el-descriptions-item label="产品">{{ detail.productCode }} · {{ detail.productName }}</el-descriptions-item>
        <el-descriptions-item label="计划数量">{{ detail.planQty }} {{ detail.unit }}</el-descriptions-item><el-descriptions-item label="订单交期">{{ detail.dueDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="计划开始">{{ detail.startDate }}</el-descriptions-item><el-descriptions-item label="计划完成">{{ detail.endDate }}</el-descriptions-item>
        <el-descriptions-item label="优先级">{{ detail.priority }}</el-descriptions-item><el-descriptions-item label="工单数">{{ detail.workOrderCount }}</el-descriptions-item>
        <el-descriptions-item label="关联工单">{{ detail.workOrderId ? `${detail.workOrderNo}（${workOrderStatusLabel(detail.workOrderStatus)}）` : '尚未创建' }}</el-descriptions-item><el-descriptions-item label="工单数量">{{ detail.workOrderId ? `${detail.workOrderPlannedQty} ${detail.unit}` : '—' }}</el-descriptions-item>
        <el-descriptions-item label="合格数量">{{ detail.goodQty }} {{ detail.unit }}</el-descriptions-item><el-descriptions-item label="报废数量">{{ detail.scrapQty }} {{ detail.unit }}</el-descriptions-item>
        <el-descriptions-item label="完成数量">{{ detail.completedQty }} / {{ detail.planQty }} {{ detail.unit }}</el-descriptions-item><el-descriptions-item label="已完成工单">{{ detail.completedWorkOrderCount }} / {{ detail.workOrderCount }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createdByName }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped>
.plan-filters { flex-wrap: wrap; }
.plan-filter { width: 170px; }
.plan-product { width: 230px; }
.plan-priority-filter { width: 130px; }
.plan-dates { width: 260px; }
.plan-subtext { display: block; margin-top: 3px; color: var(--muted); font-size: 12px; }
.plan-date-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; width: 100%; }
@media (max-width: 760px) {
  .plan-filter, .plan-product, .plan-priority-filter, .plan-dates { width: 100%; }
  .plan-date-fields { grid-template-columns: 1fr; }
}
</style>
