import { createRouter, createWebHashHistory } from 'vue-router'
import Login from '@/views/Login.vue'
import MainLayout from '@/layouts/MainLayout.vue'
import Dashboard from '@/views/Dashboard.vue'
import Dept from '@/views/Dept.vue'
import User from '@/views/User.vue'
import Role from '@/views/Role.vue'
import Doc from '@/views/Doc.vue'
import { getToken } from '@/store/auth'


const routes = [
  { path: '/login', name: 'login', component: Login, meta: { public: true } },
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: Dashboard, meta: { title: '仪表盘' } },
      { path: 'depts', name: 'depts', component: Dept, meta: { title: '部门管理' } },
      { path: 'users', name: 'users', component: User, meta: { title: '用户管理' } },
      { path: 'roles', name: 'roles', component: Role, meta: { title: '角色管理' } },
      { path: 'docs', name: 'docs', component: Doc, meta: { title: '文档管理' } }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  const token = getToken()
  if (!to.meta.public && !token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && token) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
