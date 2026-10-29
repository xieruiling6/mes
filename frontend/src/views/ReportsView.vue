<script setup>
import AppHeader from '../components/AppHeader.vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, Plus, CircleClose } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { getReportFilterOptions, getWorkOrderReportSummary, listAvailableReportWorkOrders, listMyReports, listReports, submitReport, voidReport } from '../api/reports'

const auth = useAuthStore()
const router = useRouter()
const permissions = computed(() => auth.user?.permissions || [])
const canCreate = computed(() => permissions.value.includes('report:create'))
const canReadAll = computed(() => permissions.value.includes('report:read'))
const canVoid = computed(() => permissions.value.includes('report:void'))
const activeTab = ref(canCreate.value ? 'submit' : 'records')
const loading = ref(false)
const submitting = ref(false)
const workOrders = ref([])
const summary = ref(null)
const rows = ref([])
const products = ref([])
const reporters = ref([])
const total = ref(0)
const page = reactive({ current: 1, size: 10 })
const filters = reactive({ dates: [], productId: '', reporterId: '', reportStatus: '' })
const localToday = () => {
  const today = new Date()
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
}
const form = reactive({ workOrderId: '', reportDate: localToday(), productionQty: '', goodQty: '', unqualifiedQty: '', remark: '' })
const requestKey = ref(crypto.randomUUID())
const keyedPayload = ref('')
const totalFromQuality = computed(() => Number(form.goodQty || 0) + Number(form.unqualifiedQty || 0))
const remaining = computed(() => summary.value ? Math.max(0, summary.value.plannedQty - summary.value.productionQty) : 0)
const selectedWorkOrder = computed(() => workOrders.value.find((item) => item.workOrderId === Number(form.workOrderId)))
const statusLabel = (status) => status === 'VALID' ? '有效' : '已作废'

watch(() => form.workOrderId, async (id) => {
  summary.value = null
  if (!id) return
  try { summary.value = (await getWorkOrderReportSummary(id)).data.data }
  catch (error) { ElMessage.error(error.response?.data?.message || '工单累计数据加载失败') }
})

async function loadAvailable() {
  try { workOrders.value = (await listAvailableReportWorkOrders()).data.data }
  catch (error) { ElMessage.error(error.response?.data?.message || '可报工工单加载失败') }
}

async function loadRecords() {
  loading.value = true
  const [dateFrom, dateTo] = filters.dates || []
  const params = { page: page.current, size: page.size, dateFrom: dateFrom || undefined, dateTo: dateTo || undefined,
    productId: filters.productId || undefined, reporterId: canReadAll.value ? (filters.reporterId || undefined) : undefined,
    reportStatus: filters.reportStatus || undefined }
  try {
    const response = canReadAll.value ? await listReports(params) : await listMyReports(params)
    rows.value = response.data.data.records
    total.value = response.data.data.total
  } catch (error) { ElMessage.error(error.response?.data?.message || '报工记录加载失败') }
  finally { loading.value = false }
}

function makePayload() {
  return { requestKey: requestKey.value, workOrderId: Number(form.workOrderId), reportDate: form.reportDate,
    productionQty: Number(form.productionQty), goodQty: Number(form.goodQty), unqualifiedQty: Number(form.unqualifiedQty),
    remark: form.remark.trim() || null }
}

async function submit() {
  if (!form.workOrderId || !form.reportDate || Number(form.productionQty) <= 0 || Number(form.goodQty) < 0 ||
      Number(form.unqualifiedQty) < 0 || totalFromQuality.value !== Number(form.productionQty)) {
    ElMessage.warning('请检查工单、日期和数量，生产数量必须等于合格数与不合格数之和')
    return
  }
  const payload = makePayload()
  const fingerprint = JSON.stringify({ ...payload, requestKey: undefined })
  if (keyedPayload.value && keyedPayload.value !== fingerprint) requestKey.value = crypto.randomUUID()
  payload.requestKey = requestKey.value
  keyedPayload.value = fingerprint
  submitting.value = true
  try {
    await submitReport(payload)
    ElMessage.success('报工已提交')
    Object.assign(form, { productionQty: '', goodQty: '', unqualifiedQty: '', remark: '' })
    requestKey.value = crypto.randomUUID()
    keyedPayload.value = ''
    await Promise.all([loadAvailable(), loadRecords()])
    if (form.workOrderId) summary.value = (await getWorkOrderReportSummary(form.workOrderId)).data.data
  } catch (error) { ElMessage.error(error.response?.data?.message || '提交失败；重试时会沿用本次请求键') }
  finally { submitting.value = false }
}

