<template>
  <el-tabs v-model="activeTab" :before-leave="handleBeforeLeave" class="sync-tabs">
    <!-- Tab 1：定时同步（配置） -->
    <el-tab-pane label="定时同步" name="scheduled">
      <el-card shadow="never" class="settings">
        <template #header>
          <div class="hd">
            <span>
              定时同步配置
              <el-tag v-if="dirty" type="danger" size="small" effect="dark" class="dirty-tag">未保存</el-tag>
            </span>
            <el-button type="primary" :loading="saving" @click="saveConfig">保存配置</el-button>
          </div>
        </template>

        <el-form :model="cfg" label-width="130px" class="cfg-form">
          <!-- 周期配置 -->
          <div class="section">
            <div class="section-title">周期配置</div>
            <el-form-item label="自动同步">
              <el-switch v-model="cfg.syncEnabled" />
            </el-form-item>
            <el-form-item label="同步时间">
              <div class="time-rows">
                <div v-for="(t, i) in cfg.syncTimesList" :key="i" class="time-row">
                  <el-time-picker v-model="cfg.syncTimesList[i]" value-format="HH:mm" format="HH:mm" placeholder="选择时间" />
                  <el-button text type="danger" @click="removeTime(i)">移除</el-button>
                </div>
                <el-button text type="primary" @click="addTime">+ 添加时间</el-button>
              </div>
              <div class="hint">逗号分隔的多个时刻（如 10:00,20:00）；每天到点自动全量同步。</div>
            </el-form-item>
          </div>

          <el-divider />

          <!-- 基础配置 -->
          <div class="section">
            <div class="section-title">基础配置</div>
            <el-form-item label="LDAP 地址">
              <el-input v-model="cfg.url" placeholder="ldap://host:389" />
            </el-form-item>
            <el-form-item label="Base DN">
              <el-input v-model="cfg.baseDn" placeholder="dc=example,dc=com（仅登录搜索基与目录边界）" />
            </el-form-item>
            <el-form-item label="绑定账号">
              <el-input v-model="cfg.username" />
            </el-form-item>
            <el-form-item label="绑定密码">
              <el-input v-model="cfg.password" type="password" show-password placeholder="留空表示不修改" />
            </el-form-item>
            <el-form-item label="subTree（每行一个）">
              <el-input v-model="treesText" type="textarea" :rows="4" placeholder="ou=dept,dc=example,dc=com" />
              <div class="hint">同步与组织树仅展示这些根；未配置则不同步、不展示任何部门。</div>
            </el-form-item>
          </div>
        </el-form>
      </el-card>
    </el-tab-pane>

    <!-- Tab 2：手动同步（差异对比 / 立即全量） -->
    <el-tab-pane label="手动同步" name="manual">
      <el-card shadow="never">
        <template #header>
          <div class="hd">
            <span>手动同步</span>
            <div>
              <el-button :icon="Search" :loading="loading" @click="openCompare">刷新差异</el-button>
              <el-button type="success" :loading="syncingFull" @click="doFullSync">立即全量同步</el-button>
            </div>
          </div>
        </template>

        <el-alert v-if="status" type="info" :closable="false" show-icon>
          <template #title>上次同步</template>
          <div>状态：{{ status.lastStatus || '—' }}；时间：{{ status.lastSyncTime || '—' }}{{ status.running ? '（进行中…）' : '' }}</div>
        </el-alert>

        <!-- 说明卡片：四种状态语义 -->
        <el-alert class="tip" type="info" :closable="false" show-icon>
          <template #title>同步差异说明</template>
          <div class="tip-body">
            <div><b>新增</b>：LDAP 中存在、本地库中尚无 → 同步后<b>将新建</b>该部门/用户。</div>
            <div><b>变更</b>：两边都存在、但属性不一致 → 同步后<b>将以 LDAP 为准更新</b>（具体改了什么见下方明细）。</div>
            <div><b>已消失</b>：本地有、LDAP 已删除 → 同步后<b>将从本地删除</b>（关联授权一并清理，仅标记失效保留审计）。</div>
            <div class="tip-sub">未变化（两边一致）的条目不会展示；勾选支持父子联动（勾/取消部门会联动其下条目，勾选用户会联动其部门）；默认勾选「新增 + 变更」，「已消失」需手动勾选（删除较危险）。</div>
          </div>
        </el-alert>

        <div class="stat-bar">
          <el-tag type="success" :effect="allTypeSelected('NEW') ? 'dark' : 'light'" class="tag-btn" @click="toggleType('NEW')">新增 {{ diffCounts.NEW }}</el-tag>
          <el-tag type="warning" :effect="allTypeSelected('CHANGED') ? 'dark' : 'light'" class="tag-btn" @click="toggleType('CHANGED')">变更 {{ diffCounts.CHANGED }}</el-tag>
          <el-tag type="danger" :effect="allTypeSelected('GONE') ? 'dark' : 'light'" class="tag-btn" @click="toggleType('GONE')">已消失 {{ diffCounts.GONE }}</el-tag>
          <span class="spacer" />
          <el-button size="small" text @click="clearAll">清空勾选</el-button>
        </div>
        <el-tree v-if="diffTree.length" :data="diffTree" node-key="dn" :props="{ label: 'name', children: 'children' }" default-expand-all :expand-on-click-node="false" class="cmp-list" v-loading="loading">
          <template #default="{ data }">
            <div class="node-wrap">
              <span class="cmp-row" :class="{ gone: data.status === 'GONE' }">
                <el-checkbox v-if="!data._path" v-model="selected[keyOf(data)]" @change="(v) => onNodeCheck(data, v)" />
                <el-icon class="fd" :class="{ uf: data.type === 1 }">
                  <FolderOpened v-if="data.type === 0" />
                  <User v-else />
                </el-icon>
                <span class="nm" :class="{ path: data._path }">{{ data.name }}</span>
                <span class="acc" v-if="data.type === 1">{{ data.account }}</span>
                <el-tag v-if="!data._path" size="small" :type="tagType(data.status)" class="st">{{ statusText(data.status) }}</el-tag>
              </span>
              <div v-if="data.changes && data.changes.length" class="changes">
                <div v-for="c in data.changes" :key="c.field" class="change-item">{{ c.field }}：{{ c.oldVal || '—' }} <span class="arrow">→</span> {{ c.newVal || '—' }}</div>
              </div>
            </div>
          </template>
        </el-tree>
        <el-empty v-else description="暂无差异" />

        <template #footer>
          <div class="footer">
            <span class="summary">将 新增/更新 {{ selectedAddCount }} 个、删除 {{ selectedRemoveCount }} 个</span>
            <el-button @click="clearAll">清空勾选</el-button>
            <el-button type="primary" :loading="applying" @click="applySelected">应用所选</el-button>
          </div>
        </template>
      </el-card>
    </el-tab-pane>
  </el-tabs>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { Search, FolderOpened, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { previewSync, applySync } from '@/api/ldap'
import { getLdapConfig, updateLdapConfig, triggerFullSync, getSyncStatus } from '@/api/config'

const activeTab = ref('scheduled')
const loading = ref(false)
const applying = ref(false)
const saving = ref(false)
const syncingFull = ref(false)
const previewTree = ref([])
const flat = ref([])
const selected = reactive({})
const cfg = reactive({ syncEnabled: true, syncTimesList: [], url: '', baseDn: '', username: '', password: '' })
const treesText = ref('')
const status = ref(null)

/** 配置快照：loadConfig 成功后记录，用于脏检测 */
const snapshot = ref(null)
function buildSnapshot() {
  return {
    url: cfg.url,
    baseDn: cfg.baseDn,
    username: cfg.username,
    password: cfg.password,
    syncEnabled: cfg.syncEnabled,
    syncTimes: cfg.syncTimesList.filter(Boolean).join(','),
    trees: treesText.value.split('\n').map((s) => s.trim()).filter(Boolean).join('\n')
  }
}
const dirty = computed(() => {
  if (!snapshot.value) return false
  return JSON.stringify(buildSnapshot()) !== JSON.stringify(snapshot.value)
})

function keyOf(n) {
  return n.type + ':' + (n.dn || n.account)
}
function statusText(s) {
  return { NEW: '新增', CHANGED: '变更', GONE: '已消失', SAME: '未变' }[s] || s
}
function tagType(s) {
  return { NEW: 'success', CHANGED: 'warning', GONE: 'danger', SAME: 'info' }[s] || 'info'
}
/** 平铺原树，供勾选/统计/应用使用 */
function flatten(nodes, acc) {
  for (const n of nodes || []) {
    acc.push({ node: n, key: keyOf(n) })
    flatten(n.children, acc)
  }
}

async function openCompare() {
  loading.value = true
  try {
    const res = await previewSync()
    previewTree.value = res.data || []
    const list = []
    flatten(previewTree.value, list)
    flat.value = list
    list.forEach((r) => { selected[r.key] = r.node.status === 'NEW' || r.node.status === 'CHANGED' })
  } catch (e) {} finally {
    loading.value = false
  }
}

/**
 * 递归剪枝统一差异树：
 * - 部门(type=0)：自身未变但有差异子孙 → 保留为「路径节点」(_path=true，仅层级上下文，不可勾选)；无差异叶子分支整支剪掉。
 * - 用户(type=1)：仅保留有差异(NEW/CHANGED/GONE)的节点作为叶子。
 */
function pickNode(n) {
  if (n.type !== 0) {
    return n.status === 'SAME' ? null : { ...n, children: undefined }
  }
  const children = (n.children || []).map(pickNode).filter(Boolean)
  if (n.status === 'SAME' && !children.length) return null
  return { ...n, _path: n.status === 'SAME', children: children.length ? children : undefined }
}
/** 单棵差异树：部门为枝、用户为叶 */
const diffTree = computed(() => previewTree.value.map(pickNode).filter(Boolean))

/** 树索引：key → 父 key / 子 key 列表 / 节点，用于勾选联动 */
const treeIndex = computed(() => {
  const parent = {}
  const children = {}
  const byKey = {}
  const walk = (nodes, pk) => {
    for (const n of nodes || []) {
      const k = keyOf(n)
      byKey[k] = n
      parent[k] = pk
      children[k] = children[k] || []
      if (pk) children[pk].push(k)
      walk(n.children, k)
    }
  }
  walk(diffTree.value, null)
  return { parent, children, byKey }
})

/** 某节点全部子孙的 key（含各级） */
function descendantKeys(k) {
  const out = []
  const stack = [...(treeIndex.value.children[k] || [])]
  while (stack.length) {
    const c = stack.pop()
    out.push(c)
    stack.push(...(treeIndex.value.children[c] || []))
  }
  return out
}

/**
 * 勾选联动：
 * - 勾选：联动勾选全部可勾选子孙 + 全部可勾选祖先（路径节点无勾选框，跳过）。
 * - 取消：联动取消全部子孙；向上收缩——祖先若无仍勾选的子孙也一并取消。
 */
function onNodeCheck(node, val) {
  const { parent, byKey } = treeIndex.value
  const k = keyOf(node)
  if (val) {
    descendantKeys(k).forEach((ck) => {
      const d = byKey[ck]
      if (d && !d._path) selected[ck] = true
    })
    let pk = parent[k]
    while (pk) {
      const p = byKey[pk]
      if (p && !p._path) selected[pk] = true
      pk = parent[pk]
    }
  } else {
    descendantKeys(k).forEach((ck) => { selected[ck] = false })
    let pk = parent[k]
    while (pk) {
      const p = byKey[pk]
      if (p && !p._path) {
        if (descendantKeys(pk).some((ck) => selected[ck])) break
        selected[pk] = false
      }
      pk = parent[pk]
    }
  }
}

function countBy(rows) {
  const c = { NEW: 0, CHANGED: 0, GONE: 0, SAME: 0 }
  rows.forEach((r) => { if (c[r.node.status] != null) c[r.node.status]++ })
  return c
}
const diffCounts = computed(() => countBy(flat.value))

function allTypeSelected(status) {
  const rs = flat.value.filter((r) => r.node.status === status)
  return rs.length > 0 && rs.every((r) => selected[r.key])
}
function toggleType(status) {
  const rs = flat.value.filter((r) => r.node.status === status)
  if (!rs.length) return
  const allSelected = rs.every((r) => selected[r.key])
  rs.forEach((r) => { selected[r.key] = !allSelected })
}
function clearAll() {
  flat.value.forEach((r) => { selected[r.key] = false })
}

const selectedAddCount = computed(() =>
  flat.value.filter((r) => selected[r.key] && (r.node.status === 'NEW' || r.node.status === 'CHANGED')).length
)
const selectedRemoveCount = computed(() =>
  flat.value.filter((r) => selected[r.key] && r.node.status === 'GONE').length
)

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
  const msg = `将 新增/更新 ${addDns.length} 个、删除 ${removeDeptIds.length + removeUserIds.length} 个，确认应用？`
  try {
    await ElMessageBox.confirm(msg, '应用所选', { type: 'warning', confirmButtonText: '确认应用', cancelButtonText: '取消' })
  } catch (e) { return }
  applying.value = true
  try {
    const res = await applySync({ addDns, removeDeptIds, removeUserIds })
    ElMessage.success(res.data || '同步完成')
    await openCompare()
  } catch (e) {} finally {
    applying.value = false
  }
}

