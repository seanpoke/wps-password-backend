<template>
  <div class="user-page">
    <h2 class="page-title">用户管理</h2>

    <el-card shadow="never" class="panel">
      <!-- 筛选栏：仅点击「查询」按钮才请求后端 -->
      <div class="filter-bar">
        <span class="f-label">名称</span>
        <el-input v-model="query.keyword" placeholder="请输入账号/姓名" class="f-input" clearable @keyup.enter="onSearch" />
        <span class="f-label">来源</span>
        <el-select v-model="query.source" class="f-select" placeholder="全部来源" clearable>
          <el-option v-for="s in sourceOptions" :key="s.code" :label="s.label" :value="s.code" />
        </el-select>
        <span class="f-label">角色</span>
        <el-select v-model="query.roleId" class="f-select" placeholder="全部角色" clearable>
          <el-option v-for="r in allRoles" :key="r.id" :label="r.name" :value="r.id" />
        </el-select>
        <div class="f-actions">
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
          <el-button type="primary" :icon="Search" :loading="loading" @click="onSearch">查询</el-button>
          <el-button type="success" :icon="Plus" @click="openAdd">新建用户</el-button>
        </div>
      </div>

      <el-table :data="rows" v-loading="loading" class="tbl" row-key="id">
        <el-table-column prop="account" label="账号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="name" label="姓名" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="部门" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deptName || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="160">
          <template #default="{ row }">
            <template v-if="row.roles && row.roles.length">
              <el-tag v-for="r in row.roles" :key="r.id" size="small" class="role-tag">{{ r.name }}</el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="90">
          <template #default="{ row }">{{ row.source === 'LOCAL' ? '本地' : 'LDAP' }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.updateTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" v-if="row.source === 'LOCAL'" @click="openReset(row)">重置密码</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pg-bar">
        <span class="pg-total">共 {{ total }} 条记录</span>
        <el-pagination
          background
          layout="prev, pager, next, sizes"
          :total="total"
          :current-page="page"
          :page-size="size"
          :page-sizes="[10, 20, 50]"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <!-- 编辑弹窗：LOCAL 可改除账号/来源外全部；LDAP 仅角色与可见部门权限 -->
    <el-dialog v-model="editDialog" title="编辑用户" width="600px">
      <el-form :model="editForm" label-width="110px">
        <el-form-item label="账号">
          <el-input :model-value="editRow && editRow.account" disabled />
        </el-form-item>
        <template v-if="editRow && editRow.source === 'LOCAL'">
          <el-form-item label="姓名"><el-input v-model="editForm.name" /></el-form-item>
          <el-form-item label="部门">
            <el-tree-select
              v-model="editForm.deptId"
              :data="deptTree"
              :props="{ label: 'label', children: 'children' }"
              node-key="id"
              check-strictly
              default-expand-all
              :render-after-expand="false"
              placeholder="选择部门"
              clearable
              style="width:100%"
            />
          </el-form-item>
        </template>
        <el-form-item label="角色">
          <el-select v-model="editForm.roleIds" multiple placeholder="选择角色" style="width:100%">
            <el-option v-for="r in allRoles" :key="r.id" :label="r.name + '（' + r.code + '）'" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="可见部门权限">
          <el-tree-select
            v-model="editForm.visibleDeptIds"
            :data="visibleDeptTree"
            :props="{ label: 'label', children: 'children' }"
            node-key="id"
            check-strictly
            multiple
            show-checkbox
            :render-after-expand="false"
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择可见部门（不绑定则看不到任何部门）"
            clearable
            style="width:100%"
            @change="onVisibleChange"
          />
          <div class="scope-hint">可见部门仅决定该用户在组织树中可看到的部门，<b>不等于</b>其文档阅读权限；勾选「全部」即代表可查看所有部门。未绑定任何可见部门（且无角色绑定）的用户将看不到任何部门（纯显式授权）。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新建本地用户 -->
    <el-dialog v-model="addDialog" title="新建用户（仅本地用户）" width="600px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:14px">
        <span>此功能仅用于新建<b>本地用户</b>；LDAP 用户由同步服务自动创建，不可在此手动添加。</span>
      </el-alert>
      <el-form :model="addForm" label-width="110px">
        <el-form-item label="账号"><el-input v-model="addForm.account" placeholder="登录账号" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="addForm.name" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="addForm.password" type="password" show-password placeholder="留空则默认为 账号@123456（首登需改密）；填写则不需要改密" /></el-form-item>
        <el-form-item label="部门">
          <el-tree-select
            v-model="addForm.deptId"
            :data="deptTree"
            :props="{ label: 'label', children: 'children' }"
            node-key="id"
            check-strictly
            default-expand-all
            :render-after-expand="false"
            placeholder="选择部门"
            clearable
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="可见部门权限">
          <el-tree-select
            v-model="addForm.visibleDeptIds"
            :data="visibleDeptTree"
            :props="{ label: 'label', children: 'children' }"
            node-key="id"
            check-strictly
            multiple
            show-checkbox
            :render-after-expand="false"
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择可见部门（不绑定则看不到任何部门）"
            clearable
            style="width:100%"
            @change="onVisibleChange"
          />
          <div class="scope-hint">可见部门仅决定该用户在组织树中可看到的部门，<b>不等于</b>其文档阅读权限；勾选「全部」即代表可查看所有部门。未绑定任何可见部门（且无角色绑定）的用户将看不到任何部门（纯显式授权）。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAdd">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码（仅 LOCAL） -->
    <el-dialog v-model="resetDialog" title="重置密码" width="400px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:14px">
        <span>留空直接确定，则恢复为初始密码 <b>账号@123456</b>，且首登需改密；填写新密码则不需要改密。</span>
      </el-alert>
      <el-input v-model="resetPwd" type="password" show-password placeholder="留空则恢复为初始密码 账号@123456（首登需改密）" />
      <template #footer>
        <el-button @click="resetDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveReset">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Search, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageUsers, createUser, updateUser, deleteUser, resetPassword, listUserSources, listUserVisibleDepts } from '@/api/user'
