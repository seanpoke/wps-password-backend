<template>
  <el-card shadow="never">
    <template #header>
      <div class="hd">
        <span>部门管理</span>
        <div>
          <el-input v-model="kw" placeholder="搜索部门名" style="width:180px" :prefix-icon="Search" clearable @clear="load" @keyup.enter="load" />
          <el-button v-if="activeTab === 'LDAP'" type="warning" :icon="Refresh" :loading="compareLoading" @click="openCompare">同步对比</el-button>
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

    <!-- 同步对比抽屉 -->
    <el-drawer v-model="compareOpen" title="LDAP 同步对比" size="48%" direction="rtl">
      <div v-loading="compareLoading" class="cmp">
        <div class="cmp-bar">
          <el-tag type="success" :effect="allTypeSelected('NEW') ? 'dark' : 'light'" class="tag-btn" @click="toggleType('NEW')">新增 {{ counts.NEW }}</el-tag>
          <el-tag type="warning" :effect="allTypeSelected('CHANGED') ? 'dark' : 'light'" class="tag-btn" @click="toggleType('CHANGED')">变更 {{ counts.CHANGED }}</el-tag>
          <el-tag type="danger" :effect="allTypeSelected('GONE') ? 'dark' : 'light'" class="tag-btn" @click="toggleType('GONE')">已消失 {{ counts.GONE }}</el-tag>
          <el-tag type="info">未变 {{ counts.SAME }}</el-tag>
          <span class="spacer" />
          <el-button size="small" @click="selectAll('none')">清空</el-button>
        </div>

        <el-tree
          v-if="previewTree.length"
          :data="previewTree"
          :props="{ label: 'name', children: 'children' }"
          default-expand-all
          :expand-on-click-node="false"
          class="cmp-list"
        >
          <template #default="{ data }">
            <span class="cmp-row" :class="{ gone: data.status === 'GONE' }">
              <el-checkbox v-model="selected[keyOf(data)]" :disabled="data.status === 'SAME'" />
              <el-icon v-if="data.type === 0" class="fd"><FolderOpened /></el-icon>
              <el-icon v-else class="fd"><User /></el-icon>
              <span class="nm">{{ data.name }}</span>
              <span v-if="data.account" class="acc">{{ data.account }}</span>
              <el-tag size="small" :type="tagType(data.status)" class="st">
                {{ statusText(data.status) }}
              </el-tag>
              <span class="dn" v-if="data.dn">{{ data.dn }}</span>
            </span>
          </template>
        </el-tree>
        <el-empty v-else description="当前数据库与 LDAP 完全一致，无需同步" />
      </div>
      <template #footer>
        <el-button @click="compareOpen = false">关闭</el-button>
        <el-button type="primary" :loading="applying" @click="applySelected">应用所选</el-button>
      </template>
    </el-drawer>

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
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { Search, Plus, Edit, Delete, Folder, Refresh, FolderOpened, User, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDepts, createDept, updateDept, deleteDept, listDeptRefs } from '@/api/dept'
import { previewSync, applySync } from '@/api/ldap'

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
const ldapCount = computed(() => list.value.filter(x => x.source === 'LDAP').length)

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
  const items = list.value.filter(x => x.source === activeTab.value)
  return filterDeptTree(buildDeptTree(items), k)
})

const dialog = ref(false)
const editing = ref(null)
const saving = ref(false)
const form = ref({ name: '', parentId: null })

async function load() {
  loading.value = true
  try {
    const [deptRes, refRes] = await Promise.all([listDepts(), listDeptRefs()])
    list.value = deptRes.data || []
    deptRefs.value = refRes.data || []
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

// ===== 同步对比抽屉 =====
const compareOpen = ref(false)
const compareLoading = ref(false)
const applying = ref(false)
const previewTree = ref([])
const flat = ref([])
const selected = reactive({})

function keyOf(n) {
  return n.type + ':' + (n.dn || n.account)
}
function statusText(s) {
  return { NEW: '新增', CHANGED: '变更', GONE: '已消失', SAME: '未变' }[s] || s
}
function tagType(s) {
  return { NEW: 'success', CHANGED: 'warning', GONE: 'danger', SAME: 'info' }[s] || 'info'
}
const counts = computed(() => {
  const c = { NEW: 0, CHANGED: 0, GONE: 0, SAME: 0 }
  flat.value.forEach((r) => { if (c[r.node.status] != null) c[r.node.status]++ })
  return c
})

function flatten(nodes, depth, acc) {
  for (const n of nodes || []) {
    acc.push({ node: n, depth, key: keyOf(n) })
    flatten(n.children, depth + 1, acc)
  }
}

async function openCompare() {
  compareOpen.value = true
  compareLoading.value = true
  try {
    const res = await previewSync()
    previewTree.value = res.data || []
    const list = []
    flatten(previewTree.value, 0, list)
    flat.value = list
    list.forEach((r) => { selected[r.key] = r.node.status === 'NEW' || r.node.status === 'CHANGED' })
  } catch (e) {} finally {
    compareLoading.value = false
  }
}

function selectAll(mode) {
  flat.value.forEach((r) => { selected[r.key] = false })
}

/** 该类型是否已全部勾选（用于标签高亮） */
function allTypeSelected(status) {
  const rows = flat.value.filter(r => r.node.status === status)
  return rows.length > 0 && rows.every(r => selected[r.key])
}

/** 点击统计标签：全选/取消该类型 */
function toggleType(status) {
  const rows = flat.value.filter(r => r.node.status === status)
  if (!rows.length) return
  const allSelected = rows.every(r => selected[r.key])
  rows.forEach(r => { selected[r.key] = !allSelected })
}

async function applySelected() {
  const addDns = []
  const removeDeptIds = []
  const removeUserIds = []
  flat.value.forEach((r) => {
    if (!selected[r.key]) return
    const n = r.node
    if (n.status === 'NEW' || n.status === 'CHANGED') addDns.push(n.dn)
    else if (n.status === 'GONE' && n.type === 0) removeDeptIds.push(n.id)
    else if (n.status === 'GONE' && n.type === 1) removeUserIds.push(n.id)
  })
  if (!addDns.length && !removeDeptIds.length && !removeUserIds.length) {
    return ElMessage.warning('请先勾选要同步的项')
  }
  applying.value = true
  try {
    const res = await applySync({ addDns, removeDeptIds, removeUserIds })
    ElMessage.success(res.data || '同步完成')
    await openCompare()
    await load()
  } catch (e) {} finally {
    applying.value = false
  }
}
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