onMounted(() => { openCompare(); loadConfig(); loadStatus() })

async function loadConfig() {
  try {
    const res = await getLdapConfig()
    const d = res.data || {}
    cfg.url = d.url || ''
    cfg.baseDn = d.base || ''
    cfg.username = d.username || ''
    cfg.password = ''
    cfg.syncEnabled = d.syncEnabled !== false
    cfg.syncTimesList = (d.syncTimes ? String(d.syncTimes).split(',') : []).map((s) => s.trim()).filter(Boolean)
    treesText.value = (d.trees || []).join('\n')
    snapshot.value = buildSnapshot()
  } catch (e) {}
}

async function loadStatus() {
  try {
    const res = await getSyncStatus()
    status.value = res.data || null
  } catch (e) {}
}

function addTime() {
  cfg.syncTimesList.push('')
}
function removeTime(i) {
  cfg.syncTimesList.splice(i, 1)
}

async function saveConfig() {
  saving.value = true
  try {
    const payload = {
      url: cfg.url,
      baseDn: cfg.baseDn,
      username: cfg.username,
      password: cfg.password || '',
      trees: treesText.value.split('\n').map((s) => s.trim()).filter(Boolean),
      syncTimes: cfg.syncTimesList.filter(Boolean).join(','),
      syncEnabled: cfg.syncEnabled
    }
    const res = await updateLdapConfig(payload)
    ElMessage.success(res.data || '配置保存成功')
    snapshot.value = buildSnapshot()
    await loadStatus()
  } catch (e) {} finally {
    saving.value = false
  }
}

