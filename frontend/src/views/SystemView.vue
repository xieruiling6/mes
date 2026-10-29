<script setup>
import AppHeader from '../components/AppHeader.vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search, Refresh, Edit, Key, User, Setting } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { changePasswordRequest, updateProfileRequest } from '../api/auth'
import {
  createRole, createUser, getRolePermissionIds, getUserRoleIds, listPermissions, listRoles, listUsers,
  replaceRolePermissions, replaceUserRoles, updateRole, updateUser,
} from '../api/system'

const auth = useAuthStore()
const permissions = computed(() => auth.user?.permissions || [])
const canManageUsers = computed(() => permissions.value.includes('system:user:manage'))
const canManageRoles = computed(() => permissions.value.includes('system:role:manage'))
const canReadPermissions = computed(() => permissions.value.includes('system:permission:read'))
const canOpenSystem = computed(() => canManageUsers.value || canManageRoles.value || canReadPermissions.value)
const activeTab = ref(canManageUsers.value ? 'users' : canManageRoles.value ? 'roles' : 'profile')

const users = ref([])
const usersTotal = ref(0)
const usersLoading = ref(false)
const userPage = reactive({ current: 1, size: 10 })
const userFilter = reactive({ keyword: '' })
const roles = ref([])
const rolePermissionCounts = ref({})
const permissionRows = ref([])
const rolesLoading = ref(false)

const userDialog = reactive({ visible: false, saving: false, mode: 'create', id: null })
const userFormRef = ref()
const userForm = reactive({ username: '', displayName: '', status: 'ACTIVE', password: '', roleIds: [] })
const userRules = {
  username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }, { max: 64, message: '最多 64 个字符', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }, { max: 80, message: '最多 80 个字符', trigger: 'blur' }],
  password: [{
    validator: (_rule, value, callback) => {
      if (!value) return userDialog.mode === 'edit' ? callback() : callback(new Error('新用户必须设置至少 12 位密码'))
      if (value.length < 12) return callback(new Error('密码至少 12 位'))
      callback()
    },
    trigger: 'blur',
  }],
}

const roleDialog = reactive({ visible: false, saving: false, mode: 'create', id: null })
const roleFormRef = ref()
const roleForm = reactive({ roleCode: '', roleName: '', description: '', status: 'ACTIVE', permissionIds: [] })
const roleRules = {
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[A-Za-z][A-Za-z0-9_]{1,39}$/, message: '2-40 位字母、数字或下划线，且以字母开头', trigger: 'blur' },
  ],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }, { max: 80, message: '最多 80 个字符', trigger: 'blur' }],
}
const moduleLabels = {
  dashboard: '生产看板', product: '产品资料', process: '工序资料', order: '生产订单', plan: '生产计划',
  workorder: '生产工单', report: '生产报工', quality: '质量管理', equipment: '设备管理',
  statistics: '生产统计', system: '系统管理',
}
const moduleOrder = Object.keys(moduleLabels)
const criticalAdminPermissions = ['system:user:manage', 'system:role:manage']
const isAdminRole = computed(() => roleForm.roleCode.trim().toUpperCase() === 'ADMIN')
const isCriticalPermission = (permission) => isAdminRole.value && criticalAdminPermissions.includes(permission.permissionCode)
const permissionGroups = computed(() => {
  const groups = new Map()
  permissionRows.value.forEach((permission) => {
    const key = permission.moduleCode || 'other'
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(permission)
  })
  return [...groups.entries()]
    .sort(([a], [b]) => {
      const ia = moduleOrder.indexOf(a); const ib = moduleOrder.indexOf(b)
      return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib)
    })
    .map(([moduleCode, items]) => ({
      moduleCode, label: moduleLabels[moduleCode] || moduleCode, items,
    }))
})

const profileFormRef = ref()
const profileForm = reactive({ displayName: auth.user?.displayName || '' })
const profileRules = {
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }, { max: 80, message: '最多 80 个字符', trigger: 'blur' }],
}
const passwordFormRef = ref()
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const passwordRules = {
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 12, max: 100, message: '新密码至少 12 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => value === passwordForm.newPassword ? callback() : callback(new Error('两次输入的新密码不一致')),
      trigger: 'blur',
    },
  ],
}

async function loadUsers() {
  if (!canManageUsers.value) return
  usersLoading.value = true
  try {
    const { data } = await listUsers({ page: userPage.current, size: userPage.size, keyword: userFilter.keyword || undefined })
    const records = data.data.records
    await Promise.all(records.map(async (row) => {
      try { row.roleIds = (await getUserRoleIds(row.id)).data.data }
      catch { row.roleIds = [] }
    }))
    users.value = records
    usersTotal.value = data.data.total
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '用户列表加载失败')
  } finally { usersLoading.value = false }
}