import { listDepts } from '@/api/dept'
import { listRoles } from '@/api/role'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const allRoles = ref([])
const depts = ref([])
// 来源下拉项：从后端枚举接口动态获取，失败时回退内置项
const sourceOptions = ref([
  { code: 'LOCAL', label: '本地用户' },
  { code: 'LDAP', label: 'LDAP 用户' }
])

// 查询条件（仅点击「查询」时才提交给后端）
const query = ref({ keyword: '', source: '', roleId: null })

async function fetchData() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: size.value
    }
    const q = query.value
    if (q.keyword && q.keyword.trim()) params.keyword = q.keyword.trim()
    if (q.source) params.source = q.source
    if (q.roleId != null && q.roleId !== '') params.roleId = q.roleId
    const res = await pageUsers(params)
    const d = res.data || {}
    rows.value = d.list || []
    total.value = d.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  fetchData()
}

function onReset() {
  query.value = { keyword: '', source: '', roleId: null }
  page.value = 1
  fetchData()
}

function onPageChange(p) {
  page.value = p
  fetchData()
}

function onSizeChange(s) {
  size.value = s
  page.value = 1
  fetchData()
}

// 扁平部门列表（仅 LOCAL）组装成树（用于“所属部门”单选）
const deptTree = computed(() => {
  const local = depts.value.filter(d => d.source === 'LOCAL' || !d.source)
  const nodes = new Map()
  local.forEach(d => nodes.set(d.id, { id: d.id, label: d.name, children: [] }))
  const roots = []
  local.forEach(d => {
    const node = nodes.get(d.id)
    const parent = d.parentId != null ? nodes.get(d.parentId) : null
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  return roots
})

// 全部门树（LOCAL + LDAP），用于“可见部门权限”多选；顶部插入逻辑节点“全部”(id=0)
const visibleDeptTree = computed(() => {
  const list = depts.value
  const nodes = new Map()
  list.forEach(d => nodes.set(d.id, { id: d.id, label: d.name, children: [] }))
  const roots = []
  list.forEach(d => {
    const node = nodes.get(d.id)
    const parent = d.parentId != null ? nodes.get(d.parentId) : null
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  // 逻辑“全部”节点：选中即代表可看所有部门（deptId=0）
  roots.unshift({ id: 0, label: '全部', children: [] })
  return roots
})

// ---- 编辑 ----
const editDialog = ref(false)
const editRow = ref(null)
const editForm = ref({ name: '', deptId: null, roleIds: [], visibleDeptIds: [] })

async function openEdit(row) {
  editRow.value = row
  editForm.value = {
    name: row.name || '',
    deptId: row.deptId,
    roleIds: (row.roles || []).map(r => r.id),
    visibleDeptIds: []
  }
  // 回显已绑定可见部门
  try {
    const res = await listUserVisibleDepts(row.id)
    editForm.value.visibleDeptIds = (res.data || []).map(d => d.id)
  } catch (e) {}
  editDialog.value = true
}

async function saveEdit() {
  const row = editRow.value
  if (!row) return
  saving.value = true
  try {
    // LDAP 用户后端只应用 roleIds + 可见部门权限；LOCAL 提交除账号/来源外的全部字段
    const payload = row.source === 'LDAP'
      ? { roleIds: editForm.value.roleIds, visibleDeptIds: editForm.value.visibleDeptIds }
      : { name: editForm.value.name, deptId: editForm.value.deptId, roleIds: editForm.value.roleIds, visibleDeptIds: editForm.value.visibleDeptIds }
    await updateUser(row.id, payload)
    ElMessage.success('保存成功')
    editDialog.value = false
    await fetchData()
  } finally {
    saving.value = false
  }
}

// 可见部门「全部」互斥：勾选逻辑节点(0) 即仅保留 [0]
function onVisibleChange(val) {
  if (Array.isArray(val) && val.includes(0)) {
    if (editDialog.value) editForm.value.visibleDeptIds = [0]
    else addForm.value.visibleDeptIds = [0]
  }
}

// ---- 新建 ----
const addDialog = ref(false)
const addForm = ref({ account: '', name: '', password: '', deptId: null, visibleDeptIds: [] })

function openAdd() {
  addForm.value = { account: '', name: '', password: '', deptId: null, visibleDeptIds: [] }
  addDialog.value = true
}

async function saveAdd() {
  if (!addForm.value.account) {
    return ElMessage.warning('请填写账号')
  }
  saving.value = true
  try {
    // 密码留空时后端默认使用「账号@123456」
    await createUser({ ...addForm.value })
    ElMessage.success('创建成功')
    addDialog.value = false
    await fetchData()
  } finally {
    saving.value = false
  }
}

// ---- 重置密码 ----
const resetDialog = ref(false)
const resetPwd = ref('')
const resetId = ref(null)

function openReset(row) {
  resetId.value = row.id
  resetPwd.value = ''
  resetDialog.value = true
}

async function saveReset() {
  saving.value = true
  try {
    // 密码留空则后端恢复初始密码（账号@123456）并置 mustChangePwd=1
    await resetPassword(resetId.value, resetPwd.value || '')
    ElMessage.success('密码已重置')
    resetDialog.value = false
  } finally {
    saving.value = false
  }
}

// ---- 删除 ----
async function remove(row) {
  await ElMessageBox.confirm(`确认删除用户「${row.account}」？`, '提示', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('已删除')
  // 当前页删空后回退一页
  if (rows.value.length === 1 && page.value > 1) page.value -= 1
  await fetchData()
}

onMounted(async () => {
  fetchData()
  try {
    const [r, d, s] = await Promise.all([listRoles(), listDepts(), listUserSources()])
    allRoles.value = r.data || []
    depts.value = d.data || []
    if (Array.isArray(s.data) && s.data.length) sourceOptions.value = s.data
  } catch (e) {}
})
</script>

<style scoped>
.user-page { padding: 4px; }
.page-title { margin: 0 0 14px; font-size: 20px; color: #222; }
.panel { border-radius: 8px; }
.filter-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 16px; }
.f-label { color: #555; font-size: 14px; }
.f-input { width: 200px; }
.f-select { width: 150px; }
.f-actions { margin-left: auto; display: flex; gap: 0; }
.tbl { width: 100%; }
.role-tag { margin-right: 6px; }
.pg-bar { display: flex; align-items: center; justify-content: space-between; margin-top: 14px; }
.pg-total { color: #888; font-size: 13px; }
/* 弹窗表单 label 保持单行不换行 */
:deep(.el-dialog .el-form-item__label) { white-space: nowrap; }
/* 可见部门权限提示 */
.scope-hint { color: #909399; font-size: 12px; line-height: 1.6; margin-top: 6px; }
</style>
