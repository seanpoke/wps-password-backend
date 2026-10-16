import { createRouter, createWebHashHistory } from 'vue-router'
import Login from '@/views/Login.vue'
import UserLogin from '@/views/UserLogin.vue'
import UserDownload from '@/views/UserDownload.vue'
import MainLayout from '@/layouts/MainLayout.vue'
import Dashboard from '@/views/Dashboard.vue'
import Dept from '@/views/Dept.vue'
import User from '@/views/User.vue'
import Role from '@/views/Role.vue'
import Doc from '@/views/Doc.vue'
import LdapSync from '@/views/LdapSync.vue'
import Version from '@/views/Version.vue'
import PasswordAudit from '@/views/PasswordAudit.vue'
import { getToken } from '@/store/auth'
import { getUserToken } from '@/store/userAuth'

const routes = [
  // 根路径默认跳转用户登录页
  { path: '/', redirect: '/users/login' },

  // ---- 用户侧（客户端下载）----
  { path: '/users/login', name: 'user-login', component: UserLogin, meta: { public: true, title: '用户登录' } },
  { path: '/users/download', name: 'user-download', component: UserDownload, meta: { userAuth: true, title: '软件下载' } },

  // ---- 后台管理（统一 /backend/ 前缀）----
  { path: '/backend/login', name: 'admin-login', component: Login, meta: { public: true, title: '后台登录' } },
  {
    path: '/backend',
    component: MainLayout,
    redirect: '/backend/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: Dashboard, meta: { title: '仪表盘' } },
      { path: 'depts', name: 'depts', component: Dept, meta: { title: '部门管理' } },
      { path: 'users', name: 'backend-users', component: User, meta: { title: '用户管理' } },
      { path: 'roles', name: 'roles', component: Role, meta: { title: '角色管理' } },
      { path: 'docs', name: 'docs', component: Doc, meta: { title: '文档管理' } },
      { path: 'ldap-sync', name: 'ldap-sync', component: LdapSync, meta: { title: 'LDAP 同步' } },
      { path: 'versions', name: 'versions', component: Version, meta: { title: '版本管理' } },
      { path: 'audit', name: 'audit', component: PasswordAudit, meta: { title: '密码审计' } }
    ]
  },

  // 兼容旧后台地址：/dashboard、/login 等无前缀路径统一重定向到 /backend/
  // 注意：/users 精确匹配才重定向（旧“用户管理”），不与 /users/login、/users/download 冲突
  { path: '/login', redirect: '/backend/login' },
  { path: '/dashboard', redirect: '/backend/dashboard' },
  { path: '/depts', redirect: '/backend/depts' },
  { path: '/users', redirect: '/backend/users' },
  { path: '/roles', redirect: '/backend/roles' },
  { path: '/docs', redirect: '/backend/docs' },
  { path: '/ldap-sync', redirect: '/backend/ldap-sync' },
  { path: '/versions', redirect: '/backend/versions' },
  { path: '/audit', redirect: '/backend/audit' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  const adminToken = getToken()      // 后台管理员令牌
  const userToken = getUserToken()   // 用户侧令牌

  // 用户登录页：已登录用户直接进入下载页
  if (to.name === 'user-login') {
    if (userToken) return { name: 'user-download' }
    return true
  }
  // 后台登录页：已登录管理员直接进入仪表盘
  if (to.name === 'admin-login') {
    if (adminToken) return { name: 'dashboard' }
    return true
  }
  // 用户下载页：需要用户令牌
  if (to.meta.userAuth) {
    if (!userToken) return { name: 'user-login', query: { redirect: to.fullPath } }
    return true
  }
  // 后台管理页：需要管理员令牌
  if (!adminToken) {
    return { name: 'admin-login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  const base = '文档密码管理'
  document.title = to.meta && to.meta.title ? `${to.meta.title} · ${base}` : base
})

export default router
