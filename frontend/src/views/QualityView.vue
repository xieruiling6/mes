<script setup>
import AppHeader from '../components/AppHeader.vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { createQualityRecord, disposeQualityRecord, getQualityOptions, listQualityRecords, listQualityReports } from '../api/quality'
const auth = useAuthStore(), router = useRouter()
const permissions = computed(() => auth.user?.permissions || [])
const canCreate = computed(() => permissions.value.includes('quality:create'))
const canRead = computed(() => permissions.value.includes('quality:read'))
const canDispose = computed(() => permissions.value.includes('quality:dispose'))
const rows = ref([]), orders = ref([]), reports = ref([]), total = ref(0), loading = ref(false), dialog = ref(false)
const disposeDialog = reactive({ visible: false, saving: false, id: null, status: 'REWORK', note: '' })
const page = reactive({ current: 1, size: 10 }), filters = reactive({ workOrderId: '', reportId: '', status: '', dates: [] })
const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }
const form = reactive({ workOrderId: '', reportId: '', inspectionDate: today(), inspectionQty: '', goodQty: '', unqualifiedQty: '', unqualifiedReason: '', remark: '' })
const dispositionLabels = { NOT_REQUIRED: '无需处置', PENDING: '待处理', ACCEPTED: '让步接收', REWORK: '返工', SCRAPPED: '报废' }
const headerLinks = [['/', '工作台'], ['/work-orders', '生产工单'], ['/reports', '生产报工'], ['/quality', '质量管理'], ['/equipment', '设备管理']]
watch(() => form.workOrderId, async (id) => { form.reportId = ''; reports.value = []; if (id) { try { reports.value = (await listQualityReports(id)).data.data } catch (e) { ElMessage.error(e.response?.data?.message || '报工记录加载失败') } } })
async function load() {
  loading.value = true
  const [dateFrom, dateTo] = filters.dates || []
  try { const r = await listQualityRecords({ page: page.current, size: page.size, workOrderId: filters.workOrderId || undefined, reportId: filters.reportId || undefined, status: filters.status || undefined, dateFrom: dateFrom || undefined, dateTo: dateTo || undefined }); rows.value = r.data.data.records; total.value = r.data.data.total }
  catch (e) { ElMessage.error(e.response?.data?.message || '质检记录加载失败') } finally { loading.value = false }
}
async function options() { try { orders.value = (await getQualityOptions()).data.data.workOrders } catch (e) { ElMessage.error(e.response?.data?.message || '工单列表加载失败') } }
function resetForm() { Object.assign(form, { workOrderId: '', reportId: '', inspectionDate: today(), inspectionQty: '', goodQty: '', unqualifiedQty: '', unqualifiedReason: '', remark: '' }) }
async function save() {
  const qty = Number(form.inspectionQty), good = Number(form.goodQty), bad = Number(form.unqualifiedQty)
  if (!form.workOrderId || !qty || good < 0 || bad < 0 || good + bad !== qty || (bad > 0 && !form.unqualifiedReason.trim())) { ElMessage.warning('请核对工单、检验数量、合格数量和不合格原因'); return }
  try { await createQualityRecord({ workOrderId: Number(form.workOrderId), reportId: form.reportId ? Number(form.reportId) : null, inspectionDate: form.inspectionDate, inspectionQty: qty, goodQty: good, unqualifiedQty: bad, unqualifiedReason: form.unqualifiedReason.trim() || null, remark: form.remark.trim() || null }); ElMessage.success('质检记录已保存到系统'); dialog.value = false; resetForm(); await load() }
  catch (e) { ElMessage.error(e.response?.data?.message || '创建质检记录失败') }
}
function openDispose(row) {
  Object.assign(disposeDialog, { visible: true, saving: false, id: row.id, status: 'REWORK', note: '' })
}
async function submitDispose() {
  if (!disposeDialog.note.trim()) { ElMessage.warning('请填写处置说明'); return }
  disposeDialog.saving = true
  try {
    await disposeQualityRecord(disposeDialog.id, { status: disposeDialog.status, note: disposeDialog.note.trim() })
    ElMessage.success('处置结果已记录')
    disposeDialog.visible = false
    await load()
  } catch (e) { ElMessage.error(e.response?.data?.message || '处置失败') }
  finally { disposeDialog.saving = false }
}
async function signOut() { try { await auth.logout() } catch {} finally { await router.replace('/login') } }
onMounted(async () => { await Promise.all([options(), load()]) })
</script>
<template>
  <div class="app-shell">
    <AppHeader />
    <main class="app-main wide-main"><div class="page-heading"><div><p class="page-kicker">质量管理</p><h1>质检记录</h1></div><el-button v-if="canCreate" type="primary" :icon="Plus" @click="dialog = true">新增检验</el-button></div>
      <section class="filter-row report-filters"><el-select v-model="filters.workOrderId" clearable filterable placeholder="全部工单"><el-option v-for="item in orders" :key="item.id" :value="item.id" :label="item.workOrderNo" /></el-select><el-input v-model="filters.reportId" clearable placeholder="报工记录 ID" /><el-select v-model="filters.status" clearable placeholder="全部处置状态"><el-option v-for="(label,key) in dispositionLabels" :key="key" :label="label" :value="key" /></el-select><el-date-picker v-model="filters.dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="检验日期起" end-placeholder="检验日期止" /><el-button type="primary" :icon="Search" @click="page.current=1;load()">查询</el-button><el-button :icon="Refresh" @click="filters.workOrderId='';filters.reportId='';filters.status='';filters.dates=[];page.current=1;load()">重置</el-button></section>
      <el-table v-loading="loading" :data="rows" class="data-table" empty-text="暂无质检记录"><el-table-column prop="inspectionNo" label="质检编号" min-width="195"/><el-table-column prop="workOrderNo" label="生产工单" min-width="155"/><el-table-column label="产品" min-width="150"><template #default="s">{{ s.row.productCode }} · {{ s.row.productName }}</template></el-table-column><el-table-column prop="reportNo" label="关联报工" min-width="190"><template #default="s">{{ s.row.reportNo || '按工单检验' }}</template></el-table-column><el-table-column prop="inspectionDate" label="检验日期" width="112"/><el-table-column prop="inspectionQty" label="检验数" width="85"/><el-table-column prop="goodQty" label="合格数" width="85"/><el-table-column prop="unqualifiedQty" label="不合格" width="90"/><el-table-column label="处置状态" width="110"><template #default="s"><el-tag effect="plain" :type="s.row.dispositionStatus==='PENDING'?'warning':s.row.dispositionStatus==='SCRAPPED'?'danger':'success'">{{ dispositionLabels[s.row.dispositionStatus] }}</el-tag></template></el-table-column><el-table-column v-if="canDispose" label="操作" width="100" fixed="right"><template #default="s"><el-button v-if="s.row.dispositionStatus==='PENDING'" link type="primary" @click="openDispose(s.row)">处置</el-button></template></el-table-column></el-table>
      <div class="table-footer"><span>共 {{ total }} 条</span><el-pagination v-model:current-page="page.current" v-model:page-size="page.size" :page-sizes="[10,20,50]" layout="sizes, prev, pager, next" :total="total" @current-change="load" @size-change="page.current=1;load()"/></div>
    </main>
    <el-dialog v-model="disposeDialog.visible" title="不合格品处置" width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="处置结果"><el-select v-model="disposeDialog.status" style="width: 100%"><el-option label="让步接收" value="ACCEPTED" /><el-option label="返工" value="REWORK" /><el-option label="报废" value="SCRAPPED" /></el-select></el-form-item>
        <el-form-item label="处置说明"><el-input v-model="disposeDialog.note" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明处置方式和责任信息" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="disposeDialog.visible = false">取消</el-button><el-button type="primary" :loading="disposeDialog.saving" @click="submitDispose">确认处置</el-button></template>
    </el-dialog>
    <el-dialog v-model="dialog" title="新增质检记录" width="640px" destroy-on-close><el-form label-position="top"><div class="qe-form-grid"><el-form-item label="生产工单" required><el-select v-model="form.workOrderId" filterable class="full-width"><el-option v-for="item in orders" :key="item.id" :value="item.id" :label="item.workOrderNo"/></el-select></el-form-item><el-form-item label="报工记录"><el-select v-model="form.reportId" clearable class="full-width" placeholder="可选，按工单汇总检验"><el-option v-for="item in reports" :key="item.id" :value="item.id" :label="item.reportNo+' · 合格 '+item.goodQty"/></el-select></el-form-item><el-form-item label="检验日期" required><el-date-picker v-model="form.inspectionDate" type="date" value-format="YYYY-MM-DD" class="full-width"/></el-form-item><el-form-item label="检验数量" required><el-input v-model="form.inspectionQty" type="number" min="1"/></el-form-item><el-form-item label="合格数量" required><el-input v-model="form.goodQty" type="number" min="0"/></el-form-item><el-form-item label="不合格数量" required><el-input v-model="form.unqualifiedQty" type="number" min="0"/></el-form-item><el-form-item label="不合格原因"><el-input v-model="form.unqualifiedReason" maxlength="500"/></el-form-item><el-form-item label="备注"><el-input v-model="form.remark" maxlength="500"/></el-form-item></div></el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">保存检验</el-button></template></el-dialog>
  </div>
</template>
<style scoped>
.report-filters{flex-wrap:wrap}.report-filters :deep(.el-input),.report-filters :deep(.el-select){width:160px}.qe-form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 18px}.full-width{width:100%}@media(max-width:700px){.qe-form-grid{grid-template-columns:1fr}.report-filters>*{flex:1 1 100%;width:100%!important}}
</style>