async function loadRoles() {
  if (!(canManageRoles.value || canManageUsers.value)) return
  rolesLoading.value = true
  try {
    const { data } = await listRoles()
    roles.value = data.data
    const entries = await Promise.all(roles.value.map(async (role) => {
      try { return [role.id, (await getRolePermissionIds(role.id)).data.data.length] }
      catch { return [role.id, 0] }
    }))
    rolePermissionCounts.value = Object.fromEntries(entries)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '角色列表加载失败')
  } finally { rolesLoading.value = false }
}

async function loadPermissions() {
  if (!(canManageRoles.value || canReadPermissions.value)) return
  try {
    const { data } = await listPermissions()
    permissionRows.value = data.data
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '权限列表加载失败')
  }
}

function searchUsers() { userPage.current = 1; loadUsers() }
function resetUsers() { userFilter.keyword = ''; searchUsers() }

function openCreateUser() {
  userDialog.mode = 'create'; userDialog.id = null
  Object.assign(userForm, { username: '', displayName: '', status: 'ACTIVE', password: '', roleIds: [] })
  userDialog.visible = true
}
async function openEditUser(row) {
  userDialog.mode = 'edit'; userDialog.id = row.id
  Object.assign(userForm, {
    username: row.username, displayName: row.displayName, status: row.status, password: '',
    roleIds: [...(row.roleIds || [])],
  })
  userDialog.visible = true
}
async function saveUser() {
  try { await userFormRef.value.validate() } catch { return }
  userDialog.saving = true
  try {
    const payload = {
      username: userForm.username.trim(), displayName: userForm.displayName.trim(), status: userForm.status,
      roleIds: userForm.roleIds, password: userForm.password || null,
    }
    if (userDialog.mode === 'create') await createUser(payload)
    else await updateUser(userDialog.id, payload)
    ElMessage.success(userDialog.mode === 'create' ? '用户已创建' : '用户已更新')
    userDialog.visible = false
    await loadUsers()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '用户保存失败')
  } finally { userDialog.saving = false }
}
function roleLabel(roleId) {
  return roles.value.find((role) => role.id === roleId)?.roleName || `角色 ${roleId}`
}

function openCreateRole() {
  roleDialog.mode = 'create'; roleDialog.id = null
  Object.assign(roleForm, { roleCode: '', roleName: '', description: '', status: 'ACTIVE', permissionIds: [] })
  roleDialog.visible = true
}
async function openEditRole(row) {
  roleDialog.mode = 'edit'; roleDialog.id = row.id
  Object.assign(roleForm, {
    roleCode: row.roleCode, roleName: row.roleName, description: row.description || '',
    status: row.status, permissionIds: [],
  })
  try {
    roleForm.permissionIds = (await getRolePermissionIds(row.id)).data.data
    roleDialog.visible = true
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '角色权限加载失败')
  }
}
async function saveRole() {
  try { await roleFormRef.value.validate() } catch { return }
  roleDialog.saving = true
  try {
    const payload = {
      roleCode: roleForm.roleCode.trim().toUpperCase(), roleName: roleForm.roleName.trim(),
      description: roleForm.description.trim() || null, status: roleForm.status,
      permissionIds: roleForm.permissionIds,
    }
    if (roleDialog.mode === 'create') await createRole(payload)
    else await updateRole(roleDialog.id, payload)
    ElMessage.success(roleDialog.mode === 'create' ? '角色已创建' : '角色已更新')
    roleDialog.visible = false
    await loadRoles()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '角色保存失败')
  } finally { roleDialog.saving = false }
}

async function saveProfile() {
  try { await profileFormRef.value.validate() } catch { return }
  try {
    await updateProfileRequest({ displayName: profileForm.displayName.trim() })
    if (auth.user) auth.user.displayName = profileForm.displayName.trim()
    ElMessage.success('个人资料已更新')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '个人资料更新失败')
  }
}
async function changePassword() {
  try { await passwordFormRef.value.validate() } catch { return }
  try {
    await changePasswordRequest({
      currentPassword: passwordForm.currentPassword, newPassword: passwordForm.newPassword,
    })
    Object.assign(passwordForm, { currentPassword: '', newPassword: '', confirmPassword: '' })
    passwordFormRef.value.clearValidate()
    ElMessage.success('密码已修改，请使用新密码登录')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '密码修改失败')
  }
}

