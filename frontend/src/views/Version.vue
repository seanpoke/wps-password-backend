<template>
  <div>
    <h2 class="page-title">客户端版本管理</h2>

    <div class="vm-card">
      <div class="card-head">
        <h2>版本列表</h2>
        <div class="head-actions">
          <el-select v-model="platform" placeholder="平台" style="width: 140px" @change="onPlatformChange">
            <el-option v-for="p in PLATFORMS" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
          <button class="btn btn-primary" @click="openAdd">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M12 5v14M5 12h14"/></svg>
            新增版本
          </button>
        </div>
      </div>

      <div class="info-line">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/></svg>
        <span>版本状态自动派生：低于「最低」基线的客户端将被强制升级；高于「最新」的版本标记为「未发布」；介于最低与最新之间为「可用」，最新发布为「最新」。把某版本同时设为最低 + 最新，即强制全员升级到该版本。</span>
      </div>

      <!-- 状态汇总条：常驻，不受筛选影响 -->
      <div class="status-strip">
        <button class="chip" :class="{ active: activeFilter === 'all' }" @click="setFilter('all')">
          <span class="chip-label">全部版本</span>
          <span class="chip-val">{{ scopeTotal }}</span>
        </button>
        <button class="chip" :class="{ active: activeFilter === 'latest' }" @click="setFilter('latest')">
          <span class="chip-label"><span class="dot dot-latest"></span>最新</span>
          <span class="chip-val">{{ latestVersion ? 'v' + latestVersion : '—' }}</span>
        </button>
        <button class="chip" :class="{ active: activeFilter === 'lowest' }" @click="setFilter('lowest')">
          <span class="chip-label"><span class="dot dot-lowest"></span>最低（强制基线）</span>
          <span class="chip-val">{{ minVersion ? 'v' + minVersion : '—' }}</span>
        </button>
        <button class="chip" :class="{ active: activeFilter === 'available' }" @click="setFilter('available')">
          <span class="chip-label"><span class="dot dot-available"></span>可用</span>
          <span class="chip-val">{{ statusCounts.available || 0 }}</span>
        </button>
        <button class="chip" :class="{ active: activeFilter === 'expired' }" @click="setFilter('expired')">
          <span class="chip-label"><span class="dot dot-expired"></span>过期</span>
          <span class="chip-val">{{ statusCounts.expired || 0 }}</span>
        </button>
        <button class="chip" :class="{ active: activeFilter === 'unreleased' }" @click="setFilter('unreleased')">
          <span class="chip-label"><span class="dot dot-unreleased"></span>未发布</span>
          <span class="chip-val">{{ statusCounts.unreleased || 0 }}</span>
        </button>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th style="width: 96px">状态</th>
              <th style="width: 130px">版本号</th>
              <th style="width: 110px">下载地址</th>
              <th>更新说明</th>
              <th style="width: 140px">修改时间</th>
              <th style="width: 260px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in list" :key="row.id">
              <td>
                <span v-for="st in row.states" :key="st" class="badge" :class="badgeClass(st)">
                  <svg v-if="st === 'latest'" width="11" height="11" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2l2.9 6.6 7.1.6-5.4 4.7 1.6 7L12 17.3 5.8 20.9l1.6-7L2 9.2l7.1-.6z"/></svg>
                  <svg v-else-if="st === 'lowest'" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M12 5v14M5 12l7 7 7-7"/></svg>
                  <svg v-else-if="st === 'expired'" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M12 8v4M12 16h.01"/><circle cx="12" cy="12" r="9"/></svg>
                  <svg v-else-if="st === 'unreleased'" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>
                  <svg v-else width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/></svg>
                  {{ badgeText(st) }}
                </span>
              </td>
              <td class="version">v{{ row.version }}</td>
              <td>
                <a v-if="row.downloadUrl" class="dl-link" :href="row.downloadUrl" target="_blank" rel="noopener">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="M7 10l5 5 5-5"/><path d="M12 15V3"/></svg>
                  下载
                </a>
                <span v-else class="dl-muted">—</span>
              </td>
              <td class="desc">{{ row.changelog || '—' }}</td>
              <td class="time">{{ row.updateTime || '—' }}</td>
              <td>
                <div class="ops">
                  <button class="op" @click="openEdit(row)">编辑</button>
                  <button v-if="!row.isLatest" class="op" @click="onSetLatest(row)">设为最新</button>
                  <button v-if="!row.isMin" class="op" @click="onSetMin(row)">设为最低</button>
                  <span class="op-divider"></span>
                  <button class="op danger" :disabled="row.isLatest" @click="onDelete(row)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="list.length === 0">
              <td class="empty" colspan="6">该状态下暂无版本</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="table-foot">
        <span>共 {{ total }} 条记录</span>
        <div class="foot-right">
          <el-select v-model="pageSize" size="small" style="width: 110px" @change="onSizeChange">
            <el-option v-for="s in [5, 10, 20, 50]" :key="s" :label="s + ' 条/页'" :value="s" />
          </el-select>
          <div class="pager" v-if="totalPages > 1">
            <button class="page-btn" :disabled="currentPage === 1" @click="changePage(currentPage - 1)">‹</button>
            <button v-for="n in totalPages" :key="n" class="page-btn" :class="{ current: n === currentPage }" @click="changePage(n)">{{ n }}</button>
            <button class="page-btn" :disabled="currentPage === totalPages" @click="changePage(currentPage + 1)">›</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 新增对话框 -->
    <el-dialog v-model="addVisible" title="新增版本" width="560px" @closed="resetForm">
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="平台" prop="platform">
          <el-select v-model="form.platform" placeholder="请选择平台" style="width: 100%">
            <el-option v-for="p in PLATFORMS" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本号" prop="version">
          <el-input v-model="form.version" placeholder="如 1.2.0" />
        </el-form-item>
        <el-form-item label="安装包" :required="true">
          <div v-if="form.downloadUrl" class="pkg-card" :class="{ dragging: dragOver }"
            @dragover.prevent="dragOver = true"
            @dragleave.prevent="dragOver = false"
            @drop.prevent="onDrop">
            <div class="pkg-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/></svg>
            </div>
            <div class="pkg-info">
              <div class="pkg-name">{{ pkgFileName }}</div>
              <div class="pkg-path">{{ form.downloadUrl }}</div>
            </div>
            <div class="pkg-ops">
              <button type="button" class="pkg-op" :disabled="uploading" @click="triggerUpload">{{ uploading ? '上传中…' : '重新上传' }}</button>
              <button type="button" class="pkg-op danger" :disabled="uploading" @click="removePackage">移除</button>
            </div>
          </div>
          <div v-else class="pkg-empty" :class="{ uploading, dragging: dragOver }"
            @click="!uploading && triggerUpload()"
            @dragover.prevent="dragOver = true"
            @dragleave.prevent="dragOver = false"
            @drop.prevent="onDrop">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="M17 8l-5-5-5 5"/><path d="M12 3v12"/></svg>
            <span>{{ uploading ? '上传中…' : '点击或拖拽安装包到此处上传' }}</span>
            <span class="pkg-hint">{{ form.platform === 'win' ? 'Windows 平台仅支持 .zip' : form.platform === 'android' ? 'Android 平台仅支持 .apk' : '请先选择平台' }}，上传后自动生成下载地址</span>
          </div>
        </el-form-item>
        <el-form-item label="更新说明">
          <el-input v-model="form.changelog" type="textarea" :rows="2" placeholder="本次更新内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 编辑对话框 -->
    <el-dialog v-model="editVisible" title="编辑版本" width="560px" @closed="resetForm">
      <el-form :model="form" label-width="100px" ref="formRef">
        <el-form-item label="平台">
          <el-input :model-value="platformLabel(form.platform)" disabled />
        </el-form-item>
        <el-form-item label="版本号">
          <el-input :model-value="form.version" disabled />
        </el-form-item>
        <el-form-item label="安装包" :required="true">
          <div v-if="form.downloadUrl" class="pkg-card" :class="{ dragging: dragOver }"
            @dragover.prevent="dragOver = true"
            @dragleave.prevent="dragOver = false"
            @drop.prevent="onDrop">
            <div class="pkg-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/></svg>
            </div>
            <div class="pkg-info">
              <div class="pkg-name">{{ pkgFileName }}</div>
              <div class="pkg-path">{{ form.downloadUrl }}</div>
            </div>
            <div class="pkg-ops">
              <button type="button" class="pkg-op" :disabled="uploading" @click="triggerUpload">{{ uploading ? '上传中…' : '重新上传' }}</button>
              <button type="button" class="pkg-op danger" :disabled="uploading" @click="removePackage">移除</button>
            </div>
          </div>
          <div v-else class="pkg-empty" :class="{ uploading, dragging: dragOver }"
            @click="!uploading && triggerUpload()"
            @dragover.prevent="dragOver = true"
            @dragleave.prevent="dragOver = false"
            @drop.prevent="onDrop">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="M17 8l-5-5-5 5"/><path d="M12 3v12"/></svg>
            <span>{{ uploading ? '上传中…' : '点击或拖拽安装包到此处上传' }}</span>
            <span class="pkg-hint">{{ form.platform === 'win' ? 'Windows 平台仅支持 .zip' : form.platform === 'android' ? 'Android 平台仅支持 .apk' : '请先选择平台' }}，上传后自动生成下载地址</span>
          </div>
        </el-form-item>
        <el-form-item label="更新说明">
          <el-input v-model="form.changelog" type="textarea" :rows="2" placeholder="本次更新内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 隐藏的文件选择器：两个对话框共用 -->
    <input
      ref="fileInput"
      type="file"
      :accept="acceptExts"
      style="display: none"
      @change="onFileChange" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listVersion, createVersion, updateVersion, setMinVersion, setLatestVersion, deleteVersion, uploadVersionPackage } from '@/api/version'

