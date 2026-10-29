<script setup>
import AppHeader from '../components/AppHeader.vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, View, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { listProducts, getProduct, createProduct, updateProduct, updateProductStatus } from '../api/products'

const auth = useAuthStore()
const canManage = computed(() => auth.user?.permissions?.includes('product:manage'))
const canReadProcesses = computed(() => ['process:read', 'process:manage'].some((permission) => auth.user?.permissions?.includes(permission)))
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
const form = reactive({ productCode: '', productName: '', specification: '', unit: '' })
const rules = {
  productCode: [{ required: true, message: '请输入产品编码', trigger: 'blur' }, { max: 40, message: '最多 40 个字符', trigger: 'blur' }],
  productName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }, { max: 120, message: '最多 120 个字符', trigger: 'blur' }],
  specification: [{ max: 120, message: '最多 120 个字符', trigger: 'blur' }],
  unit: [{ required: true, message: '请输入计量单位', trigger: 'blur' }, { max: 20, message: '最多 20 个字符', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const { data } = await listProducts({ page: page.current, size: page.size, keyword: filters.keyword || undefined, status: filters.status || undefined })
    rows.value = data.data.records
    total.value = data.data.total
  } catch (error) { ElMessage.error(error.response?.data?.message || '产品列表加载失败') }
  finally { loading.value = false }
}
function search() { page.current = 1; load() }
function reset() { filters.keyword = ''; filters.status = ''; search() }
function openCreate() {
  dialog.mode = 'create'; dialog.id = null
  Object.assign(form, { productCode: '', productName: '', specification: '', unit: '' })
  dialog.visible = true
}
function openEdit(row) {
  dialog.mode = 'edit'; dialog.id = row.id
  Object.assign(form, { productCode: row.productCode, productName: row.productName, specification: row.specification || '', unit: row.unit })
  dialog.visible = true
}
async function save() {
  await formRef.value.validate()
  dialog.saving = true
  try {
    const payload = { ...form }
    if (dialog.mode === 'create') await createProduct(payload)
    else await updateProduct(dialog.id, payload)
    ElMessage.success(dialog.mode === 'create' ? '产品已创建' : '产品已更新')
    dialog.visible = false
    await load()
  } catch (error) { if (error !== false) ElMessage.error(error.response?.data?.message || '保存失败') }
  finally { dialog.saving = false }
}
async function showDetail(row) {
  try { const { data } = await getProduct(row.id); detail.value = data.data; detailVisible.value = true }
  catch (error) { ElMessage.error(error.response?.data?.message || '产品详情加载失败') }
}
async function toggleStatus(row) {
  const next = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  try {
    await ElMessageBox.confirm(`确认${next === 'ACTIVE' ? '启用' : '停用'}产品“${row.productName}”吗？`, '状态变更', { type: 'warning' })
    await updateProductStatus(row.id, next)
    ElMessage.success('产品状态已更新')
    await load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error.response?.data?.message || '状态更新失败') }
}
onMounted(load)
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="app-main wide-main">
      <div class="page-heading"><div><p class="page-kicker">基础资料</p><h1>产品管理</h1></div><el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate">新增产品</el-button></div>
      <section class="filter-row">
        <el-input v-model="filters.keyword" clearable placeholder="搜索产品编码或名称" class="filter-keyword" @keyup.enter="search" />
        <el-select v-model="filters.status" clearable placeholder="全部状态" class="filter-status"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="INACTIVE" /></el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button :icon="Refresh" @click="reset">重置</el-button>
      </section>
      <el-table v-loading="loading" :data="rows" row-key="id" class="data-table" empty-text="暂无产品">
        <el-table-column prop="productCode" label="产品编码" min-width="150" />
        <el-table-column prop="productName" label="产品名称" min-width="180" />
        <el-table-column prop="specification" label="规格" min-width="160"><template #default="scope">{{ scope.row.specification || '—' }}</template></el-table-column>
        <el-table-column prop="unit" label="单位" width="100" />
        <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="更新时间" min-width="175"><template #default="scope">{{ scope.row.updatedAt || scope.row.createdAt || '—' }}</template></el-table-column>
        <el-table-column label="操作" width="190" fixed="right"><template #default="scope">
          <el-button link type="primary" :icon="View" title="查看详情" aria-label="查看详情" @click="showDetail(scope.row)" />
          <el-button v-if="canManage" link type="primary" :icon="Edit" title="编辑产品" aria-label="编辑产品" @click="openEdit(scope.row)" />
          <el-button v-if="canManage" link :type="scope.row.status === 'ACTIVE' ? 'danger' : 'success'" :icon="scope.row.status === 'ACTIVE' ? CircleClose : CircleCheck" :title="scope.row.status === 'ACTIVE' ? '停用产品' : '启用产品'" :aria-label="scope.row.status === 'ACTIVE' ? '停用产品' : '启用产品'" @click="toggleStatus(scope.row)" />
        </template></el-table-column>
      </el-table>
      <div class="table-footer"><span>共 {{ total }} 条</span><el-pagination v-model:current-page="page.current" v-model:page-size="page.size" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" :total="total" @current-change="load" @size-change="search" /></div>
    </main>

    <el-dialog v-model="dialog.visible" :title="dialog.mode === 'create' ? '新增产品' : '编辑产品'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px"><el-form-item label="产品编码" prop="productCode"><el-input v-model="form.productCode" maxlength="40" /></el-form-item><el-form-item label="产品名称" prop="productName"><el-input v-model="form.productName" maxlength="120" /></el-form-item><el-form-item label="规格" prop="specification"><el-input v-model="form.specification" maxlength="120" /></el-form-item><el-form-item label="计量单位" prop="unit"><el-input v-model="form.unit" maxlength="20" /></el-form-item></el-form>
      <template #footer><el-button @click="dialog.visible = false">取消</el-button><el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="detailVisible" title="产品详情" width="520px"><el-descriptions v-if="detail" :column="1" border><el-descriptions-item label="产品编码">{{ detail.productCode }}</el-descriptions-item><el-descriptions-item label="产品名称">{{ detail.productName }}</el-descriptions-item><el-descriptions-item label="规格">{{ detail.specification || '—' }}</el-descriptions-item><el-descriptions-item label="单位">{{ detail.unit }}</el-descriptions-item><el-descriptions-item label="状态">{{ detail.status === 'ACTIVE' ? '启用' : '停用' }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item><el-descriptions-item label="更新时间">{{ detail.updatedAt }}</el-descriptions-item></el-descriptions></el-dialog>
  </div>
</template>
