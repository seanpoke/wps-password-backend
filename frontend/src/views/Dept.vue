<template>
  <el-card shadow="never">
    <template #header>
      <div class="hd">
        <span>部门管理</span>
        <div>
          <el-input v-model="kw" placeholder="搜索部门名" style="width:180px" :prefix-icon="Search" clearable @clear="load" @keyup.enter="load" />
          <el-button v-if="activeTab === 'LOCAL'" type="primary" :icon="Plus" @click="openAdd">新建部门</el-button>
        </div>
      </div>
    </template>

    <el-tabs v-model="activeTab">
      <el-tab-pane :label="'本地部门（' + localCount + '）'" name="LOCAL" />
      <el-tab-pane :label="'LDAP 部门（' + ldapCount + '）'" name="LDAP" />
    </el-tabs>

    <el-tree
      v-loading="loading"
      :data="treeData"
      node-key="id"
      :props="{ label: 'name', children: 'children' }"
      :expand-on-click-node="false"
    >
      <template #default="{ data }">
        <span class="node">
          <el-icon class="folder"><Folder /></el-icon>
          <span class="name">{{ data.name }}</span>
          <el-popover v-if="refMap[data.id] && refMap[data.id].labels.length > 0" placement="right" :width="240" trigger="hover">
            <template #reference>
              <span class="ref-badge" :title="'被 ' + refMap[data.id].labels.length + ' 个用户/角色设为可见'" @click.stop><el-icon><View /></el-icon>{{ refMap[data.id].labels.length }}</span>
            </template>
            <div class="ref-pop">
              <div class="ref-pop-title">被 {{ refMap[data.id].labels.length }} 个用户/角色设为可见</div>
              <template v-if="refMap[data.id].users.length">
                <div class="ref-pop-group">用户（{{ refMap[data.id].users.length }}）</div>
                <div v-for="g in refMap[data.id].users" :key="'u-' + g" class="ref-pop-item">• {{ g }}</div>
              </template>
              <template v-if="refMap[data.id].roles.length">
                <div class="ref-pop-group">角色（{{ refMap[data.id].roles.length }}）</div>
                <div v-for="g in refMap[data.id].roles" :key="'r-' + g" class="ref-pop-item">• {{ g }}</div>
              </template>
            </div>
          </el-popover>
          <span v-if="data.source === 'LOCAL'" class="ops">
            <el-button link type="primary" :icon="Edit" @click="openEdit(data)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="remove(data)">删除</el-button>
          </span>
        </span>
      </template>
    </el-tree>
    <el-empty v-if="!loading && !treeData.length" description="暂无部门" />

    <el-dialog v-model="dialog" :title="editing ? '修改部门' : '新建部门'" width="420px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="父部门">
          <el-tree-select
            v-model="form.parentId"
            :data="parentTree"
            :props="{ label: 'label', children: 'children' }"
            node-key="id"
            check-strictly
            default-expand-all
            :render-after-expand="false"
            placeholder="顶级（无父级），点击节点选中"
            clearable
            style="width:100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Search, Plus, Edit, Delete, Folder, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDepts, createDept, updateDept, deleteDept, listDeptRefs } from '@/api/dept'
import { ldapTree } from '@/api/ldap'

const loading = ref(false)
const list = ref([])
const kw = ref('')
const activeTab = ref('LOCAL')
const deptRefs = ref([])
const refMap = computed(() => {
  const m = {}
  deptRefs.value.forEach(r => {
    m[r.deptId] = {
      labels: r.relLabels || [],
      users: r.userLabels || [],
      roles: r.roleLabels || [],
      docAuth: r.docAuthCount || 0
    }
  })
  return m
})

const localCount = computed(() => list.value.filter(x => x.source === 'LOCAL').length)

// LDAP 页签直接来自内存缓存（deptNodeCache），不再读 DB 的 source=LDAP 行
const ldapNodes = ref([])
const ldapTreeData = computed(() => {
  // 用 DB 中已同步的 LDAP 部门 path(dn) -> id 映射，保留可见性角标
  const dnToId = {}
  list.value.filter(x => x.source === 'LDAP' && x.path).forEach(d => { dnToId[d.path.toLowerCase()] = d.id })
  const build = (dto) => ({
    id: dnToId[(dto.dn || '').toLowerCase()] ?? dto.dn,
    name: dto.name,
    source: 'LDAP',
    children: (dto.deptList || []).map(build)
  })
  return (ldapNodes.value || []).map(build)
})
function countDepts(nodes) {
  let c = 0
  for (const n of nodes) { c++; if (n.children) c += countDepts(n.children) }
  return c
}
const ldapCount = computed(() => countDepts(ldapTreeData.value))