const PLATFORMS = [
  { value: 'win', label: 'Windows' },
  { value: 'android', label: 'Android' }
]
const platformLabel = (p) => (PLATFORMS.find((x) => x.value === p) || {}).label || p

const platform = ref('win')
const loading = ref(false)
const saving = ref(false)
const list = ref([])
const addVisible = ref(false)
const editVisible = ref(false)
const editId = ref(null)
const formRef = ref(null)
const fileInput = ref(null)
const uploading = ref(false)
const uploadFileName = ref('')
const dragOver = ref(false)
const form = reactive({
  platform: '', version: '', downloadUrl: '', changelog: ''
})
const rules = {
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }],
  version: [{ required: true, message: '请输入版本号', trigger: 'blur' }]
}

const activeFilter = ref('all')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const scopeTotal = ref(0)
const latestVersion = ref(null)
const minVersion = ref(null)
const statusCounts = ref({})

function validateVersion(v) {
  return /^\d+(\.\d+){0,3}$/.test(v || '')
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

const pkgFileName = computed(() => {
  const url = form.downloadUrl || ''
  if (!url) return ''
  try {
    const path = /^https?:\/\//i.test(url) ? new URL(url).pathname : url
    return decodeURIComponent(path.split('/').filter(Boolean).pop() || url)
  } catch {
    return url
  }
})

function removePackage() {
  form.downloadUrl = ''
  uploadFileName.value = ''
}

const acceptExts = computed(() => {
  if (form.platform === 'win') return '.zip'
  if (form.platform === 'android') return '.apk'
  return '.zip,.apk'
})

function extAllowed(file) {
  const name = (file.name || '').toLowerCase()
  if (form.platform === 'win') return name.endsWith('.zip')
  if (form.platform === 'android') return name.endsWith('.apk')
  return name.endsWith('.zip') || name.endsWith('.apk')
}

function badgeClass(st) {
  return {
    latest: 'badge-latest',
    lowest: 'badge-lowest',
    available: 'badge-available',
    expired: 'badge-expired',
    unreleased: 'badge-unreleased'
  }[st]
}
function badgeText(st) {
  return { latest: '最新', lowest: '最低', available: '可用', expired: '过期', unreleased: '未发布' }[st]
}

function setFilter(f) {
  if (activeFilter.value === f) return
  activeFilter.value = f
  currentPage.value = 1
  loadList()
}
function changePage(n) {
  if (n >= 1 && n <= totalPages.value && n !== currentPage.value) {
    currentPage.value = n
    loadList()
  }
}
function onSizeChange() {
  currentPage.value = 1
  loadList()
}

async function loadList() {
  loading.value = true
  try {
    const res = await listVersion({
      platform: platform.value,
      status: activeFilter.value,
      page: currentPage.value,
      size: pageSize.value
    })
    const data = res.data || {}
    list.value = data.list || []
    total.value = data.total || 0
    scopeTotal.value = data.scopeTotal || 0
    latestVersion.value = data.latestVersion || null
    minVersion.value = data.minVersion || null
    statusCounts.value = data.statusCounts || {}
  } catch (e) {} finally {
    loading.value = false
  }
}

function onPlatformChange() {
  activeFilter.value = 'all'
  currentPage.value = 1
  loadList()
}

function resetForm() {
  form.platform = ''
  form.version = ''
  form.downloadUrl = ''
  form.changelog = ''
  uploadFileName.value = ''
  formRef.value?.clearValidate?.()
}

function openAdd() {
  resetForm()
  form.platform = platform.value
  addVisible.value = true
}

function triggerUpload() {
  fileInput.value?.click()
}

async function uploadFile(file, input) {
  if (!form.platform) {
    ElMessage.warning('请先选择平台')
    if (input) input.value = ''
    return
  }
  if (!validateVersion(form.version)) {
    ElMessage.warning('请先填写正确的版本号（如 1.2.0）')
    if (input) input.value = ''
    return
  }
  if (!extAllowed(file)) {
    ElMessage.warning(form.platform === 'win' ? 'Windows 平台仅支持上传 .zip 安装包' : 'Android 平台仅支持上传 .apk 安装包')
    if (input) input.value = ''
    return
  }
  const fd = new FormData()
  fd.append('platform', form.platform)
  fd.append('version', form.version)
  fd.append('file', file)
  uploading.value = true
  try {
    const res = await uploadVersionPackage(fd)
    form.downloadUrl = res.data // 形如 /downloads/win-1.0.2.exe
    uploadFileName.value = file.name
    ElMessage.success('安装包已上传，下载地址已自动填充')
  } catch (e) {} finally {
    uploading.value = false
    dragOver.value = false
    if (input) input.value = '' // 允许重复选择同一文件
  }
}

function onFileChange(e) {
  const input = e.target
  const file = input.files && input.files[0]
  if (file) uploadFile(file, input)
}

function onDrop(e) {
  dragOver.value = false
  if (uploading.value) return
  const file = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0]
  if (!file) return
  uploadFile(file)
}

