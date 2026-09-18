<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="brand">
        <el-icon :size="34" color="#2b8"><Lock /></el-icon>
        <h2>文档密码安全管理</h2>
        <p class="sub">后台管理系统</p>
      </div>
      <el-form :model="form" :rules="rules" ref="formRef" @submit.prevent="onSubmit" label-position="top">
        <el-form-item label="账号" prop="account">
          <el-input v-model="form.account" placeholder="如 admin_local" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" :prefix-icon="Key" @keyup.enter="onSubmit" />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="submit" @click="onSubmit">登 录</el-button>
      </el-form>
      <p class="tip">提示：管理员账号为 admin_local；LDAP 内部用户不支持后台管理。</p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { User, Key, Lock } from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'
import { setToken, setUser } from '@/store/auth'

const router = useRouter()
const route = useRoute()
const formRef = ref()
const loading = ref(false)
const form = reactive({ account: 'admin_local', password: '123456' })
const rules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const res = await login(form.account, form.password)
    const d = res.data
    setToken(d.token)
    setUser({ account: d.account, name: d.name, role: d.role, source: d.source })
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || '/dashboard'
    router.push(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2a44 0%, #2b8 100%);
}
.login-card {
  width: 360px;
  border-radius: 12px;
  padding: 8px 6px;
}
.brand { text-align: center; margin-bottom: 10px; }
.brand h2 { margin: 8px 0 0; font-size: 20px; color: #222; }
.brand .sub { margin: 0; color: #999; font-size: 13px; }
.submit { width: 100%; margin-top: 6px; }
.tip { font-size: 12px; color: #aaa; text-align: center; margin: 10px 0 0; }
</style>
