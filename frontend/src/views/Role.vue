<template>
  <el-card shadow="never">
    <template #header>
      <div class="hd">
        <span>角色管理</span>
        <el-button type="primary" :icon="Plus" @click="openAdd">新建角色</el-button>
      </div>
    </template>

    <el-table :data="list" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="code" label="角色编码" width="140" />
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column prop="priority" label="优先级" width="100" />
      <el-table-column prop="remark" label="备注" show-overflow-tooltip />
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button size="small" :icon="Link" @click="openGroups(row)">关联可见部门</el-button>
          <el-button size="small" :icon="Edit" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editing ? '修改角色' : '新建角色'" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="角色编码">
          <el-input v-model="form.code" :disabled="!!editing" placeholder="如 admin / user / manager" />
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" :max="999" />
          <span class="tip">数值越小优先级越高</span>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawer" :title="'角色「' + (currentRole ? currentRole.name : '') + '」可见的部门'" size="460px">
      <div v-loading="deptLoading">
        <div class="hd">
          <span>已可见部门（{{ roleDepts.length }}）</span>
          <el-button type="primary" :icon="Plus" size="small" @click="bindVisible = true">添加可见部门</el-button>
        </div>
        <el-table :data="roleDepts" border size="small">
          <el-table-column prop="name" label="部门名称" show-overflow-tooltip />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="unbindDept(row.id)">解绑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-dialog v-model="bindVisible" title="添加可见部门" width="360px" append-to-body>
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
        <p class="scope-hint">部门可见范围仅决定该角色在组织树中可看到的部门，<b>不等于</b>其文档阅读权限。</p>
        <template #footer>
          <el-button @click="bindVisible = false">取消</el-button>
          <el-button type="primary" :loading="binding" @click="doBindDept">确定</el-button>
        </template>
      </el-dialog>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Plus, Edit, Delete, Link } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listRoles, createRole, updateRole, deleteRole } from '@/api/role'
import { listDepts } from '@/api/dept'
import { listVisibleDepts, addVisibleDept, deleteVisibleDept } from '@/api/visibleDept'

const loading = ref(false)
const list = ref([])
const dialog = ref(false)
const editing = ref(null)
const saving = ref(false)
const form = ref({ code: '', name: '', priority: 100, remark: '' })

const drawer = ref(false)
const currentRole = ref(null)
const deptLoading = ref(false)
const roleDepts = ref([])
const depts = ref([])
const bindVisible = ref(false)
const bindDeptId = ref(null)
const binding = ref(false)

const deptTree = computed(() => {
  const nodes = new Map()
  depts.value.forEach(d => nodes.set(d.id, { id: d.id, label: d.name, children: [] }))
  const roots = []
  depts.value.forEach(d => {
    const node = nodes.get(d.id)
    const parent = d.parentId != null ? nodes.get(d.parentId) : null
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  return roots
})

async function load() {
  loading.value = true
  try {
    const res = await listRoles()
    list.value = res.data || []
  } finally { loading.value = false }
}

function openAdd() {
  editing.value = null
  form.value = { code: '', name: '', priority: 100, remark: '' }
  dialog.value = true
}
function openEdit(row) {
  editing.value = row
  form.value = { code: row.code, name: row.name, priority: row.priority, remark: row.remark }
  dialog.value = true
}
async function save() {
  if (!form.value.code) return ElMessage.warning('请输入角色编码')
  if (!form.value.name) return ElMessage.warning('请输入名称')
  saving.value = true
  try {
    if (editing.value) {
      await updateRole(editing.value.id, { name: form.value.name, priority: form.value.priority, remark: form.value.remark })
    } else {
      await createRole({ code: form.value.code, name: form.value.name, priority: form.value.priority, remark: form.value.remark })
    }
    ElMessage.success('保存成功')
    dialog.value = false
    await load()
  } finally { saving.value = false }
}
async function remove(row) {
  await ElMessageBox.confirm(`确认删除角色「${row.name}」？将级联清理用户/部门关联`, '提示', { type: 'warning' })
  try {
    await deleteRole(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {}
}

async function openGroups(row) {
  currentRole.value = row
  drawer.value = true
  await loadDepts()
}
async function loadDepts() {
  deptLoading.value = true
  try {
    const [rg, d] = await Promise.all([
      listVisibleDepts('ROLE', currentRole.value.id),
      listDepts()
    ])
    roleDepts.value = rg.data || []
    depts.value = d.data || []
  } finally { deptLoading.value = false }
}
async function doBindDept() {
  if (bindDeptId.value == null) return ElMessage.warning('请选择部门')
  binding.value = true
  try {
    await addVisibleDept({ relType: 'ROLE', relId: currentRole.value.id, deptId: bindDeptId.value })
    ElMessage.success('已添加')
    bindVisible.value = false
    bindDeptId.value = null
    await loadDepts()
  } finally { binding.value = false }
}
async function unbindDept(deptId) {
  await deleteVisibleDept('ROLE', currentRole.value.id, deptId)
  ElMessage.success('已解绑')
  await loadDepts()
}

onMounted(load)
</script>

<style scoped>
.hd { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.tip { color: #999; font-size: 12px; margin-left: 8px; }
.scope-hint { color: #909399; font-size: 12px; line-height: 1.6; margin: 10px 0 0; }
</style>
