<template>
  <div class="doc-page">
    <h2 class="page-title">文档管理</h2>

    <el-card shadow="never" class="panel">
      <!-- 筛选栏：仅点击「查询」按钮才请求后端 -->
      <div class="filter-bar">
        <span class="f-label">关键词</span>
        <el-input
          v-model="query.keyword"
          placeholder="请输入 uid / 文档名称 / 所属账号"
          class="f-input"
          clearable
          @keyup.enter="onSearch"
        />
        <div class="f-actions">
          <el-button :icon="Refresh" :loading="loading" @click="onReset">重置</el-button>
          <el-button type="primary" :icon="Search" :loading="loading" @click="onSearch">查询</el-button>
        </div>
      </div>

      <el-table :data="rows" v-loading="loading" class="tbl" row-key="id">
        <el-table-column prop="uid" label="uid" min-width="240" show-overflow-tooltip />
        <el-table-column prop="fileName" label="文档名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fileName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="account" label="所属账号" min-width="120" />
        <el-table-column label="创建时间" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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

    <!-- 文档详情 -->
    <el-dialog v-model="detailDialog" title="文档详情" width="680px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="uid" :span="2">{{ detail.uid }}</el-descriptions-item>
          <el-descriptions-item label="文档名称">{{ detail.fileName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="所属账号">{{ detail.account }}</el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">{{ detail.createTime }}</el-descriptions-item>
        </el-descriptions>

        <div class="auth-title">文档授权信息</div>
        <template v-if="detail.auths && detail.auths.length">
          <template v-if="deptAuths.length">
            <div class="auth-group">部门（{{ deptAuths.length }}）</div>
            <div v-for="(a, i) in deptAuths" :key="'d' + i" class="auth-item">• {{ a.name }}</div>
          </template>
          <template v-if="userAuths.length">
            <div class="auth-group">用户（{{ userAuths.length }}）</div>
            <div v-for="(a, i) in userAuths" :key="'u' + i" class="auth-item">• {{ a.name }}<span v-if="a.account">（{{ a.account }}）</span></div>
          </template>
        </template>
        <el-empty v-else description="暂无授权" :image-size="60" />
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageDocs, getDocDetail, deleteDoc } from '@/api/doc'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

// 查询条件（仅点击「查询」时才提交给后端）
const query = ref({ keyword: '' })

async function fetchData() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (query.value.keyword && query.value.keyword.trim()) params.keyword = query.value.keyword.trim()
    const res = await pageDocs(params)
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
  query.value = { keyword: '' }
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

/* ---------- 详情 ---------- */
const detailDialog = ref(false)
const detail = ref(null)

const deptAuths = computed(() => (detail.value?.auths || []).filter(a => a.type === 0))
const userAuths = computed(() => (detail.value?.auths || []).filter(a => a.type === 1))

async function openDetail(row) {
  const res = await getDocDetail(row.id)
  detail.value = res.data
  detailDialog.value = true
}

/* ---------- 删除 ---------- */
async function remove(row) {
  await ElMessageBox.confirm(
    '是否确认删除该文档？<br/><span style="color:#f56c6c;font-size:12px">删除后其全部文档授权关系将被一并清除，且不可恢复。</span>',
    '删除文档【' + (row.fileName || row.uid) + '】',
    { dangerouslyUseHTMLString: true, type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
  await deleteDoc(row.id)
  ElMessage.success('删除成功')
  // 若当前页删空则回退一页
  if (rows.value.length === 1 && page.value > 1) page.value -= 1
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.doc-page { padding: 4px; }
.page-title { margin: 0 0 14px; font-size: 20px; color: #222; }
.panel { border-radius: 8px; }
.filter-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 16px; }
.f-label { color: #555; font-size: 14px; }
.f-input { width: 280px; }
.f-actions { margin-left: auto; display: flex; gap: 0; }
.tbl { width: 100%; }
.pg-bar { display: flex; align-items: center; justify-content: space-between; margin-top: 14px; }
.pg-total { color: #888; font-size: 13px; }
.auth-title { font-size: 14px; font-weight: 600; color: #303133; margin: 16px 0 8px; }
.auth-group { font-size: 13px; font-weight: 600; color: #909399; margin: 8px 0 4px; }
.auth-item { font-size: 13px; color: #606266; line-height: 1.9; }
</style>
