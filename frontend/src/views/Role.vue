<template>
  <div class="role-page">
    <h2 class="page-title">角色管理</h2>

    <el-card shadow="never" class="panel">
      <div class="toolbar">
        <el-button type="success" :icon="Plus" @click="openAdd">新建角色</el-button>
      </div>

      <el-table :data="list" v-loading="loading" class="tbl">
        <el-table-column prop="code" label="编码" min-width="120" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column label="备注" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="可见部门权限" min-width="200">
          <template #default="{ row }">
            <template v-if="row.visibleDeptNames && row.visibleDeptNames.length">
              <el-tag v-for="n in row.visibleDeptNames" :key="n" size="small" class="vd-tag" :type="n === '全部' ? 'warning' : 'info'">{{ n }}</el-tag>
            </template>
            <span v-else class="vd-empty">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.code === 'admin'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" :disabled="row.code === 'admin'" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑弹窗：对齐用户管理风格，含可见部门权限 -->
    <el-dialog v-model="dialog" :title="editing ? '编辑角色' : '新建角色'" width="600px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="编码">
          <el-input v-model="form.code" :disabled="!!editing" placeholder="如 admin / user / manager" />
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" :max="999" />
          <span class="tip">数值越小优先级越高</span>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="可见部门权限">
          <el-tree-select
            v-model="form.visibleDeptIds"
            :data="deptTree"
            :props="{ label: 'label', children: 'children' }"
            node-key="id"
            check-strictly
            multiple
            show-checkbox
            :render-after-expand="false"
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择可见部门（不选则无额外范围）"
            clearable
            style="width:100%"
            @change="onVisibleChange"
          />
          <div class="scope-hint">可见部门仅决定该角色下用户在组织树中可看到的部门，<b>不等于</b>其文档阅读权限；勾选「全部」即代表可查看所有部门。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listRoles, createRole, updateRole, deleteRole } from '@/api/role'
import { listDepts } from '@/api/dept'
import { listVisibleDepts } from '@/api/visibleDept'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const dialog = ref(false)
const editing = ref(null)
const form = ref({ code: '', name: '', priority: 100, remark: '', visibleDeptIds: [] })
const depts = ref([])

// 全部门树（LOCAL + LDAP），顶部插入逻辑节点「全部」(id=0)
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
  roots.unshift({ id: 0, label: '全部', children: [] })
  return roots
})

// 选中「全部」即互斥：一旦勾选 0，仅保留 [0]
function onVisibleChange(val) {
  if (Array.isArray(val) && val.includes(0)) {
    form.value.visibleDeptIds = [0]
  }
}

async function load() {
  loading.value = true
  try {
    const res = await listRoles()
    list.value = res.data || []
  } finally { loading.value = false }
}

async function openAdd() {
  editing.value = null
  form.value = { code: '', name: '', priority: 100, remark: '', visibleDeptIds: [] }
  dialog.value = true
}

async function openEdit(row) {
  editing.value = row
  form.value = { code: row.code, name: row.name, priority: row.priority, remark: row.remark || '', visibleDeptIds: [] }
  // 回显已绑定可见部门
  try {
    const res = await listVisibleDepts('ROLE', row.id)
    form.value.visibleDeptIds = (res.data || []).map(d => d.id)
  } catch (e) {}
  dialog.value = true
}

async function save() {
  if (!form.value.code) return ElMessage.warning('请输入角色编码')
  if (!form.value.name) return ElMessage.warning('请输入名称')
  saving.value = true
  try {
    const payload = {
      name: form.value.name,
      priority: form.value.priority,
      remark: form.value.remark,
      visibleDeptIds: form.value.visibleDeptIds
    }
    if (editing.value) {
      await updateRole(editing.value.id, payload)
    } else {
      await createRole({ code: form.value.code, ...payload })
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

onMounted(async () => {
  load()
  try {
    const d = await listDepts()
    depts.value = d.data || []
  } catch (e) {}
})
</script>

<style scoped>
.role-page { padding: 4px; }
.page-title { margin: 0 0 14px; font-size: 20px; color: #222; }
.panel { border-radius: 8px; }
.toolbar { display: flex; justify-content: flex-end; margin-bottom: 14px; }
.tbl { width: 100%; }
.tip { color: #999; font-size: 12px; margin-left: 8px; }
.vd-tag { margin-right: 6px; }
.vd-empty { color: #c0c4cc; }
/* 弹窗表单 label 保持单行不换行 */
:deep(.el-dialog .el-form-item__label) { white-space: nowrap; }
/* 可见部门权限提示 */
.scope-hint { color: #909399; font-size: 12px; line-height: 1.6; margin-top: 6px; }
</style>