/** 父部门候选树：仅 LOCAL；编辑时排除自身及其子孙（防止把自己挂到自己下面） */
const parentTree = computed(() => {
  const local = list.value.filter(d => d.source === 'LOCAL' || !d.source)
  const edit = editing.value
  const forbidden = new Set()
  if (edit) {
    // 自身 + 全部子孙（沿 parentId 向下遍历），防止把自己挂到自己下面
    forbidden.add(edit.id)
    const byParent = new Map()
    local.forEach(d => {
      const k = d.parentId != null ? d.parentId : '#'
      if (!byParent.has(k)) byParent.set(k, [])
      byParent.get(k).push(d)
    })
    const stack = [edit.id]
    while (stack.length) {
      const pid = stack.pop()
      for (const child of byParent.get(pid) || []) {
        if (!forbidden.has(child.id)) {
          forbidden.add(child.id)
          stack.push(child.id)
        }
      }
    }
  }
  const nodes = new Map()
  local.forEach(d => {
    if (!forbidden.has(d.id)) nodes.set(d.id, { id: d.id, label: d.name, children: [] })
  })
  const roots = []
  local.forEach(d => {
    if (forbidden.has(d.id)) return
    const node = nodes.get(d.id)
    const parent = d.parentId != null ? nodes.get(d.parentId) : null
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  return roots
})

/** 平铺 sys_dept -> 树（parentId 指向的父不在集内时挂为根） */
function buildDeptTree(items) {
  const nodes = new Map(items.map(d => [d.id, { ...d, children: [] }]))
  const roots = []
  items.forEach(d => {
    const node = nodes.get(d.id)
    const parent = d.parentId != null ? nodes.get(d.parentId) : null
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  return roots
}

/** 关键字过滤：保留命中节点及其祖先链 */
function filterDeptTree(nodes, k) {
  if (!k) return nodes
  const out = []
  nodes.forEach(n => {
    const kids = n.children ? filterDeptTree(n.children, k) : []
    if ((n.name || '').includes(k) || kids.length) {
      out.push({ ...n, children: kids })
    }
  })
  return out
}

const treeData = computed(() => {
  const k = kw.value.trim()
  if (activeTab.value === 'LDAP') {
    return filterDeptTree(ldapTreeData.value, k)
  }
  const items = list.value.filter(x => x.source === 'LOCAL' || !x.source)
  return filterDeptTree(buildDeptTree(items), k)
})

const dialog = ref(false)
const editing = ref(null)
const saving = ref(false)
const form = ref({ name: '', parentId: null })

async function load() {
  loading.value = true
  try {
    const [deptRes, refRes, ldapRes] = await Promise.all([listDepts(), listDeptRefs(), ldapTree()])
    list.value = deptRes.data || []
    deptRefs.value = refRes.data || []
    ldapNodes.value = ldapRes.data || []
  } finally { loading.value = false }
}

function openAdd() {
  editing.value = null
  form.value = { name: '', parentId: null }
  dialog.value = true
}
function openEdit(row) {
  editing.value = row
  form.value = { name: row.name, parentId: row.parentId }
  dialog.value = true
}
async function save() {
  if (!form.value.name) return ElMessage.warning('请输入名称')
  saving.value = true
  try {
    if (editing.value) await updateDept(editing.value.id, { name: form.value.name, parentId: form.value.parentId })
    else await createDept({ name: form.value.name, parentId: form.value.parentId })
    ElMessage.success('保存成功')
    dialog.value = false
    await load()
  } finally { saving.value = false }
}
async function remove(row) {
  const ref = refMap.value[row.id]
  const parts = []
  if (ref && ref.labels.length) parts.push('被 ' + ref.labels.length + ' 个用户/角色设为可见')
  if (ref && ref.docAuth > 0) parts.push('含 ' + ref.docAuth + ' 条文档授权')
  const extra = parts.length ? '（当前' + parts.join('、') + '，删除后将一并清除）' : ''
  await ElMessageBox.confirm(
    '是否确认删除该部门？<br/><span style="color:#f56c6c;font-size:12px">删除当前节点后，其子部门' + extra + '都会被一并删除。</span>',
    '删除【' + row.name + '】部门',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消', dangerouslyUseHTMLString: true }
  )
  await deleteDept(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.hd { display: flex; justify-content: space-between; align-items: center; }
.ref-badge { display: inline-flex; align-items: center; gap: 2px; margin-left: 10px; padding: 0 6px; height: 18px; font-size: 12px; line-height: 1; color: #409eff; background: #ecf5ff; border-radius: 9px; cursor: pointer; flex-shrink: 0; }
.ref-badge:hover { background: #d9ecff; }
.ref-pop-title { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 6px; }
.ref-pop-group { font-size: 12px; font-weight: 600; color: #909399; margin: 6px 0 2px; }
.ref-pop-item { font-size: 12px; color: #606266; line-height: 1.8; }
.node { display: flex; align-items: center; gap: 6px; flex: 1; min-width: 0; padding-right: 8px; }
.folder { color: #e6a23c; flex-shrink: 0; }
.name { font-size: 14px; color: #303133; flex-shrink: 0; }
.ops { flex-shrink: 0; margin-left: 72px; visibility: hidden; }
:deep(.el-tree-node__content:hover) .ops { visibility: visible; }
.cmp { height: 100%; display: flex; flex-direction: column; }
.cmp-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; }
.cmp-bar .spacer { flex: 1; }
.tag-btn { cursor: pointer; user-select: none; }
.cmp-list { flex: 1; overflow: auto; border: 1px solid #ebeef5; border-radius: 4px; }
.cmp-row { display: flex; align-items: center; gap: 6px; padding: 4px 8px; flex: 1; min-width: 0; }
.cmp-row.gone { background: #fef0f0; }
.cmp-row .fd { flex-shrink: 0; color: #e6a23c; }
.cmp-row .nm { font-weight: 500; flex-shrink: 0; }
.cmp-row .acc { color: #909399; font-size: 12px; }
.cmp-row .st { margin-left: 4px; flex-shrink: 0; }
.cmp-row .dn { color: #c0c4cc; font-size: 12px; margin-left: auto; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
</style>
