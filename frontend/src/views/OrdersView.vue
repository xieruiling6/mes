<script setup>
import AppHeader from '../components/AppHeader.vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, View, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { listProducts } from '../api/products'
import { cancelOrder, confirmOrder, createOrder, getOrder, getOrderExecution, listOrders, updateOrder } from '../api/orders'

const auth = useAuthStore()
const canManage = computed(() => auth.user?.permissions?.includes('order:manage'))
const canConfirm = computed(() => auth.user?.permissions?.includes('order:confirm'))
const canCancel = computed(() => auth.user?.permissions?.includes('order:cancel'))
const canReadPlans = computed(() => ['plan:read', 'plan:manage'].some((permission) => auth.user?.permissions?.includes(permission)))
const canReadProducts = computed(() => ['product:read', 'product:manage'].some((permission) => auth.user?.permissions?.includes(permission)))
const canReadProcesses = computed(() => ['process:read', 'process:manage'].some((permission) => auth.user?.permissions?.includes(permission)))
const loading = ref(false)
const productsLoading = ref(false)
const products = ref([])
const rows = ref([])
const total = ref(0)
const filters = reactive({ orderNo: '', productId: '', status: '', dueDates: [] })
const page = reactive({ current: 1, size: 10 })
const dialog = reactive({ visible: false, saving: false, mode: 'create', id: null, expectedUpdatedAt: null })
const formRef = ref()
const form = reactive({ orderNo: '', productId: null, orderQty: null, dueDate: '', remark: '' })
const detail = ref(null)
const detailExecution = ref(null)
const detailVisible = ref(false)
const statuses = [
  { value: 'DRAFT', label: '草稿', type: 'info' },
  { value: 'CONFIRMED', label: '已确认', type: 'warning' },
  { value: 'IN_PROGRESS', label: '生产中', type: 'primary' },
  { value: 'COMPLETED', label: '已完成', type: 'success' },
  { value: 'CANCELLED', label: '已取消', type: 'danger' },
]
const statusInfo = (status) => statuses.find((item) => item.value === status) || { label: status, type: 'info' }
const rules = {
  orderNo: [{ required: true, message: '请输入订单编号', trigger: 'blur' }, { max: 40, message: '最多 40 个字符', trigger: 'blur' }],
  productId: [{ required: true, message: '请选择启用产品', trigger: 'change' }],
  orderQty: [{ required: true, message: '请输入生产数量', trigger: 'blur' }, { type: 'number', min: 1, message: '数量必须大于零', trigger: 'change' }],
}

