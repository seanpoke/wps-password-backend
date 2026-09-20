<template>
  <el-container class="layout">
    <el-aside width="210px" class="aside">
      <div class="logo">
        <el-icon :size="20" color="#2b8"><Lock /></el-icon>
        <span>密码管理后台</span>
      </div>
      <el-menu :default-active="activeMenu" router background-color="#1f2a44" text-color="#cdd" active-text-color="#2b8">
        <el-menu-item index="/dashboard"><el-icon><DataLine /></el-icon><span>仪表盘</span></el-menu-item>
        <el-menu-item index="/depts"><el-icon><OfficeBuilding /></el-icon><span>部门管理</span></el-menu-item>
        <el-menu-item index="/users"><el-icon><User /></el-icon><span>用户管理</span></el-menu-item>
        <el-menu-item v-if="isAdmin" index="/roles"><el-icon><Key /></el-icon><span>角色管理</span></el-menu-item>
        <el-menu-item v-if="isAdmin" index="/docs"><el-icon><Document /></el-icon><span>文档管理</span></el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="title">{{ currentTitle }}</div>
        <el-dropdown @command="onCommand">
          <span class="user">
            <el-icon><Avatar /></el-icon>
            <span>{{ user ? user.name + '（' + (user.account) + '）' : '' }}</span>
            <el-tag v-if="user && user.source" size="small" type="info" style="margin-left:6px">{{ user.source }}</el-tag>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout"><el-icon><SwitchButton /></el-icon>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Key } from '@element-plus/icons-vue'
import { getUser, clearAuth } from '@/store/auth'
import { logout } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const user = getUser()
const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta.title || '')
const isAdmin = computed(() => user && (user.role === 'admin' || (user.roles && user.roles.includes('admin'))))

async function onCommand(cmd) {
  if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
    } catch (e) {
      return
    }
    try { await logout() } catch (e) {}
    clearAuth()
    ElMessage.success('已退出')
    router.push({ name: 'login' })
  }
}
</script>

<style scoped>
.layout { height: 100vh; }
.aside { background: #1f2a44; }
.logo {
  height: 56px; display: flex; align-items: center; gap: 8px;
  color: #fff; font-weight: 600; padding: 0 16px; font-size: 15px;
  border-bottom: 1px solid #ffffff22;
}
.header {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-bottom: 1px solid #eee;
}
.header .title { font-size: 16px; font-weight: 600; color: #333; }
.user { display: flex; align-items: center; gap: 6px; cursor: pointer; color: #555; outline: none; }
.main { background: #f5f7fa; padding: 16px; }
</style>