function search() { page.current = 1; loadRecords() }
function reset() { Object.assign(filters, { dates: [], productId: '', reporterId: '', reportStatus: '' }); search() }
async function correctReport(row) {
  try {
    const { value } = await ElMessageBox.prompt('填写更正原因。原报工数据将保留为作废记录。', '作废报工', {
      confirmButtonText: '确认作废', cancelButtonText: '取消', inputType: 'textarea',
      inputValidator: (value) => Boolean(value?.trim()) || '请填写作废原因',
    })
    await voidReport(row.id, value.trim())
    ElMessage.success('报工已作废，原记录已保留')
    await loadRecords()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.response?.data?.message || '作废失败')
  }
}
async function signOut() {
  try { await auth.logout(); ElMessage.success('已退出登录') }
  catch { ElMessage.warning('会话已结束') }
  finally { await router.replace('/login') }
}

onMounted(async () => {
  const [options] = await Promise.all([getReportFilterOptions().catch((error) => {
    ElMessage.error(error.response?.data?.message || '筛选项加载失败')
    return null
  }), canCreate.value ? loadAvailable() : Promise.resolve(), loadRecords()])
  products.value = options?.data?.data?.products || []
  reporters.value = options?.data?.data?.reporters || []
})
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="app-main wide-main report-page">
      <div class="page-heading"><div><p class="page-kicker">车间执行</p><h1>生产报工</h1></div></div>
      <el-tabs v-model="activeTab" @tab-change="activeTab === 'records' && loadRecords()">
        <el-tab-pane v-if="canCreate" label="提交报工" name="submit">
          <section class="report-entry">
            <div class="report-entry-head">
              <div>
                <p class="entry-kicker">现场数据采集</p>
                <h2>新建报工单</h2>
                <p class="entry-desc">选择执行中的工单，录入当班产量；系统会实时校验数量并汇总工单进度。</p>
              </div>
              <div v-if="selectedWorkOrder" class="entry-workorder">
                <span>当前工单</span>
                <strong>{{ selectedWorkOrder.workOrderNo }}</strong>
                <small>{{ selectedWorkOrder.productName }}</small>
              </div>
            </div>
            <el-form label-position="top" class="report-form">
              <el-form-item label="生产工单" required>
                <el-select v-model="form.workOrderId" filterable placeholder="选择生产中的工单" class="full-width">
                  <el-option v-for="order in workOrders" :key="order.workOrderId" :value="order.workOrderId"
                    :label="`${order.workOrderNo} · ${order.productName} · 剩余 ${order.plannedQty - order.reportedQty} ${order.unit}`" />
                </el-select>
              </el-form-item>
              <div class="form-grid">
                <el-form-item label="报工日期" required><el-date-picker v-model="form.reportDate" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item>
                <el-form-item label="本次生产数量" required><el-input v-model="form.productionQty" type="number" min="1" step="1" /></el-form-item>
                <el-form-item label="合格数量" required><el-input v-model="form.goodQty" type="number" min="0" step="1" /></el-form-item>
                <el-form-item label="不合格数量" required><el-input v-model="form.unqualifiedQty" type="number" min="0" step="1" /></el-form-item>
                <el-form-item label="备注" class="form-note"><el-input v-model="form.remark" maxlength="500" show-word-limit /></el-form-item>
              </div>
              <div v-if="summary" class="summary-strip">
                <span>工单计划 <strong>{{ summary.plannedQty }} {{ selectedWorkOrder?.unit }}</strong></span>
                <span>已报工 <strong>{{ summary.productionQty }}</strong></span>
                <span>合格 <strong>{{ summary.goodQty }}</strong></span>
                <span>不合格 <strong>{{ summary.unqualifiedQty }}</strong></span>
                <span>剩余 <strong>{{ remaining }}</strong></span>
              </div>
              <div class="form-actions"><span v-if="form.productionQty">数量核对：{{ totalFromQuality }} / {{ form.productionQty }}</span><el-button type="primary" :icon="Plus" :loading="submitting" @click="submit">提交报工</el-button></div>
            </el-form>
          </section>
        </el-tab-pane>
        <el-tab-pane label="报工记录" name="records">
          <section class="filter-row report-filters">
            <el-date-picker v-model="filters.dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" />
            <el-select v-model="filters.productId" placeholder="全部产品" clearable filterable><el-option v-for="product in products" :key="product.id" :value="product.id" :label="`${product.productCode} · ${product.productName}`" /></el-select>
            <el-select v-if="canReadAll" v-model="filters.reporterId" placeholder="全部报工人" clearable filterable><el-option v-for="reporter in reporters" :key="reporter.reporterId" :value="reporter.reporterId" :label="reporter.reporterName" /></el-select>
            <el-select v-model="filters.reportStatus" placeholder="全部状态" clearable><el-option label="有效" value="VALID" /><el-option label="已作废" value="VOID" /></el-select>
            <el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button :icon="Refresh" @click="reset">重置</el-button>
          </section>
          <el-table v-loading="loading" :data="rows" class="data-table" empty-text="暂无报工记录">
            <el-table-column prop="reportNo" label="报工编号" min-width="190" />
            <el-table-column prop="workOrderNo" label="工单" min-width="175" />
            <el-table-column prop="productName" label="产品" min-width="145" />
            <el-table-column prop="reportDate" label="日期" width="115" />
            <el-table-column prop="reporterName" label="报工人" width="110" />
            <el-table-column prop="productionQty" label="生产数量" width="105" />
            <el-table-column prop="goodQty" label="合格" width="85" />
            <el-table-column prop="unqualifiedQty" label="不合格" width="90" />
            <el-table-column label="状态" width="95"><template #default="scope"><el-tag :type="scope.row.reportStatus === 'VALID' ? 'success' : 'info'" effect="plain">{{ statusLabel(scope.row.reportStatus) }}</el-tag></template></el-table-column>
            <el-table-column v-if="canVoid" label="操作" width="90" fixed="right"><template #default="scope"><el-button v-if="scope.row.reportStatus === 'VALID'" link type="danger" :icon="CircleClose" @click="correctReport(scope.row)">作废</el-button></template></el-table-column>
          </el-table>
          <div class="table-footer"><span>共 {{ total }} 条</span><el-pagination v-model:current-page="page.current" v-model:page-size="page.size" :page-sizes="[10,20,50]" layout="sizes, prev, pager, next" :total="total" @current-change="loadRecords" @size-change="search" /></div>
        </el-tab-pane>
      </el-tabs>
    </main>
  </div>
