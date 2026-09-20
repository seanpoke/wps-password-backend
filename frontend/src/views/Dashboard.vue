<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="6" v-for="c in cards" :key="c.title">
        <el-card shadow="hover" class="stat">
          <el-icon :size="40" :color="c.color"><component :is="c.icon" /></el-icon>
          <div class="meta">
            <div class="num">{{ c.value }}</div>
            <div class="label">{{ c.title }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="quick" header="快速操作">
      <el-button type="primary" :icon="OfficeBuilding" @click="go('/depts')">部门管理</el-button>
      <el-button type="success" :icon="User" @click="go('/users')">用户管理</el-button>
      <el-button type="info" :icon="DataLine" @click="go('/ldap')">LDAP 组织树</el-button>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { OfficeBuilding, User, Share, DataLine } from '@element-plus/icons-vue'
import { listDepts } from '@/api/dept'
import { listUsers } from '@/api/user'
import { listRoles } from '@/api/role'
import { getDeptRefs } from '@/api/visibleDept'

const router = useRouter()
const cards = ref([
  { title: '部门数', value: '-', icon: OfficeBuilding, color: '#2b8' },
  { title: '用户数', value: '-', icon: User, color: '#e6a23c' },
  { title: '角色数', value: '-', icon: Share, color: '#67c23a' },
  { title: '可见部门关联数', value: '-', icon: DataLine, color: '#409eff' }
])

function go(p) { router.push(p) }

async function load() {
  try {
    const [d, u, r, refs] = await Promise.all([listDepts(), listUsers(), listRoles(), getDeptRefs()])
    cards.value[0].value = (d.data || []).length
    cards.value[1].value = (u.data || []).length
    cards.value[2].value = (r.data || []).length
    cards.value[3].value = (refs.data || []).reduce((s, x) => s + (x.labels ? x.labels.length : 0), 0)
  } catch (e) {}
}

onMounted(load)
</script>

<style scoped>
.stat { display: flex; align-items: center; gap: 16px; }
.meta .num { font-size: 28px; font-weight: 700; color: #222; }
.meta .label { color: #888; font-size: 13px; }
.quick { margin-top: 16px; }
</style>