function openEdit(row) {
  editId.value = row.id
  form.platform = row.platform
  form.version = row.version
  form.downloadUrl = row.downloadUrl || ''
  form.changelog = row.changelog || ''
  editVisible.value = true
}

async function save() {
  if (!validateVersion(form.version)) {
    return ElMessage.warning('版本号格式不合法（应为 1 / 1.2 / 1.2.3）')
  }
  const payload = {
    platform: form.platform,
    version: form.version,
    downloadUrl: form.downloadUrl || null,
    changelog: form.changelog || null
  }
  saving.value = true
  try {
    const res = await createVersion(payload)
    ElMessage.success(res.data || '新增成功')
    addVisible.value = false
    await loadList()
  } catch (e) {} finally {
    saving.value = false
  }
}

async function saveEdit() {
  const payload = {
    downloadUrl: form.downloadUrl || null,
    changelog: form.changelog || null
  }
  saving.value = true
  try {
    const res = await updateVersion(editId.value, payload)
    ElMessage.success(res.data || '保存成功')
    editVisible.value = false
    await loadList()
  } catch (e) {} finally {
    saving.value = false
  }
}

async function onSetMin(row) {
  try {
    await ElMessageBox.confirm(`确认将 ${row.version} 设为「最低支持版本」？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    const res = await setMinVersion(row.id)
    ElMessage.success(res.data || '已设为最低')
    await loadList()
  } catch (e) {}
}

async function onSetLatest(row) {
  try {
    await ElMessageBox.confirm(`确认将 ${row.version} 设为「最新版本」？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    const res = await setLatestVersion(row.id)
    ElMessage.success(res.data || '已设为最新')
    await loadList()
  } catch (e) {}
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除版本 ${row.version}？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    const res = await deleteVersion(row.id)
    ElMessage.success(res.data || '已删除')
    await loadList()
  } catch (e) {}
}