onMounted(() => Promise.all([loadUsers(), loadRoles(), loadPermissions()]))
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="app-main wide-main">
      <div class="page-heading">
        <div><p class="page-kicker">系统与安全</p><h1>系统管理</h1></div>
        <el-tag effect="plain" type="success">{{ auth.user?.displayName }}</el-tag>
      </div>

      <el-tabs v-model="activeTab" class="system-tabs">
        <el-tab-pane v-if="canManageUsers" name="users">
          <template #label><span class="tab-label"><el-icon><User /></el-icon>用户与角色</span></template>
          <section class="filter-row">
            <el-input v-model="userFilter.keyword" clearable placeholder="搜索登录账号或显示名称" class="filter-keyword" @keyup.enter="searchUsers" />
            <el-button type="primary" :icon="Search" @click="searchUsers">查询</el-button>
            <el-button :icon="Refresh" @click="resetUsers">重置</el-button>
            <el-button type="primary" :icon="Plus" class="push-right" @click="openCreateUser">新增用户</el-button>
          </section>
          <el-table v-loading="usersLoading" :data="users" row-key="id" class="data-table" empty-text="暂无用户">
            <el-table-column prop="username" label="登录账号" min-width="140" />
            <el-table-column prop="displayName" label="显示名称" min-width="140" />
            <el-table-column label="角色" min-width="220">
              <template #default="scope">
                <el-tag v-for="roleId in scope.row.roleIds" :key="roleId" effect="plain" class="row-tag">{{ roleLabel(roleId) }}</el-tag>
                <span v-if="!scope.row.roleIds?.length" class="muted">未分配</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="lastLoginAt" label="最近登录" min-width="175"><template #default="scope">{{ scope.row.lastLoginAt || '—' }}</template></el-table-column>
            <el-table-column prop="createdAt" label="创建时间" min-width="175" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="scope"><el-button link type="primary" :icon="Edit" @click="openEditUser(scope.row)">编辑</el-button></template>
            </el-table-column>
          </el-table>
          <div class="table-footer">
            <span>共 {{ usersTotal }} 条</span>
            <el-pagination v-model:current-page="userPage.current" v-model:page-size="userPage.size" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" :total="usersTotal" @current-change="loadUsers" @size-change="searchUsers" />
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="canManageRoles" name="roles">
          <template #label><span class="tab-label"><el-icon><Key /></el-icon>角色与权限</span></template>
          <section class="filter-row">
            <span class="section-hint">角色授予 API 权限编码；前端菜单和按钮显隐不能替代后端校验。</span>
            <el-button type="primary" :icon="Plus" class="push-right" @click="openCreateRole">新增角色</el-button>
          </section>
          <el-table v-loading="rolesLoading" :data="roles" row-key="id" class="data-table" empty-text="暂无角色">
            <el-table-column prop="roleCode" label="角色编码" min-width="180" />
            <el-table-column prop="roleName" label="角色名称" min-width="150" />
            <el-table-column prop="description" label="说明" min-width="240"><template #default="scope">{{ scope.row.description || '—' }}</template></el-table-column>
            <el-table-column label="可用权限" width="120" align="right">
              <template #default="scope">
                {{ rolePermissionCounts[scope.row.id] ?? '—' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="scope"><el-button link type="primary" :icon="Edit" @click="openEditRole(scope.row)">编辑</el-button></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane name="profile">
          <template #label><span class="tab-label"><el-icon><Setting /></el-icon>个人设置</span></template>
          <section class="profile-grid">
            <div class="panel-block">
              <h2>个人资料</h2>
              <p class="panel-hint">登录账号：<strong>{{ auth.user?.username }}</strong>；角色：{{ (auth.user?.roles || []).join('、') || '未分配' }}</p>
              <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="90px">
                <el-form-item label="显示名称" prop="displayName"><el-input v-model="profileForm.displayName" maxlength="80" /></el-form-item>
                <el-form-item><el-button type="primary" @click="saveProfile">保存资料</el-button></el-form-item>
              </el-form>
            </div>
            <div class="panel-block">
              <h2>修改密码</h2>
              <p class="panel-hint">修改成功后当前会话继续有效，下次登录请使用新密码。</p>
              <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="100px">
                <el-form-item label="当前密码" prop="currentPassword"><el-input v-model="passwordForm.currentPassword" type="password" show-password autocomplete="current-password" /></el-form-item>
                <el-form-item label="新密码" prop="newPassword"><el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" placeholder="至少 12 位" /></el-form-item>
                <el-form-item label="确认新密码" prop="confirmPassword"><el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
                <el-form-item><el-button type="primary" @click="changePassword">修改密码</el-button></el-form-item>
              </el-form>
            </div>
          </section>
          <section v-if="canOpenSystem" class="panel-block security-note">
            <h2>账号安全约束</h2>
            <p>停用用户不能登录；系统必须保留至少一名启用状态的管理员。最后一名管理员不能被停用，也不能移除其管理员角色。</p>
          </section>
        </el-tab-pane>
      </el-tabs>
    </main>

    <el-dialog v-model="userDialog.visible" :title="userDialog.mode === 'create' ? '新增用户' : '编辑用户'" width="560px" destroy-on-close>
      <el-form ref="userFormRef" :model="userForm" :rules="userRules" label-width="95px">
        <el-form-item label="登录账号" prop="username"><el-input v-model="userForm.username" maxlength="64" :disabled="userDialog.mode === 'edit'" /></el-form-item>
        <el-form-item label="显示名称" prop="displayName"><el-input v-model="userForm.displayName" maxlength="80" /></el-form-item>
        <el-form-item label="密码" prop="password"><el-input v-model="userForm.password" type="password" show-password autocomplete="new-password" :placeholder="userDialog.mode === 'create' ? '至少 12 位' : '留空表示不修改密码'" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="userForm.status"><el-radio value="ACTIVE">启用</el-radio><el-radio value="DISABLED">停用</el-radio></el-radio-group></el-form-item>
        <el-form-item label="角色"><el-select v-model="userForm.roleIds" multiple filterable placeholder="可分配多个角色" style="width: 100%"><el-option v-for="role in roles" :key="role.id" :value="role.id" :label="`${role.roleName}（${role.roleCode}）`" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="userDialog.visible = false">取消</el-button><el-button type="primary" :loading="userDialog.saving" @click="saveUser">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="roleDialog.visible" class="role-dialog" :title="roleDialog.mode === 'create' ? '新增角色' : '编辑角色与权限'" width="760px" destroy-on-close>
      <el-form ref="roleFormRef" :model="roleForm" :rules="roleRules" label-width="90px">
        <el-form-item label="角色编码" prop="roleCode"><el-input v-model="roleForm.roleCode" maxlength="40" :disabled="roleDialog.mode === 'edit'" placeholder="例如 QC_INSPECTOR" /></el-form-item>
        <el-form-item label="角色名称" prop="roleName"><el-input v-model="roleForm.roleName" maxlength="80" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="roleForm.description" maxlength="255" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="roleForm.status"><el-radio value="ACTIVE">启用</el-radio><el-radio value="DISABLED">停用</el-radio></el-radio-group></el-form-item>
        <el-form-item label="权限">
          <el-checkbox-group v-model="roleForm.permissionIds" class="permission-groups">
            <div v-for="group in permissionGroups" :key="group.moduleCode" class="permission-group">
              <h4>{{ group.label }}</h4>
              <el-checkbox v-for="permission in group.items" :key="permission.id" :value="permission.id" :disabled="isCriticalPermission(permission)">
                {{ permission.permissionName }}<code>{{ permission.permissionCode }}</code>
              </el-checkbox>
            </div>
            <p v-if="isAdminRole" class="admin-role-hint">ADMIN 角色必须保留“管理用户”和“管理角色”权限，避免系统失去管理入口。</p>
            <p v-if="!permissionGroups.length" class="muted">无可分配权限</p>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="roleDialog.visible = false">取消</el-button><el-button type="primary" :loading="roleDialog.saving" @click="saveRole">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.system-tabs { margin-top: 22px; }
.tab-label { display: inline-flex; align-items: center; gap: 6px; }
.push-right { margin-left: auto; }
.section-hint { color: var(--muted); font-size: 13px; }
.muted { color: var(--faint); font-size: 12px; }
.row-tag { margin: 2px 6px 2px 0; }
.profile-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: start; gap: 24px; }
:deep(.role-dialog .el-dialog__body) { max-height: 62vh; overflow-y: auto; }
.panel-block { padding: 22px 24px; border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--surface); box-shadow: var(--shadow-xs); }
.panel-block h2 { margin: 0 0 8px; font-size: 16px; }
.panel-hint { margin: 0 0 20px; color: var(--muted); font-size: 13px; line-height: 1.6; }
.security-note { margin-top: 24px; }
.security-note p { margin: 0; color: var(--ink-soft); font-size: 13px; line-height: 1.7; }
.permission-groups { display: grid; width: 100%; gap: 14px; }
.permission-group { padding: 12px 14px; border: 1px solid var(--line); border-radius: var(--radius); background: var(--surface-muted); }
.permission-group h4 { margin: 0 0 8px; color: var(--ink); font-size: 13px; }
.permission-group :deep(.el-checkbox) { min-width: 230px; margin-right: 14px; }
.permission-group code { margin-left: 6px; color: var(--muted); font-size: 11px; }
.admin-role-hint { margin: 0; color: var(--warning); font-size: 12px; }
@media (max-width: 900px) {
  .profile-grid { grid-template-columns: 1fr; }
  .permission-group :deep(.el-checkbox) { min-width: 100%; }
}
</style>