async function load() {
  loading.value = true
  const [dueDateFrom, dueDateTo] = filters.dueDates || []
  try {
    const { data } = await listOrders({ page: page.current, size: page.size, orderNo: filters.orderNo || undefined,
      productId: filters.productId || undefined, status: filters.status || undefined,
      dueDateFrom: dueDateFrom || undefined, dueDateTo: dueDateTo || undefined })
    rows.value = data.data.records
    total.value = data.data.total
  } catch (error) { ElMessage.error(error.response?.data?.message || '订单列表加载失败') }
  finally { loading.value = false }
}
async function loadProducts() {
  productsLoading.value = true
  try {
    const { data } = await listProducts({ page: 1, size: 100, status: 'ACTIVE' })
    products.value = data.data.records
  } catch (error) { ElMessage.error(error.response?.data?.message || '产品列表加载失败') }
  finally { productsLoading.value = false }
}
function search() { page.current = 1; load() }
function reset() { Object.assign(filters, { orderNo: '', productId: '', status: '', dueDates: [] }); search() }
async function openCreate() {
  await loadProducts()
  dialog.mode = 'create'; dialog.id = null; dialog.expectedUpdatedAt = null
  Object.assign(form, { orderNo: '', productId: null, orderQty: null, dueDate: '', remark: '' })
  dialog.visible = true
}
async function openEdit(row) {
  if (row.status !== 'DRAFT' && row.status !== 'CONFIRMED') return
  if (row.hasPlan) { ElMessage.warning('订单已有生产计划，不能在此页面编辑'); return }
  await loadProducts()
  try {
    const { data } = await getOrder(row.id)
    const order = data.data
    dialog.mode = 'edit'; dialog.id = row.id; dialog.expectedUpdatedAt = order.updatedAt
    Object.assign(form, { orderNo: order.orderNo, productId: order.productId, orderQty: order.orderQty,
      dueDate: order.dueDate || '', remark: order.remark || '' })
    dialog.visible = true
  } catch (error) { ElMessage.error(error.response?.data?.message || '订单详情加载失败') }
}
async function save() {
  try { await formRef.value.validate() } catch { return }
  dialog.saving = true
  try {
    const payload = { ...form, dueDate: form.dueDate || null }
    if (dialog.mode === 'create') await createOrder(payload)
    else await updateOrder(dialog.id, { ...payload, expectedUpdatedAt: dialog.expectedUpdatedAt })
    ElMessage.success(dialog.mode === 'create' ? '生产订单已创建' : '生产订单已更新')
    dialog.visible = false
    await load()
  } catch (error) { ElMessage.error(error.response?.data?.message || '保存失败，请刷新订单后重试') }
  finally { dialog.saving = false }
}
async function showDetail(row) {
  try {
    const [orderResponse, executionResponse] = await Promise.all([getOrder(row.id), getOrderExecution(row.id)])
    detail.value = orderResponse.data.data
    detailExecution.value = executionResponse.data.data
    detailVisible.value = true
  } catch (error) { ElMessage.error(error.response?.data?.message || '订单详情加载失败') }
}
const workOrderStatusLabel = (status) => statuses.find((item) => item.value === status)?.label || status
const completionRate = (completed, planned) => planned ? Math.min(100, Math.round(completed * 100 / planned)) : 0
async function changeStatus(row, action) {
  const isConfirm = action === 'confirm'
  try {
    await ElMessageBox.confirm(`确认${isConfirm ? '确认' : '取消'}生产订单“${row.orderNo}”吗？`, isConfirm ? '确认订单' : '取消订单', { type: 'warning' })
    if (isConfirm) await confirmOrder(row.id)
    else await cancelOrder(row.id)
    ElMessage.success(isConfirm ? '订单已确认' : '订单已取消')
    await load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error.response?.data?.message || '状态更新失败') }
}
onMounted(load)
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="app-main wide-main">
      <div class="page-heading"><div><p class="page-kicker">生产业务</p><h1>生产订单</h1></div><el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate">新建订单</el-button></div>
      <section class="filter-row order-filters">
        <el-input v-model="filters.orderNo" clearable placeholder="订单编号" class="order-filter-no" @keyup.enter="search" />
        <el-select v-model="filters.productId" clearable filterable placeholder="全部产品" class="order-filter-product"><el-option v-for="product in products" :key="product.id" :label="`${product.productCode} · ${product.productName}`" :value="product.id" /></el-select>
        <el-select v-model="filters.status" clearable placeholder="全部状态" class="filter-status"><el-option v-for="status in statuses" :key="status.value" :label="status.label" :value="status.value" /></el-select>
        <el-date-picker v-model="filters.dueDates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="交期开始" end-placeholder="交期结束" range-separator="至" class="order-filter-dates" />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button :icon="Refresh" @click="reset">重置</el-button>
      </section>
      <el-table v-loading="loading" :data="rows" row-key="id" class="data-table" empty-text="暂无生产订单">
        <el-table-column prop="orderNo" label="订单编号" min-width="150" />
        <el-table-column label="产品" min-width="220"><template #default="scope"><strong>{{ scope.row.productName }}</strong><small class="order-product-code">{{ scope.row.productCode }}</small></template></el-table-column>
        <el-table-column label="订单数量" width="130"><template #default="scope">{{ scope.row.orderQty }} {{ scope.row.unit }}</template></el-table-column>
        <el-table-column prop="dueDate" label="要求交期" width="130"><template #default="scope">{{ scope.row.dueDate || '—' }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="statusInfo(scope.row.status).type" effect="plain">{{ statusInfo(scope.row.status).label }}</el-tag></template></el-table-column>
        <el-table-column prop="createdByName" label="创建人" width="120" />
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="170" fixed="right"><template #default="scope">
          <el-button link type="primary" :icon="View" title="订单详情" aria-label="订单详情" @click="showDetail(scope.row)" />
          <el-button v-if="canManage && ['DRAFT','CONFIRMED'].includes(scope.row.status) && !scope.row.hasPlan" link type="primary" :icon="Edit" title="编辑订单" aria-label="编辑订单" @click="openEdit(scope.row)" />
          <el-button v-if="canConfirm && scope.row.status === 'DRAFT'" link type="success" :icon="CircleCheck" title="确认订单" aria-label="确认订单" @click="changeStatus(scope.row, 'confirm')" />
          <el-button v-if="canCancel && ['DRAFT','CONFIRMED'].includes(scope.row.status)" link type="danger" :icon="CircleClose" title="取消订单" aria-label="取消订单" @click="changeStatus(scope.row, 'cancel')" />
        </template></el-table-column>
      </el-table>
      <div class="table-footer"><span>共 {{ total }} 条</span><el-pagination v-model:current-page="page.current" v-model:page-size="page.size" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" :total="total" @current-change="load" @size-change="search" /></div>
    </main>

    <el-dialog v-model="dialog.visible" :title="dialog.mode === 'create' ? '新建生产订单' : '编辑生产订单'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="订单编号" prop="orderNo"><el-input v-model="form.orderNo" maxlength="40" /></el-form-item>
        <el-form-item label="生产产品" prop="productId"><el-select v-model="form.productId" filterable :loading="productsLoading" placeholder="请选择启用产品" style="width: 100%"><el-option v-for="product in products" :key="product.id" :label="`${product.productCode} · ${product.productName}`" :value="product.id" /></el-select></el-form-item>
        <el-form-item label="生产数量" prop="orderQty"><el-input-number v-model="form.orderQty" :min="1" :precision="0" :step="1" controls-position="right" style="width: 100%" /></el-form-item>
        <el-form-item label="要求交期"><el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" placeholder="选择交期" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog.visible = false">取消</el-button><el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="detailVisible" title="生产订单详情" width="760px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="订单编号">{{ detail.orderNo }}</el-descriptions-item><el-descriptions-item label="状态">{{ statusInfo(detail.status).label }}</el-descriptions-item>
        <el-descriptions-item label="产品">{{ detail.productCode }} · {{ detail.productName }}</el-descriptions-item><el-descriptions-item label="规格">{{ detail.specification || '—' }}</el-descriptions-item>
        <el-descriptions-item label="订单数量">{{ detail.orderQty }} {{ detail.unit }}</el-descriptions-item><el-descriptions-item label="要求交期">{{ detail.dueDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createdByName }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="更新时间" :span="2">{{ detail.updatedAt }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
      <section class="execution-panel">
        <div class="execution-heading"><h2>关联计划与执行</h2><span v-if="detailExecution?.planId">计划 → 工单 → 有效报工</span></div>
        <el-descriptions v-if="detailExecution?.planId" :column="2" border>
          <el-descriptions-item label="生产计划">{{ detailExecution.planNo }}（{{ statusInfo(detailExecution.planStatus).label }}）</el-descriptions-item>
          <el-descriptions-item label="计划数量">{{ detailExecution.planQty }} {{ detail.unit }}</el-descriptions-item>
          <el-descriptions-item label="计划开始">{{ detailExecution.startDate }}</el-descriptions-item><el-descriptions-item label="计划完成">{{ detailExecution.endDate }}</el-descriptions-item>
          <el-descriptions-item label="生产工单">{{ detailExecution.workOrderId ? `${detailExecution.workOrderNo}（${workOrderStatusLabel(detailExecution.workOrderStatus)}）` : '尚未创建' }}</el-descriptions-item>
          <el-descriptions-item label="工单数量">{{ detailExecution.workOrderId ? `${detailExecution.plannedQty} ${detail.unit}` : '—' }}</el-descriptions-item>
          <el-descriptions-item label="有效报工">{{ detailExecution.reportCount }} 条</el-descriptions-item>
          <el-descriptions-item label="合格 / 报废">{{ detailExecution.goodQty }} / {{ detailExecution.scrapQty }} {{ detail.unit }}</el-descriptions-item>
          <el-descriptions-item label="完成数量" :span="2">
            <div class="execution-progress"><span>{{ detailExecution.completedQty }} / {{ detailExecution.planQty }} {{ detail.unit }}</span><el-progress :percentage="completionRate(detailExecution.completedQty, detailExecution.planQty)" /></div>
          </el-descriptions-item>
        </el-descriptions>
        <el-empty v-else description="该订单尚未创建生产计划" :image-size="60" />
      </section>
    </el-dialog>
  </div>
</template>

<style scoped>
.order-filters { flex-wrap: wrap; }
.order-filter-no { width: 190px; }
.order-filter-product { width: 250px; }
.order-filter-dates { width: 280px; }
.order-product-code { display: block; margin-top: 3px; color: var(--muted); font-size: 12px; }
@media (max-width: 700px) {
  .order-filter-no, .order-filter-product, .order-filter-dates { width: 100%; }
}
.execution-panel { margin-top: 24px; }
.execution-heading { display: flex; align-items: baseline; gap: 12px; margin-bottom: 12px; }
.execution-heading h2 { margin: 0; font-size: 16px; }
.execution-heading span { color: var(--muted); font-size: 12px; }
.execution-progress { display: flex; align-items: center; gap: 16px; width: 100%; }
.execution-progress .el-progress { flex: 1; }
</style>