onMounted(loadList)
</script>

<style scoped>
.page-title { margin: 0 0 14px; font-size: 20px; color: #222; }

.vm-card { background: #fff; border: 1px solid #E5E7EB; border-radius: 8px; box-shadow: 0 1px 2px rgba(16,24,40,.05); overflow: hidden; }
.card-head { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px; border-bottom: 1px solid #E5E7EB; }
.card-head h2 { font-size: 16px; font-weight: 600; margin: 0; }
.head-actions { display: flex; align-items: center; gap: 12px; }
.btn { display: inline-flex; align-items: center; gap: 6px; height: 34px; padding: 0 14px; border-radius: 4px; font-size: 13px; cursor: pointer; border: 1px solid transparent; }
.btn-primary { background: #2E5CE6; color: #fff; font-weight: 500; }
.btn-primary:hover { background: #2449BF; }

.pkg-card { display: flex; align-items: center; gap: 12px; width: 100%; min-width: 0; box-sizing: border-box; padding: 12px 14px; border: 1px solid #E5E7EB; border-radius: 8px; background: #FAFBFC; overflow: hidden; }
.pkg-icon { display: flex; align-items: center; justify-content: center; width: 40px; height: 40px; border-radius: 8px; background: #EEF2FF; color: #2E5CE6; flex-shrink: 0; }
.pkg-info { flex: 1; min-width: 0; }
.pkg-name { font-size: 14px; font-weight: 600; color: #1F2329; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pkg-path { font-size: 12px; color: #98A2B3; font-family: Consolas, monospace; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-top: 2px; }
.pkg-ops { display: flex; align-items: center; gap: 4px; flex-shrink: 0; }
.pkg-op { padding: 4px 8px; border-radius: 4px; font-size: 13px; color: #2E5CE6; cursor: pointer; background: none; border: none; transition: background .12s; white-space: nowrap; }
.pkg-op:hover:not(:disabled) { background: #EEF2FF; }
.pkg-op:disabled { color: #C0C4CC; cursor: not-allowed; }
.pkg-op.danger { color: #D92D20; }
.pkg-op.danger:hover:not(:disabled) { background: #FEF3F2; }

:deep(.el-form-item__content) { min-width: 0; }

.pkg-empty { display: flex; flex-direction: column; align-items: center; gap: 4px; width: 100%; min-width: 0; box-sizing: border-box; padding: 22px 14px; border: 1px dashed #C6CBD4; border-radius: 8px; background: #FAFBFC; color: #4E5969; font-size: 14px; cursor: pointer; transition: all .15s; }
.pkg-empty:hover { border-color: #2E5CE6; color: #2E5CE6; background: #EEF2FF; }
.pkg-empty.uploading { cursor: not-allowed; color: #98A2B3; }
.pkg-empty svg { color: #98A2B3; }
.pkg-empty:hover svg { color: #2E5CE6; }
.pkg-empty.dragging { border-color: #2E5CE6; border-style: solid; background: #EEF2FF; color: #2E5CE6; }
.pkg-empty.dragging svg { color: #2E5CE6; }
.pkg-card.dragging { border-color: #2E5CE6; background: #EEF2FF; }
.pkg-hint { font-size: 12px; color: #98A2B3; }

.info-line { display: flex; align-items: center; gap: 8px; margin: 14px 20px 0; padding: 8px 12px; background: #EEF2FF; border-radius: 4px; color: #4E5969; font-size: 13px; }
.info-line svg { flex-shrink: 0; color: #2E5CE6; }

.status-strip { display: flex; flex-wrap: wrap; gap: 10px; padding: 14px 20px; border-bottom: 1px solid #E5E7EB; }
.chip { display: inline-flex; flex-direction: column; align-items: flex-start; gap: 2px; padding: 8px 14px; min-width: 96px; border: 1px solid #E5E7EB; border-radius: 4px; background: #fff; cursor: pointer; transition: all .15s; text-align: left; }
.chip:hover { border-color: #2E5CE6; }
.chip.active { border-color: #2E5CE6; background: #EEF2FF; }
.chip-label { display: flex; align-items: center; gap: 5px; font-size: 12px; color: #86909C; }
.chip-val { font-size: 15px; font-weight: 600; color: #1F2329; font-variant-numeric: tabular-nums; }
.dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.dot-latest { background: #067647; }
.dot-lowest { background: #B54708; }
.dot-available { background: #98A2B3; }
.dot-expired { background: #D92D20; }
.dot-unreleased { background: #2E5CE6; }

.table-wrap { padding: 6px 20px 20px; }
table { width: 100%; border-collapse: collapse; }
thead th { text-align: left; font-size: 12px; font-weight: 500; color: #86909C; background: #FAFBFC; padding: 10px 12px; border-bottom: 1px solid #E5E7EB; white-space: nowrap; }
tbody td { padding: 13px 12px; border-bottom: 1px solid #E5E7EB; color: #1F2329; vertical-align: middle; }
tbody tr:last-child td { border-bottom: none; }
tbody tr:hover { background: #F8F9FB; }
td.version { font-weight: 600; font-variant-numeric: tabular-nums; white-space: nowrap; }
td.desc { color: #4E5969; }
td.time { color: #4E5969; font-variant-numeric: tabular-nums; white-space: nowrap; }
.empty { text-align: center; color: #86909C; padding: 32px; }

.badge { display: inline-flex; align-items: center; gap: 5px; height: 22px; padding: 0 9px; border-radius: 11px; font-size: 12px; font-weight: 500; white-space: nowrap; margin-right: 4px; }
.badge-latest { background: #ECFDF3; color: #067647; }
.badge-lowest { background: #FFFAEB; color: #B54708; }
.badge-available { background: #F2F4F7; color: #475467; }
.badge-expired { background: #FEF3F2; color: #D92D20; }
.badge-unreleased { background: #EEF2FF; color: #2E5CE6; }

.dl-link { display: inline-flex; align-items: center; gap: 5px; color: #2E5CE6; text-decoration: none; font-size: 13px; }
.dl-link:hover { text-decoration: underline; }
.dl-muted { color: #86909C; }

.ops { display: flex; align-items: center; gap: 4px; white-space: nowrap; }
.op { padding: 5px 8px; border-radius: 4px; font-size: 13px; color: #2E5CE6; cursor: pointer; background: none; border: none; transition: background .12s; }
.op:hover { background: #EEF2FF; }
.op:disabled { color: #C0C4CC; cursor: not-allowed; }
.op:disabled:hover { background: none; }
.op.danger { color: #D92D20; }
.op.danger:hover:not(:disabled) { background: #FEF3F2; }
.op-divider { width: 1px; height: 14px; background: #D1D5DB; margin: 0 4px; }

.table-foot { display: flex; align-items: center; justify-content: space-between; padding: 12px 20px; border-top: 1px solid #E5E7EB; color: #86909C; font-size: 13px; }
.foot-right { display: flex; align-items: center; gap: 12px; }
.pager { display: flex; align-items: center; gap: 6px; }
.page-btn { min-width: 28px; height: 28px; display: inline-flex; align-items: center; justify-content: center; border: 1px solid #D1D5DB; border-radius: 4px; background: #fff; color: #4E5969; font-size: 13px; cursor: pointer; padding: 0 6px; }
.page-btn:hover:not(:disabled) { border-color: #2E5CE6; color: #2E5CE6; }
.page-btn.current { background: #2E5CE6; border-color: #2E5CE6; color: #fff; }
.page-btn:disabled { color: #C0C4CC; cursor: not-allowed; border-color: #E5E7EB; }
</style>