async function doFullSync() {
  syncingFull.value = true
  try {
    const res = await triggerFullSync()
    const data = res.data || {}
    if (data.status === 'BUSY') {
      ElMessage.warning(data.message || '当前正在同步，请稍后重试')
      return
    }
    ElMessage.success(data.message || '已触发全量同步')
    startSyncPolling()
  } catch (e) {} finally {
    syncingFull.value = false
  }
}

/** 轮询同步状态，后台同步完成后自动刷新差异树 */
let pollTimer = null
function startSyncPolling() {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = setInterval(async () => {
    try {
      const res = await getSyncStatus()
      const s = (res && res.data) || {}
      if (s.running === false) {
        clearInterval(pollTimer)
        pollTimer = null
        await loadStatus()
        await openCompare()
        ElMessage.success('全量同步已完成')
      }
    } catch (e) {}
  }, 3000)
}

/** 离开「定时同步」tab 前，若有未保存改动则确认 */
async function handleBeforeLeave(newName, oldName) {
  if (oldName === 'scheduled' && dirty.value) {
    try {
      await ElMessageBox.confirm('有未保存的改动，确定离开并放弃更改？', '未保存', {
        type: 'warning',
        confirmButtonText: '放弃更改',
        cancelButtonText: '留在本页'
      })
      return true
    } catch (e) {
      return false
    }
  }
  return true
}

