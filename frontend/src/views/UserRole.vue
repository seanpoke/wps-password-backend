<template>
  <el-card shadow="never">
    <template #header>
      <div class="hd">
        <span>用户授权（角色 / 可见部门）</span>
      </div>
    </template>

    <div class="bar">
      <el-input v-model="account" placeholder="输入账号（本地或 LDAP）" style="width:240px" :prefix-icon="User" clearable @keyup.enter="query" />
      <el-button type="primary" :icon="Search" :loading="loading" @click="query">查询授权</el-button>
    </div>

    <el-alert v-if="found" :title="'已找到用户：' + found.name + '（' + found.account + '，来源 ' + (found.source || '-') + '）'" type="success" :closable="false" class="mb" />

    <template v-if="found">
      <el-divider content-position="left">角色（取并集）</el-divider>
      <div class="bar">
        <span class="sub">当前角色：{{ userRoles.length }} 个</span>
        <el-button type="primary" size="small" :icon="Plus" @click="roleBindVisible = true">添加角色</el-button>
      </div>
      <el-table :data="userRoles" border size="small" class="mb">
        <el-table-column prop="code" label="角色编码" width="160" />
        <el-table-column prop="name" label="名称" width="200" />
        <el-table-column prop="priority" label="优先级" width="100" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="unbindRole(row.id)">解绑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">可见部门（直接绑定，无权限组）</el-divider>
      <div class="bar">
        <span class="sub">当前可见部门：{{ userDepts.length }} 个</span>
        <el-button type="primary" size="small" :icon="Plus" @click="deptBindVisible = true">添加可见部门</el-button>
      </div>
      <el-table :data="userDepts" border size="small">
        <el-table-column prop="name" label="部门名称" show-overflow-tooltip />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="unbindDept(row.id)">解绑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <el-empty v-else :description="found === false ? ('未查询到用户 ' + account) : '请输入账号后点击「查询授权」'" />

    <el-dialog v-model="roleBindVisible" title="添加角色" width="360px" append-to-body>
      <el-select v-model="bindRoleId" placeholder="选择角色" style="width:100%">
        <el-option v-for="r in allRoles" :key="r.id" :label="r.name + '（' + r.code + '）'" :value="r.id" />
      </el-select>
      <template #footer>
        <el-button @click="roleBindVisible = false">取消</el-button>
        <el-button type="primary" :loading="binding" @click="doBindRole">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="deptBindVisible" title="添加可见部门" width="360px" append-to-body>
      <el-tree-select
        v-model="bindDeptId"
        :data="deptTree"
        :props="{ label: 'label', children: 'children' }"
        node-key="id"
        check-strictly
        default-expand-all
        :render-after-expand="false"
        placeholder="选择部门（LDAP/本地统一）"
        clearable
        style="width:100%"
      />
      <p class="scope-hint">可见部门仅决定该用户在组织树中可看到的部门，<b>不等于</b>其文档阅读权限。</p>
      <template #footer>
        <el-button @click="deptBindVisible = false">取消</el-button>
        <el-button type="primary" :loading="binding" @click="doBindDept">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Plus, Search, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listRoles, findUser, listUserRoles, bindUserRole, unbindUserRole } from '@/api/role'
import { listDepts } from '@/api/dept'
import { listVisibleDepts, addVisibleDept, deleteVisibleDept } from '@/api/visibleDept'

const account = ref('')
const loading = ref(false)
const found = ref(null)
const userRoles = ref([])
const userDepts = ref([])

const allRoles = ref([])
const allDepts = ref([])
const deptTree = computed(() => {
  const nodes = new Map()
  allDepts.value.forEach(d => nodes.set(d.id, { id: d.id, label: d.name, children: [] }))
  const roots = []
  allDepts.value.forEach(d => {
    const node = nodes.get(d.id)
    const parent = d.parentId != null ? nodes.get(d.parentId) : null
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  return roots
})

const roleBindVisible = ref(false)
const deptBindVisible = ref(false)
const bindRoleId = ref(null)
const bindDeptId = ref(null)
const binding = ref(false)

async function query() {
  const acc = account.value.trim()
  if (!acc) return ElMessage.warning('请输入账号')
  loading.value = true
  try {
    found.value = null
    userRoles.value = []
    userDepts.value = []
    const u = await findUser(acc)
    if (!u.data) {
      found.value = false
      ElMessage.warning('未查询到用户 ' + acc)
      return
    }
    found.value = u.data
    const [ur, ud, d] = await Promise.all([
      listUserRoles(acc),
      listVisibleDepts('USER', found.value.id),
      listDepts()
    ])
    userRoles.value = ur.data || []
    userDepts.value = ud.data || []
    allDepts.value = d.data || []
  } finally { loading.value = false }
}

async function doBindRole() {
  if (!bindRoleId.value) return ElMessage.warning('请选择角色')
  binding.value = true
  try {
    await bindUserRole({ account: account.value.trim(), roleId: bindRoleId.value })
    ElMessage.success('已绑定')
    roleBindVisible.value = false
    bindRoleId.value = null
    await query()
  } finally { binding.value = false }
}
async function unbindRole(id) {
  await unbindUserRole(id)
  ElMessage.success('已解绑')
  await query()
}
async function doBindDept() {
  if (bindDeptId.value == null) return ElMessage.warning('请选择部门')
  if (!found.value || !found.value.id) return ElMessage.warning('用户无本地 ID，无法绑定')
  binding.value = true
  try {
    await addVisibleDept({ relType: 'USER', relId: found.value.id, deptId: bindDeptId.value })
    ElMessage.success('已添加')
    deptBindVisible.value = false
    bindDeptId.value = null
    await query()
  } finally { binding.value = false }
}
async function unbindDept(deptId) {
  if (!found.value || !found.value.id) return
  await deleteVisibleDept('USER', found.value.id, deptId)
  ElMessage.success('已解绑')
  await query()
}

onMounted(async () => {
  const [r, d] = await Promise.all([listRoles(), listDepts()])
  allRoles.value = r.data || []
  allDepts.value = d.data || []
})
</script>

<style scoped>
.hd { display: flex; justify-content: space-between; align-items: center; }
.bar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.sub { color: #666; font-size: 13px; }
.mb { margin-bottom: 12px; }
.scope-hint { color: #909399; font-size: 12px; line-height: 1.6; margin: 10px 0 0; }
</style>
