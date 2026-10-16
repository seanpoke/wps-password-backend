<template>
  <div class="login-shell">
    <!-- 左侧品牌区 -->
    <aside class="brand-panel">
      <div class="brand-panel-inner">
        <a class="brand brand-light" href="javascript:void(0)">
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M12 2.5 4.5 5.4v5.9c0 4.6 3.2 7.9 7.5 9.2 4.3-1.3 7.5-4.6 7.5-9.2V5.4L12 2.5Z" fill="#ffffff"/>
            <path d="m8.8 12.1 2.3 2.3 4.2-4.4" stroke="#152036" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <span>文档密码管理</span>
        </a>
        <div class="brand-copy">
          <h1>为文档加密而生</h1>
          <p>文件在本地完成加密，密码统一管理，数据不经过云端，你的文档只属于你。</p>
        </div>
        <ul class="brand-features">
          <li>
            <span class="f-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" aria-hidden="true"><rect x="4.5" y="10" width="15" height="10.5" rx="2.2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/><path d="M12 14v3"/></svg>
            </span>
            本地加密，数据不上云
          </li>
          <li>
            <span class="f-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" aria-hidden="true"><rect x="2.5" y="4" width="13" height="9.5" rx="1.8"/><path d="M7 20h4"/><rect x="17.5" y="9" width="4" height="11" rx="1.4"/></svg>
            </span>
            Windows / Android 双端可用
          </li>
          <li>
            <span class="f-icon">
              <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2.8l2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 16.2l-5.2 2.8 1-5.9-4.3-4.1 5.9-.8L12 2.8Z"/></svg>
            </span>
            仅提供最新版本，安全始终在线
          </li>
        </ul>
        <p class="brand-foot">登录后可查看并下载最新版本</p>
      </div>
      <svg class="brand-watermark" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2 4 5v6c0 5 3.4 8.4 8 10 4.6-1.6 8-5 8-10V5l-8-3Z"/></svg>
    </aside>

    <!-- 右侧登录表单 -->
    <main class="form-panel">
      <div class="form-box">
        <div class="form-head">
          <h2>欢迎登录</h2>
          <p>登录后即可进入下载页面</p>
        </div>
        <form @submit.prevent="onSubmit" novalidate>
          <div class="field" :class="{ 'has-error': errors.user }">
            <label for="username">用户名</label>
            <div class="input-wrap">
              <svg class="lead-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="8.5" r="3.8"/><path d="M4.5 20c1.3-3.6 4.1-5.4 7.5-5.4s6.2 1.8 7.5 5.4"/></svg>
              <input id="username" v-model="form.account" type="text" placeholder="请输入用户名" autocomplete="username" @input="onInput" />
            </div>
          </div>
          <div class="field" :class="{ 'has-error': errors.pwd }">
            <label for="password">密码</label>
            <div class="input-wrap with-toggle">
              <svg class="lead-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" aria-hidden="true"><rect x="4.5" y="10" width="15" height="10.5" rx="2.2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/><path d="M12 14v3"/></svg>
              <input id="password" v-model="form.password" :type="showPwd ? 'text' : 'password'" placeholder="请输入密码" autocomplete="current-password" @input="onInput" @keyup.enter="onSubmit" />
              <button type="button" class="pwd-toggle" :class="{ 'is-on': showPwd }" :aria-label="showPwd ? '隐藏密码' : '显示密码'" @click="showPwd = !showPwd">
                <svg class="icon-eye" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2.5 12S6 5.8 12 5.8 21.5 12 21.5 12 18 18.2 12 18.2 2.5 12 2.5 12Z"/><circle cx="12" cy="12" r="3"/></svg>
                <svg class="icon-eye-off" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 3l18 18"/><path d="M10.6 6.2A9.6 9.6 0 0 1 12 6c6 0 9.5 6 9.5 6a17.4 17.4 0 0 1-2.6 3.4"/><path d="M6.4 8A17 17 0 0 0 2.5 12S6 18 12 18c1.2 0 2.3-.2 3.3-.6"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/></svg>
              </button>
            </div>
          </div>
          <p class="form-error" v-show="errorMsg" role="alert">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3.5 2.5 20h19L12 3.5Z"/><path d="M12 10v4.5"/><path d="M12 17.6h.01"/></svg>
            <span>{{ errorMsg }}</span>
          </p>
          <button type="submit" class="submit-btn" :class="{ 'is-loading': loading }" :disabled="loading">
            <span class="btn-spinner" aria-hidden="true"></span>
            <span class="btn-text">登 录</span>
          </button>
        </form>
      </div>
      <p class="form-foot">© 2026 文档密码管理</p>
    </main>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userLogin } from '@/api/auth'