/** 路由离开本页时拦截 */
onBeforeRouteLeave(async (to, from) => {
  if (activeTab.value === 'scheduled' && dirty.value) {
    try {
      await ElMessageBox.confirm('有未保存的改动，确定离开并放弃更改？', '未保存', {
        type: 'warning',
        confirmButtonText: '放弃更改',
        cancelButtonText: '留在本页'
      })
      return true
    } catch (e) {
      return false
    }
  }
  return true
})

/** 浏览器刷新 / 关闭时拦截 */
function beforeUnloadHandler(e) {
  if (activeTab.value === 'scheduled' && dirty.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}
onMounted(() => { window.addEventListener('beforeunload', beforeUnloadHandler) })
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnloadHandler)
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
})
</script>

<style scoped>
.sync-tabs { margin-bottom: 16px; }
.hd { display: flex; justify-content: space-between; align-items: center; }
.dirty-tag { margin-left: 8px; vertical-align: middle; }
.tip { margin-bottom: 14px; }
.tip-body { font-size: 13px; line-height: 1.9; color: #606266; }
.tip-sub { margin-top: 4px; color: #909399; }
.stat-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; }
.stat-bar .spacer { flex: 1; }
.tag-btn { cursor: pointer; user-select: none; }
.cmp-list { border: 1px solid #ebeef5; border-radius: 4px; padding: 4px; }
/* el-tree 节点默认固定 26px 高会裁切多行内容，改为自适应 */
.cmp-list :deep(.el-tree-node__content) { height: auto; min-height: 26px; padding: 2px 0; }
/* el-tree 节点内容区默认是横向 flex，包一层纵向容器让「主行 + 变更明细」上下堆叠 */
.node-wrap { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; }
.cmp-row { display: flex; align-items: center; gap: 6px; padding: 4px 8px; width: 100%; min-width: 0; flex-wrap: wrap; }
.cmp-row.gone { background: #fef0f0; }
.cmp-row .fd { flex-shrink: 0; color: #e6a23c; }
.cmp-row .fd.uf { color: #409eff; }
.cmp-row .nm { font-weight: 500; flex-shrink: 0; }
.cmp-row .nm.path { color: #c0c4cc; font-weight: 400; }
.cmp-row .acc { color: #909399; font-size: 12px; }
.cmp-row .st { margin-left: 4px; flex-shrink: 0; }
.changes { width: 100%; padding: 2px 0 2px 32px; }
.change-item { font-size: 12px; color: #e6a23c; line-height: 1.7; }
.change-item .arrow { color: #909399; margin: 0 2px; }
.footer { display: flex; align-items: center; justify-content: flex-end; gap: 12px; }
.footer .summary { color: #606266; font-size: 13px; margin-right: auto; }
.cfg-form { max-width: 760px; }
.section { padding-bottom: 4px; }
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  padding-left: 8px;
  border-left: 3px solid #409eff;
}
.time-rows { display: flex; flex-direction: column; gap: 8px; }
.time-row { display: flex; align-items: center; gap: 8px; }
.hint { color: #909399; font-size: 12px; line-height: 1.6; margin-top: 4px; }
</style>