</template>

<style scoped>
.report-page { max-width: 1320px; }
.report-entry {
  padding: 28px 30px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
}
.report-entry-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 28px;
  margin-bottom: 24px;
  padding-bottom: 22px;
  border-bottom: 1px solid var(--line);
}
.entry-kicker {
  margin: 0 0 6px;
  color: var(--brand);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}
.report-entry-head h2 { margin: 0 0 6px; color: var(--ink); font-size: 20px; letter-spacing: -0.01em; }
.entry-desc { max-width: 680px; margin: 0; color: var(--muted); font-size: 13px; line-height: 1.7; }
.entry-workorder {
  display: flex;
  min-width: 196px;
  flex-direction: column;
  gap: 3px;
  padding: 13px 16px;
  background: #f3faf8;
  border: 1px solid #d8ece7;
  border-radius: 12px;
}
.entry-workorder span { color: var(--muted); font-size: 11px; letter-spacing: 0.08em; }
.entry-workorder strong { color: var(--ink); font-size: 14px; }
.entry-workorder small { overflow: hidden; color: var(--muted); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.report-form { max-width: 1120px; }
.form-grid { display: grid; grid-template-columns: repeat(4, minmax(130px, 1fr)); gap: 0 16px; }
.form-note { grid-column: 1 / -1; }
.full-width { width: 100%; }
.summary-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
  padding: 16px 20px;
  background: #f7fbfa;
  border: 1px solid var(--line);
  border-radius: 12px;
}
.summary-strip span { display: flex; flex-direction: column; gap: 4px; color: var(--muted); font-size: 12px; }
.summary-strip strong { color: var(--ink); font-size: 17px; font-weight: 650; }
.form-actions { display: flex; justify-content: flex-end; align-items: center; gap: 16px; padding-top: 20px; margin-top: 4px; border-top: 1px solid var(--line); }
.form-actions span { margin-right: auto; color: var(--muted); font-size: 13px; }
.report-filters { flex-wrap: wrap; }
.report-filters :deep(.el-input) { width: 135px; }
.report-filters :deep(.el-select) { width: 140px; }
@media (max-width: 1000px) { .summary-strip { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 760px) {
  .report-entry { padding: 22px 18px; }
  .report-entry-head { flex-direction: column; gap: 16px; }
  .entry-workorder { width: 100%; }
  .form-grid { grid-template-columns: repeat(2, minmax(120px, 1fr)); }
  .summary-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 16px; }
}
@media (max-width: 480px) {
  .form-grid { grid-template-columns: 1fr; }
  .form-note { grid-column: auto; }
  .report-filters > * { flex: 1 1 100%; width: 100% !important; }
  .form-actions { align-items: flex-end; }
}
</style>