import { setUserToken, setUserInfo } from '@/store/userAuth'

const router = useRouter()
const route = useRoute()
const form = reactive({ account: '', password: '' })
const showPwd = ref(false)
const loading = ref(false)
const errorMsg = ref('')
const errors = reactive({ user: false, pwd: false })

function onInput() {
  errors.user = false
  errors.pwd = false
  errorMsg.value = ''
}

async function onSubmit() {
  const u = form.account.trim()
  const p = form.password
  if (!u) {
    errorMsg.value = '请输入用户名'
    errors.user = true
    document.getElementById('username')?.focus()
    return
  }
  if (!p) {
    errorMsg.value = '请输入密码'
    errors.pwd = true
    document.getElementById('password')?.focus()
    return
  }
  errorMsg.value = ''
  loading.value = true
  try {
    const res = await userLogin(u, p)
    const d = res.data
    setUserToken(d.token)
    setUserInfo({ account: d.account, name: d.name, role: d.role })
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || '/users/download'
    router.push(redirect)
  } catch (e) {
    errorMsg.value = (e && e.message) || '登录失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-shell {
  --bg: #f4f6fa;
  --surface: #ffffff;
  --ink: #152036;
  --ink-2: #3c4763;
  --muted: #6b7590;
  --line: #e2e7f1;
  --primary: #1d4ed8;
  --primary-hover: #1a45c0;
  --danger: #b42318;
  --danger-bg: #fef3f2;
  --danger-line: #fecdca;
  --font: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "HarmonyOS Sans SC", "MiSans", "Microsoft YaHei", sans-serif;
  -webkit-text-size-adjust: 100%;
  min-height: 100vh;
  min-height: 100dvh;
  display: grid;
  grid-template-columns: 1.05fr .95fr;
  margin: 0;
  font-family: var(--font);
  font-size: 16px;
  line-height: 1.75;
  color: var(--ink);
  background: var(--bg);
}
.login-shell *, .login-shell *::before, .login-shell *::after { box-sizing: border-box; }
.login-shell svg, .login-shell img { display: block; }
.login-shell a { color: inherit; }

/* ---------- 左侧品牌区 ---------- */
.brand-panel { position: relative; overflow: hidden; background: var(--ink); color: #fff; padding: 52px 56px 44px; display: flex; flex-direction: column; }
.brand-panel-inner { position: relative; z-index: 1; display: flex; flex-direction: column; height: 100%; }
.brand { display: inline-flex; align-items: center; gap: 10px; text-decoration: none; font-weight: 700; font-size: 17px; letter-spacing: .02em; }
.brand svg { width: 26px; height: 26px; flex: none; }
.brand-light svg { color: #fff; }
.brand-copy { margin-top: auto; padding-top: 44px; }
.brand-copy h1 { margin: 0; font-size: clamp(25px, 2.8vw, 33px); line-height: 1.32; font-weight: 800; }
.brand-copy p { margin: 14px 0 0; color: #aab4cc; font-size: 15.5px; max-width: 26em; }
.brand-features { list-style: none; margin: 34px 0 0; padding: 0; display: flex; flex-direction: column; gap: 14px; }
.brand-features li { display: flex; align-items: center; gap: 13px; font-size: 15px; color: #d6dcec; }
.brand-features .f-icon { flex: none; width: 38px; height: 38px; border-radius: 10px; background: rgba(255, 255, 255, .08); display: flex; align-items: center; justify-content: center; }
.brand-features .f-icon svg { width: 18px; height: 18px; color: #8fb0ff; }
.brand-foot { margin-top: auto; padding-top: 42px; font-size: 13px; color: #7d89a6; }
.brand-watermark { position: absolute; right: -64px; bottom: -84px; width: 340px; height: 340px; color: #fff; opacity: .05; pointer-events: none; }

/* ---------- 右侧表单区 ---------- */
.form-panel { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48px 32px; background: var(--surface); }
.form-box { width: 100%; max-width: 400px; }
.form-head { margin-bottom: 26px; }
.form-head h2 { margin: 0; font-size: 26px; font-weight: 800; }
.form-head p { margin: 6px 0 0; font-size: 14.5px; color: var(--muted); }
.field { margin-bottom: 18px; }
.field label { display: block; margin-bottom: 7px; font-size: 14px; font-weight: 600; color: var(--ink-2); }
.input-wrap { position: relative; display: flex; align-items: center; }
.input-wrap .lead-icon { position: absolute; left: 14px; width: 18px; height: 18px; color: #98a2b8; pointer-events: none; }
.input-wrap input { width: 100%; height: 48px; padding: 0 14px 0 42px; border: 1px solid var(--line); border-radius: 10px; font: inherit; font-size: 15px; color: var(--ink); background: #fff; transition: border-color .15s, box-shadow .15s; }
.input-wrap.with-toggle input { padding-right: 46px; }
.input-wrap input::placeholder { color: #a6afc2; }
.input-wrap input:focus { outline: none; border-color: var(--primary); box-shadow: 0 0 0 3px rgba(29, 78, 216, .14); }
.field.has-error .input-wrap input { border-color: var(--danger); }
.pwd-toggle { position: absolute; right: 6px; display: flex; align-items: center; justify-content: center; width: 36px; height: 36px; border: 0; background: transparent; border-radius: 8px; color: #98a2b8; cursor: pointer; transition: color .15s, background .15s; }
.pwd-toggle:hover { color: var(--ink-2); background: #f2f4f9; }
.pwd-toggle svg { width: 18px; height: 18px; }
.pwd-toggle .icon-eye-off { display: none; }
.pwd-toggle.is-on .icon-eye { display: none; }
.pwd-toggle.is-on .icon-eye-off { display: block; }
.form-error { display: flex; align-items: center; gap: 8px; margin: 0 0 16px; padding: 9px 12px; background: var(--danger-bg); border: 1px solid var(--danger-line); border-radius: 9px; font-size: 13.5px; color: var(--danger); }
.form-error[hidden] { display: none; }
.form-error svg { width: 15px; height: 15px; flex: none; }
.submit-btn { display: flex; align-items: center; justify-content: center; gap: 9px; width: 100%; height: 50px; border: 0; border-radius: 10px; background: var(--primary); color: #fff; font: inherit; font-size: 16px; font-weight: 700; letter-spacing: .08em; cursor: pointer; box-shadow: 0 10px 24px -10px rgba(29, 78, 216, .55); transition: background .15s, transform .15s, box-shadow .15s; }
.submit-btn:hover { background: var(--primary-hover); transform: translateY(-1px); }
.submit-btn:active { transform: translateY(0); }
.submit-btn:disabled { opacity: .75; cursor: default; transform: none; }
.submit-btn:focus-visible, .pwd-toggle:focus-visible, .input-wrap input:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.btn-spinner { display: none; width: 17px; height: 17px; border: 2px solid rgba(255, 255, 255, .4); border-top-color: #fff; border-radius: 50%; animation: spin .7s linear infinite; }
.submit-btn.is-loading .btn-spinner { display: block; }
@keyframes spin { to { transform: rotate(360deg); } }
.form-foot { margin-top: 34px; font-size: 13px; color: #9aa3b8; }

/* ---------- 自适应 ---------- */
@media (max-width: 900px) {
  .login-shell { grid-template-columns: 1fr; }
  .brand-panel { padding: 32px 24px 28px; }
  .brand-copy { margin-top: 20px; padding-top: 0; }
  .brand-copy p { margin-top: 8px; font-size: 14px; }
  .brand-features, .brand-foot, .brand-watermark { display: none; }
  .form-panel { padding: 40px 24px 48px; }
  .form-box { max-width: 440px; }
}
@media (prefers-reduced-motion: reduce) { .login-shell * { transition: none !important; animation: none !important; } }
</style>
