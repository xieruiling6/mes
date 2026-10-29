<script setup>
import AppHeader from '../components/AppHeader.vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, View, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { listProcesses, getProcess, createProcess, updateProcess, updateProcessStatus } from '../api/processes'

const auth = useAuthStore()
const canManage = computed(() => auth.user?.permissions?.includes('process:manage'))
const canReadProducts = computed(() => ['product:read', 'product:manage'].some((permission) => auth.user?.permissions?.includes(permission)))
const canReadOrders = computed(() => ['order:read', 'order:manage'].some((permission) => auth.user?.permissions?.includes(permission)))
const canReadPlans = computed(() => ['plan:read', 'plan:manage'].some((permission) => auth.user?.permissions?.includes(permission)))
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const filters = reactive({ keyword: '', status: '' })
const page = reactive({ current: 1, size: 10 })
const dialog = reactive({ visible: false, mode: 'create', saving: false, id: null })
const detail = ref(null)
const detailVisible = ref(false)
const formRef = ref()
const form = reactive({ processCode: '', processName: '', description: '' })
const rules = {
  processCode: [{ required: true, message: '请输入工序编码', trigger: 'blur' }, { max: 40, message: '最多 40 个字符', trigger: 'blur' }],
  processName: [{ required: true, message: '请输入工序名称', trigger: 'blur' }, { max: 120, message: '最多 120 个字符', trigger: 'blur' }],
  description: [{ max: 500, message: '最多 500 个字符', trigger: 'blur' }],
}
async function load() {
  loading.value = true
  try { const { data } = await listProcesses({ page: page.current, size: page.size, keyword: filters.keyword || undefined, status: filters.status || undefined }); rows.value = data.data.records; total.value = data.data.total }
  catch (error) { ElMessage.error(error.response?.data?.message || '工序列表加载失败') }
  finally { loading.value = false }
}
function search() { page.current = 1; load() }
function reset() { filters.keyword = ''; filters.status = ''; search() }
function openCreate() { dialog.mode = 'create'; dialog.id = null; Object.assign(form, { processCode: '', processName: '', description: '' }); dialog.visible = true }
function openEdit(row) { dialog.mode = 'edit'; dialog.id = row.id; Object.assign(form, { processCode: row.processCode, processName: row.processName, description: row.description || '' }); dialog.visible = true }
async function save() {
  await formRef.value.validate(); dialog.saving = true
  try { if (dialog.mode === 'create') await createProcess({ ...form }); else await updateProcess(dialog.id, { ...form }); ElMessage.success('工序已保存'); dialog.visible = false; await load() }
  catch (error) { if (error !== false) ElMessage.error(error.response?.data?.message || '保存失败') }
  finally { dialog.saving = false }
}
async function showDetail(row) { try { const { data } = await getProcess(row.id); detail.value = data.data; detailVisible.value = true } catch (error) { ElMessage.error(error.response?.data?.message || '工序详情加载失败') } }
async function toggleStatus(row) {
  const next = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  try { await ElMessageBox.confirm(`确认${next === 'ACTIVE' ? '启用' : '停用'}工序“${row.processName}”吗？`, '状态变更', { type: 'warning' }); await updateProcessStatus(row.id, next); ElMessage.success('工序状态已更新'); await load() }
  catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error.response?.data?.message || '状态更新失败') }
}
onMounted(load)
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="app-main wide-main">
      <div class="page-heading"><div><p class="page-kicker">基础资料</p><h1>工序管理</h1></div><el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate">新增工序</el-button></div>
      <section class="filter-row"><el-input v-model="filters.keyword" clearable placeholder="搜索工序编码或名称" class="filter-keyword" @keyup.enter="search" /><el-select v-model="filters.status" clearable placeholder="全部状态" class="filter-status"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="INACTIVE" /></el-select><el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button :icon="Refresh" @click="reset">重置</el-button></section>
      <el-table v-loading="loading" :data="rows" row-key="id" class="data-table" empty-text="暂无工序"><el-table-column prop="processCode" label="工序编码" min-width="160" /><el-table-column prop="processName" label="工序名称" min-width="190" /><el-table-column prop="description" label="说明" min-width="240"><template #default="scope">{{ scope.row.description || '—' }}</template></el-table-column><el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="更新时间" min-width="175"><template #default="scope">{{ scope.row.updatedAt || scope.row.createdAt || '—' }}</template></el-table-column><el-table-column label="操作" width="190" fixed="right"><template #default="scope"><el-button link type="primary" :icon="View" title="查看详情" aria-label="查看详情" @click="showDetail(scope.row)" /><el-button v-if="canManage" link type="primary" :icon="Edit" title="编辑工序" aria-label="编辑工序" @click="openEdit(scope.row)" /><el-button v-if="canManage" link :type="scope.row.status === 'ACTIVE' ? 'danger' : 'success'" :icon="scope.row.status === 'ACTIVE' ? CircleClose : CircleCheck" :title="scope.row.status === 'ACTIVE' ? '停用工序' : '启用工序'" :aria-label="scope.row.status === 'ACTIVE' ? '停用工序' : '启用工序'" @click="toggleStatus(scope.row)" /></template></el-table-column></el-table>
      <div class="table-footer"><span>共 {{ total }} 条</span><el-pagination v-model:current-page="page.current" v-model:page-size="page.size" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" :total="total" @current-change="load" @size-change="search" /></div>
    </main>
    <el-dialog v-model="dialog.visible" :title="dialog.mode === 'create' ? '新增工序' : '编辑工序'" width="520px" destroy-on-close><el-form ref="formRef" :model="form" :rules="rules" label-width="90px"><el-form-item label="工序编码" prop="processCode"><el-input v-model="form.processCode" maxlength="40" /></el-form-item><el-form-item label="工序名称" prop="processName"><el-input v-model="form.processName" maxlength="120" /></el-form-item><el-form-item label="说明" prop="description"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item></el-form><template #footer><el-button @click="dialog.visible = false">取消</el-button><el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button></template></el-dialog>
    <el-dialog v-model="detailVisible" title="工序详情" width="520px"><el-descriptions v-if="detail" :column="1" border><el-descriptions-item label="工序编码">{{ detail.processCode }}</el-descriptions-item><el-descriptions-item label="工序名称">{{ detail.processName }}</el-descriptions-item><el-descriptions-item label="说明">{{ detail.description || '—' }}</el-descriptions-item><el-descriptions-item label="状态">{{ detail.status === 'ACTIVE' ? '启用' : '停用' }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item><el-descriptions-item label="更新时间">{{ detail.updatedAt }}</el-descriptions-item></el-descriptions></el-dialog>
  </div>
</template>
