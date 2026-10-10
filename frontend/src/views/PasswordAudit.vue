<template>
  <div>
    <h2 class="page-title">密码审计日志</h2>

    <div class="pa-card">
      <div class="filter-bar">
        <div class="filter-item">
          <label class="req">UID</label>
          <el-input v-model="filters.uid" placeholder="文件指纹ID(必填)" clearable @keyup.enter="onSearch" />
        </div>
        <div class="filter-item">
          <label class="req">开始日期</label>
          <el-date-picker v-model="filters.startTime" type="date" placeholder="起(必填)"
            format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width: 160px" />
        </div>
        <div class="filter-item">
          <label class="req">结束日期</label>
          <el-date-picker v-model="filters.endTime" type="date" placeholder="止(必填)"
            format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width: 160px" />
        </div>
        <div class="filter-item">
          <label>路径</label>
          <el-input v-model="filters.path" placeholder="文件路径(可选)" clearable @keyup.enter="onSearch" />
        </div>
        <div class="filter-item">
          <label>操作人</label>
          <el-input v-model="filters.user" placeholder="createBy(可选)" clearable @keyup.enter="onSearch" />
        </div>
        <div class="filter-item">
          <label>平台</label>
          <el-select v-model="filters.platform" placeholder="全部" clearable style="width: 130px">
            <el-option label="全部" value="" />
            <el-option label="Windows" value="win" />
            <el-option label="Android" value="android" />
          </el-select>
        </div>
        <div class="filter-actions">
          <button class="btn btn-primary" :disabled="!canSearch || loading" @click="onSearch">{{ loading ? '查询中…' : '查询' }}</button>
          <button class="btn btn-ghost" @click="onReset">重置</button>
        </div>
      </div>

      <div class="info-line">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/></svg>
        <span>日志从本地文件按需检索，仅扫描所选日期范围内的文件、不预先加载全部；「解密」复用系统私钥，明文仅在弹窗展示，不写入任何日志。</span>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th style="width: 180px">时间</th>
              <th style="width: 140px">UID</th>
              <th>路径</th>
              <th style="width: 100px">平台</th>
              <th style="width: 110px">操作人</th>
              <th style="width: 80px">密钥</th>
              <th style="width: 90px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, idx) in list" :key="idx">
              <td class="mono">{{ row.timestamp }}</td>
              <td class="mono">{{ row.uid }}</td>
              <td class="path" :title="row.path">{{ row.path }}</td>
              <td>{{ platformLabel(row.platform) }}</td>
              <td>{{ row.createBy }}</td>
              <td class="mono">{{ row.keyVersion }}</td>
              <td>
                <button class="op" @click="decryptRow(row)">解密</button>
              </td>
            </tr>
            <tr v-if="list.length === 0">
              <td class="empty" colspan="7">{{ loading ? '查询中…' : (searched ? '无匹配记录' : '请填写 UID 与日期范围后点击「查询」') }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="table-foot">
        <span>共 {{ total }} 条记录</span>
        <div class="foot-right">
          <el-select v-model="pageSize" size="small" style="width: 110px" @change="onSizeChange">
            <el-option v-for="s in [10, 20, 50, 100]" :key="s" :label="s + ' 条/页'" :value="s" />
          </el-select>
          <div class="pager" v-if="totalPages > 1">
            <button class="page-btn" :disabled="currentPage === 1" @click="changePage(currentPage - 1)">‹</button>
            <button v-for="n in totalPages" :key="n" class="page-btn" :class="{ current: n === currentPage }" @click="changePage(n)">{{ n }}</button>
            <button class="page-btn" :disabled="currentPage === totalPages" @click="changePage(currentPage + 1)">›</button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="dlg.visible" title="记录解密" width="560px">
      <div v-if="dlg.row" class="dlg-meta">
        <div class="dlg-row"><div class="dlg-label">时间</div><div class="dlg-val mono">{{ dlg.row.timestamp }}</div></div>
        <div class="dlg-row"><div class="dlg-label">UID</div><div class="dlg-val mono">{{ dlg.row.uid }}</div></div>
        <div class="dlg-row"><div class="dlg-label">路径</div><div class="dlg-val break">{{ dlg.row.path }}</div></div>
        <div class="dlg-row"><div class="dlg-label">平台</div><div class="dlg-val">{{ platformLabel(dlg.row.platform) }}</div></div>
        <div class="dlg-row"><div class="dlg-label">操作人</div><div class="dlg-val">{{ dlg.row.createBy }}</div></div>
        <div class="dlg-row"><div class="dlg-label">密钥版本</div><div class="dlg-val mono">{{ dlg.row.keyVersion }}</div></div>
      </div>
      <el-divider>密文 → 明文</el-divider>
      <div class="dlg-decrypt">
        <div class="dlg-row">
          <div class="dlg-label">改前密码</div>
          <div class="dlg-val break mono">{{ dlg.loading ? '解密中…' : (dlg.before !== null ? dlg.before : (dlg.row && dlg.row.beforePassword)) }}</div>
        </div>
        <div class="dlg-row">
          <div class="dlg-label">改后密码</div>
          <div class="dlg-val break mono">{{ dlg.loading ? '解密中…' : (dlg.after !== null ? dlg.after : (dlg.row && dlg.row.afterPassword)) }}</div>
        </div>
        <div class="dlg-row" v-if="dlg.row && dlg.row.possiblePasswords && dlg.row.possiblePasswords.length">
          <div class="dlg-label">可能密码</div>
          <div class="dlg-val break mono">{{ dlg.loading ? '解密中…' : (dlg.possible && dlg.possible.length ? dlg.possible.join(' , ') : dlg.row.possiblePasswords.join(' , ')) }}</div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="dlg.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { queryAudit, decryptText } from '@/api/audit'

const filters = reactive({ uid: '', path: '', user: '', platform: '', startTime: '', endTime: '' })
const loading = ref(false)
const searched = ref(false)
const list = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

const canSearch = computed(() =>
  filters.uid.trim() && filters.startTime && filters.endTime)

const dlg = reactive({ visible: false, loading: false, row: null, before: null, after: null, possible: [] })

const platformLabel = (p) => (p === 'win' ? 'Windows' : p === 'android' ? 'Android' : (p || '—'))

function buildPayload() {
  const p = {
    page: currentPage.value,
    size: pageSize.value,
    uid: filters.uid.trim(),
    startTime: filters.startTime,
    endTime: filters.endTime
  }
  if (filters.path) p.path = filters.path
  if (filters.user) p.user = filters.user
  if (filters.platform) p.platform = filters.platform
  return p
}

async function load() {
  loading.value = true
  searched.value = true
  try {
    const res = await queryAudit(buildPayload())
    const data = res.data || {}
    list.value = data.list || []
    total.value = data.total || 0
  } catch (e) {
    const msg = e && e.response && e.response.data && e.response.data.msg
    ElMessage.error(msg || '查询失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  if (!canSearch.value) {
    ElMessage.warning('UID 与起止日期均为必填')
    return
  }
  currentPage.value = 1
  load()
}
function onReset() {
  filters.uid = ''
  filters.path = ''
  filters.user = ''
  filters.platform = ''
  filters.startTime = ''
  filters.endTime = ''
  currentPage.value = 1
  searched.value = false
  list.value = []
  total.value = 0
}
function changePage(n) {
  if (n >= 1 && n <= totalPages.value && n !== currentPage.value) {
    currentPage.value = n
    load()
  }
}
function onSizeChange() { currentPage.value = 1; load() }

async function decryptRow(row) {
  dlg.visible = true
  dlg.row = row
  dlg.loading = true
  dlg.before = null
  dlg.after = null
  dlg.possible = []
  try {
    const jobs = []
    jobs.push(row.beforePassword
      ? decryptText(row.beforePassword, row.keyVersion).then(r => (r.data && r.data.decryptedText) || '')
      : Promise.resolve(''))
    jobs.push(row.afterPassword
      ? decryptText(row.afterPassword, row.keyVersion).then(r => (r.data && r.data.decryptedText) || '')
      : Promise.resolve(''))
    const poss = (row.possiblePasswords && row.possiblePasswords.length)
      ? row.possiblePasswords.map(p => decryptText(p, row.keyVersion).then(r => (r.data && r.data.decryptedText) || ''))
      : []
    const [b, a] = await Promise.all(jobs)
    dlg.before = b
    dlg.after = a
    dlg.possible = await Promise.all(poss)
  } catch (e) {
    const msg = e && e.response && e.response.data && e.response.data.msg
    ElMessage.error(msg || '解密失败')
  } finally {
    dlg.loading = false
  }
}
</script>

<style scoped>
.page-title { margin: 0 0 14px; font-size: 20px; color: #222; }

.pa-card { background: #fff; border: 1px solid #E5E7EB; border-radius: 8px; box-shadow: 0 1px 2px rgba(16,24,40,.05); overflow: hidden; }

.filter-bar { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 12px; padding: 16px 20px; border-bottom: 1px solid #E5E7EB; }
.filter-item { display: flex; flex-direction: column; gap: 4px; }
.filter-item label { font-size: 12px; color: #86909C; }
.filter-item label.req::after { content: ' *'; color: #F53F3F; }
.filter-actions { display: flex; gap: 8px; margin-left: auto; }
.btn { display: inline-flex; align-items: center; gap: 6px; height: 34px; padding: 0 14px; border-radius: 4px; font-size: 13px; cursor: pointer; border: 1px solid transparent; }
.btn-primary { background: #2E5CE6; color: #fff; font-weight: 500; }
.btn-primary:hover:not(:disabled) { background: #2449BF; }
.btn-primary:disabled { background: #B9C4EE; cursor: not-allowed; }
.btn-ghost { background: #fff; color: #4E5969; border-color: #D1D5DB; }
.btn-ghost:hover { border-color: #2E5CE6; color: #2E5CE6; }

.info-line { display: flex; align-items: center; gap: 8px; margin: 14px 20px 0; padding: 8px 12px; background: #EEF2FF; border-radius: 4px; color: #4E5969; font-size: 13px; }
.info-line svg { flex-shrink: 0; color: #2E5CE6; }

.table-wrap { padding: 6px 20px 20px; }
table { width: 100%; border-collapse: collapse; }
thead th { text-align: left; font-size: 12px; font-weight: 500; color: #86909C; background: #FAFBFC; padding: 10px 12px; border-bottom: 1px solid #E5E7EB; white-space: nowrap; }
tbody td { padding: 11px 12px; border-bottom: 1px solid #E5E7EB; color: #1F2329; vertical-align: middle; }
tbody tr:last-child td { border-bottom: none; }
tbody tr:hover { background: #F8F9FB; }
.mono { font-family: Consolas, monospace; font-variant-numeric: tabular-nums; white-space: nowrap; }
.path { max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #4E5969; }
.empty { text-align: center; color: #86909C; padding: 32px; }

.op { padding: 4px 10px; border-radius: 4px; font-size: 12px; color: #2E5CE6; cursor: pointer; background: none; border: 1px solid #D1D5DB; white-space: nowrap; transition: all .12s; }
.op:hover { border-color: #2E5CE6; background: #EEF2FF; }

.table-foot { display: flex; align-items: center; justify-content: space-between; padding: 12px 20px; border-top: 1px solid #E5E7EB; color: #86909C; font-size: 13px; }
.foot-right { display: flex; align-items: center; gap: 12px; }
.pager { display: flex; align-items: center; gap: 6px; }
.page-btn { min-width: 28px; height: 28px; display: inline-flex; align-items: center; justify-content: center; border: 1px solid #D1D5DB; border-radius: 4px; background: #fff; color: #4E5969; font-size: 13px; cursor: pointer; padding: 0 6px; }
.page-btn:hover:not(:disabled) { border-color: #2E5CE6; color: #2E5CE6; }
.page-btn.current { background: #2E5CE6; border-color: #2E5CE6; color: #fff; }
.page-btn:disabled { color: #C0C4CC; cursor: not-allowed; border-color: #E5E7EB; }

.dlg-meta { margin-bottom: 4px; }
.dlg-row { display: flex; gap: 12px; padding: 8px 0; border-bottom: 1px solid #F0F2F5; }
.dlg-row:last-child { border-bottom: none; }
.dlg-label { width: 72px; flex-shrink: 0; color: #86909C; font-size: 13px; padding-top: 2px; }
.dlg-val { flex: 1; color: #1F2329; font-size: 13px; word-break: break-all; }
.dlg-val.break { word-break: break-all; white-space: pre-wrap; }
</style>
