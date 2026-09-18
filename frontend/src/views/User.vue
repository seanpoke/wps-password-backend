<template>
  <el-card shadow="never">
    <template #header>
      <div class="hd">
        <span>用户管理</span>
        <div>
          <el-input v-model="kw" placeholder="搜索账号/姓名" style="width:180px" :prefix-icon="Search" clearable @clear="load" @keyup.enter="load" />
          <el-button v-if="activeTab === 'LOCAL'" type="primary" :icon="Plus" @click="openAdd">新建用户</el-button>
        </div>
      </div>
    </template>

    <el-tabs v-model="activeTab" @tab-change="page = 1">
      <el-tab-pane :label="'本地用户（' + localCount + '）'" name="LOCAL" />
      <el-tab-pane :label="'LDAP 用户（' + ldapCount + '）'" name="LDAP" />
    </el-tabs>

    <el-table :data="paged" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="account" label="账号" />
      <el-table-column prop="name" label="姓名" />
      <el-table-column prop="deptId" label="部门ID" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="source" label="来源" width="90" />
      <el-table-column label="首登改密" width="90">
        <template #default="{ row }"><el-tag v-if="row.mustChangePwd === 1" type="warning">需改密</el-tag><span v-else>-</span></template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <template v-if="row.source === 'LOCAL'">
            <el-button size="small" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" :icon="Key" @click="openReset(row)">重置密码</el-button>
            <el-button size="small" type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
          </template>
          <span v-else class="readonly-tip">随 LDAP 同步</span>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pg" background layout="prev,pager,next,total" :total="filtered.length" v-model:current-page="page" :page-size="size" />

    <el-dialog v-model="dialog" :title="editing ? '编辑用户' : '新建用户'" width="440px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="账号" v-if="!editing"><el-input v-model="form.account" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="密码" v-if="!editing"><el-input v-model="form.password" type="password" show-password placeholder="初始密码，首登需改密" /></el-form-item>
        <el-form-item label="部门">
          <el-tree-select
            v-model="form.deptId"
            :data="deptTree"
            :props="{ label: 'label', children: 'children' }"
            node-key="id"
            check-strictly
            default-expand-all
            :render-after-expand="false"
            placeholder="选择部门（点击节点选中）"
            clearable
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="状态" v-if="editing">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetDialog" title="重置密码" width="400px">
      <el-input v-model="resetPwd" type="password" show-password placeholder="新密码" />
      <template #footer>
        <el-button @click="resetDialog=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveReset">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { Search, Plus, Edit, Delete, Key } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, createUser, updateUser, deleteUser, resetPassword } from '@/api/user'
import { listDepts } from '@/api/dept'

const loading = ref(false)
const list = ref([])
const depts = ref([])
const kw = ref('')
const page = ref(1)
const size = 10
const activeTab = ref('LOCAL')

const localCount = computed(() => list.value.filter(x => x.source === 'LOCAL').length)
const ldapCount = computed(() => list.value.filter(x => x.source === 'LDAP').length)

/** 把扁平部门列表组装成树（仅 LOCAL 来源），parentId 悬空的挂为根节点，label 带 ID 便于区分重名 */
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

const filtered = computed(() => {
  const k = kw.value.trim()
  return list.value
    .filter(x => x.source === activeTab.value)
    .filter(x => !k || (x.account || '').includes(k) || (x.name || '').includes(k))
})
const paged = computed(() => filtered.value.slice((page.value - 1) * size, page.value * size))

const dialog = ref(false)
const editing = ref(null)
const saving = ref(false)
const form = ref({ account: '', name: '', password: '', deptId: null, status: 1 })

const resetDialog = ref(false)
const resetPwd = ref('')
const resetId = ref(null)

async function load() {
  loading.value = true
  try {
    const [u, d] = await Promise.all([listUsers(), listDepts()])
    list.value = u.data || []
    depts.value = d.data || []
  } finally { loading.value = false }
}

function openAdd() {
  editing.value = null
  form.value = { account: '', name: '', password: '', deptId: null, status: 1 }
  dialog.value = true
}
function openEdit(row) {
  editing.value = row
  form.value = { name: row.name, deptId: row.deptId, status: row.status === 0 ? 0 : 1 }
  dialog.value = true
}
async function save() {
  saving.value = true
  try {
    if (editing.value) await updateUser(editing.value.id, { name: form.value.name, deptId: form.value.deptId, status: form.value.status })
    else await createUser({ account: form.value.account, name: form.value.name, password: form.value.password, deptId: form.value.deptId })
    ElMessage.success('保存成功')
    dialog.value = false
    await load()
  } finally { saving.value = false }
}
function openReset(row) {
  resetId.value = row.id
  resetPwd.value = ''
  resetDialog.value = true
}
async function saveReset() {
  if (!resetPwd.value) return ElMessage.warning('请输入新密码')
  saving.value = true
  try {
    await resetPassword(resetId.value, resetPwd.value)
    ElMessage.success('密码已重置')
    resetDialog.value = false
  } finally { saving.value = false }
}
async function remove(row) {
  await ElMessageBox.confirm('确认删除该用户？', '提示', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('已删除')
  await load()
}

watch(kw, () => { page.value = 1 })

onMounted(load)
</script>

<style scoped>
.hd { display: flex; justify-content: space-between; align-items: center; }
.pg { margin-top: 12px; justify-content: flex-end; }
.readonly-tip { color: #999; font-size: 12px; }
</style>
