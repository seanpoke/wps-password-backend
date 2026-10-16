<template>
  <div class="dlpage">
    <header class="site-header">
      <div class="wrap header-inner">
        <a class="brand" href="javascript:void(0)" @click="scrollTo('top')">
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M12 2.5 4.5 5.4v5.9c0 4.6 3.2 7.9 7.5 9.2 4.3-1.3 7.5-4.6 7.5-9.2V5.4L12 2.5Z" fill="currentColor"/>
            <path d="m8.8 12.1 2.3 2.3 4.2-4.4" stroke="#ffffff" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <span>文档密码管理</span>
        </a>
        <nav class="site-nav">
          <a href="javascript:void(0)" @click="scrollTo('whatsnew')">更新说明</a>
          <a href="javascript:void(0)" @click="scrollTo('faq')">常见问题</a>
          <a class="logout-link" href="javascript:void(0)" @click="onLogout" title="退出登录">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10 4.5H7.5A2.5 2.5 0 0 0 5 7v10a2.5 2.5 0 0 0 2.5 2.5H10"/><path d="M16 12h5"/><path d="m18.5 9.5 2.5 2.5-2.5 2.5"/></svg>
            退出
          </a>
        </nav>
      </div>
    </header>

    <main id="top">
      <!-- 主视觉 + 下载卡 -->
      <section class="hero">
        <div class="wrap hero-main">
          <div class="hero-copy">
            <h1>文档密码管理</h1>
            <p class="tagline">为文档加密而生：文件在本地完成加密，密码统一管理，数据不经过云端，你的文档只属于你。</p>
          </div>

          <aside class="dl-card" aria-label="软件下载" v-loading="loading">
            <div class="tabs" role="tablist" aria-label="选择平台">
              <button class="tab" type="button" role="tab" :class="{ 'is-active': platform === 'windows' }" :aria-selected="platform === 'windows' ? 'true' : 'false'" @click="platform = 'windows'">Windows</button>
              <button class="tab" type="button" role="tab" :class="{ 'is-active': platform === 'android' }" :aria-selected="platform === 'android' ? 'true' : 'false'" @click="platform = 'android'">Android</button>
            </div>
            <div class="dl-body">
              <div class="dl-version-row">
                <span class="badge-latest">
                  <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2.8l2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 16.2l-5.2 2.8 1-5.9-4.3-4.1 5.9-.8L12 2.8Z"/></svg>
                  最新版本
                </span>
                <span class="dl-version">{{ cur.version }}</span>
              </div>
              <a class="dl-button" id="dlButton" :href="cur.downloadUrl || 'javascript:void(0)'" @click="onDownload">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3.5v11"/><path d="m7.5 10 4.5 4.5L16.5 10"/><path d="M4.5 19.5v.5a1.5 1.5 0 0 0 1.5 1.5h12a1.5 1.5 0 0 0 1.5-1.5v-.5"/></svg>
                <span>下载 {{ cur.label }} 版</span>
              </a>
              <dl class="dl-meta">
                <div><dt>文件大小</dt><dd>{{ cur.size }}</dd></div>
                <div><dt>更新时间</dt><dd>{{ cur.date }}</dd></div>
                <div><dt>系统要求</dt><dd>{{ cur.reqShort }}</dd></div>
              </dl>
              <p class="dl-note">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="8.5"/><path d="M12 11v5"/><path d="M12 7.8h.01"/></svg>
                <span>本页面仅提供最新版本下载，历史版本暂不支持。</span>
              </p>
            </div>
          </aside>

          <ul class="chips">
            <li>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" aria-hidden="true"><rect x="4.5" y="10" width="15" height="10.5" rx="2.2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/><path d="M12 14v3"/></svg>
              本地加密，数据不上云
            </li>
            <li>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" aria-hidden="true"><rect x="2.5" y="4" width="13" height="9.5" rx="1.8"/><path d="M7 20h4"/><rect x="17.5" y="9" width="4" height="11" rx="1.4"/></svg>
              Windows / Android 双端可用
            </li>
            <li>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M13 2.5 4.8 13.2h6.2L9.8 21.5 19.2 9.9h-6.4L13 2.5Z"/></svg>
              界面简洁，开箱即用
            </li>
          </ul>
        </div>
      </section>

      <!-- 更新说明 -->
      <section class="section" id="whatsnew">
        <div class="wrap">
          <div class="section-head">
            <h2>更新说明</h2>
            <p class="section-sub">{{ cur.label }} · {{ cur.version }} · {{ cur.date }} 发布</p>
          </div>
          <div class="card">
            <ul class="notes-list">
              <li v-for="(n, i) in cur.notes" :key="i">{{ n }}</li>
            </ul>
          </div>
        </div>
      </section>

      <!-- 系统要求与安装步骤 -->
      <section class="section" id="install">
        <div class="wrap">
          <div class="section-head">
            <h2>安装与使用</h2>
            <p class="section-sub">当前平台：<span>{{ cur.label }}</span></p>
          </div>
          <div class="two-col">
            <div class="card">
              <h3 class="card-title">系统要求</h3>
              <ul class="req-list">
                <li v-for="(r, i) in cur.requirements" :key="i">{{ r }}</li>
              </ul>
            </div>
            <div class="card">
              <h3 class="card-title">操作步骤</h3>
              <ol class="step-list">
                <li v-for="(s, i) in cur.steps" :key="i">{{ s }}</li>
              </ol>
            </div>
          </div>
        </div>
      </section>

      <!-- 常见问题 -->
      <section class="section" id="faq">
        <div class="wrap">
          <div class="section-head"><h2>常见问题</h2></div>
          <div class="card faq-card">
            <details class="faq-item" open>
              <summary>
                为什么只提供最新版本？
                <svg class="chev" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m6.5 9.5 5.5 5.5 5.5-5.5"/></svg>
              </summary>
              <p>为了保障安全与兼容性，本页面仅提供最新版本。历史版本中已修复的漏洞可能依然存在，继续下载和使用旧版本存在安全风险，因此不再对外提供。</p>
            </details>
            <details class="faq-item">
              <summary>
                我需要旧版本，怎么办？
                <svg class="chev" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m6.5 9.5 5.5 5.5 5.5-5.5"/></svg>
              </summary>
              <p>暂不支持历史版本下载。如确有特殊需要，请联系软件管理员，由管理员评估后单独提供。</p>
            </details>
            <details class="faq-item">
              <summary>
                下载后无法安装怎么办？
                <svg class="chev" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m6.5 9.5 5.5 5.5 5.5-5.5"/></svg>
              </summary>
              <p>Windows 用户请确认系统为 64 位并满足上方系统要求；Android 用户如提示「禁止安装未知来源应用」，请按系统引导允许后重试。仍有问题可联系软件管理员。</p>
            </details>
          </div>
        </div>
      </section>
    </main>

    <footer class="site-footer">
      <div class="wrap footer-inner">
        <p>© 2026 文档密码管理</p>
        <p>本页面仅提供最新版本下载</p>
      </div>
    </footer>

    <div class="toast" :class="{ show: toast.show }" role="status" aria-live="polite">{{ toast.text }}</div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getLatestVersion } from '@/api/version'
import { logout } from '@/api/auth'
import { getUserToken, clearUserAuth } from '@/store/userAuth'

const router = useRouter()
const platform = ref('windows')
const loading = ref(false)
const latest = ref({ win: null, android: null })
const toast = reactive({ show: false, text: '' })
let toastTimer = null

// 原型 tab key -> 后端 platform 值
const API_PLATFORM = { windows: 'win', android: 'android' }

// 与版本数据无关的静态文案/系统要求（原型内嵌内容）
const STATIC = {
  windows: {
    label: 'Windows',
    size: '86.4 MB',
    reqShort: 'Windows 10 及以上（64 位）',
    requirements: ['Windows 10 及以上（64 位）', '4 GB 及以上内存', '约 200 MB 可用磁盘空间'],
    steps: ['点击上方「下载 Windows 版」按钮，获取软件压缩包', '将压缩包解压到本机任意文件夹', '在解压后的文件夹中找到 PasswordManager.exe，双击运行即可'],
    fallbackNotes: ['全新视觉设计，主界面更清爽，常用操作一步直达', '加密引擎升级，大文档加解密速度显著提升', '新增密码生成器，可一键生成高强度随机密码', '修复了若干已知问题，整体稳定性提升']
  },
  android: {
    label: 'Android',
    size: '42.8 MB',
    reqShort: 'Android 8.0 及以上',
    requirements: ['Android 8.0 及以上', '约 150 MB 可用存储空间', '支持指纹识别的设备体验更佳'],
    steps: ['点击上方按钮下载 APK 安装包', '如系统提示「禁止安装未知来源应用」，按引导允许后重试', '打开应用并设置主密码，即可开始使用'],
    fallbackNotes: ['与 Windows 端同步发布，核心加密能力一致', '支持指纹解锁，验证后快速进入', '优化切后台自动锁定逻辑，忘记锁屏也不怕', '修复了若干已知问题']
  }
}

const cur = computed(() => {
  const key = platform.value
  const s = STATIC[key]
  const v = latest.value[API_PLATFORM[key]]
  const changelog = v && v.changelog ? v.changelog : ''
  const notes = changelog.split(/\r?\n/).map((x) => x.trim()).filter(Boolean)
  return {
    label: s.label,
    version: v && v.version ? 'v' + v.version : '—',
    size: s.size,
    date: v && v.releaseTime ? v.releaseTime.slice(0, 10) : '—',
    reqShort: s.reqShort,
    downloadUrl: v ? v.downloadUrl : '',
    notes: notes.length ? notes : s.fallbackNotes,
    requirements: s.requirements,
    steps: s.steps
  }
})

function showToast(text) {
  toast.text = text
  toast.show = true
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toast.show = false }, 2600)
}

function onDownload(e) {
  if (!cur.value.downloadUrl) {
    e.preventDefault()
    showToast('当前平台暂无可用安装包，请联系管理员')
  }
}

function scrollTo(id) {
  const el = document.getElementById(id)
  if (el) el.scrollIntoView({ behavior: 'smooth' })
}

async function onLogout() {
  try { await logout() } catch (e) {}
  clearUserAuth()
  ElMessage.success('已退出登录')
  router.replace({ name: 'user-login' })
}

onMounted(async () => {
  if (!getUserToken()) {
    router.replace({ name: 'user-login' })
    return
  }
  loading.value = true
  try {
    const [win, android] = await Promise.all([
      getLatestVersion('win').then((r) => r.data).catch(() => null),
      getLatestVersion('android').then((r) => r.data).catch(() => null)
    ])
    latest.value = { win, android }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.dlpage {
  --bg: #f4f6fa;
  --surface: #ffffff;
  --ink: #152036;
  --ink-2: #3c4763;
  --muted: #6b7590;
  --line: #e2e7f1;
  --primary: #1d4ed8;
  --primary-hover: #1a45c0;
  --primary-soft: #e9eeff;
  --primary-ink: #16379e;
  --ok: #0c9268;
  --ok-soft: #e4f5ee;
  --shadow-card: 0 1px 2px rgba(21, 32, 54, .04), 0 16px 40px -16px rgba(21, 32, 54, .14);
  --font: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "HarmonyOS Sans SC", "MiSans", "Microsoft YaHei", sans-serif;
  margin: 0;
  font-family: var(--font);
  font-size: 16px;
  line-height: 1.75;
  color: var(--ink);
  background: var(--bg);
  min-height: 100vh;
}
.dlpage *, .dlpage *::before, .dlpage *::after { box-sizing: border-box; }
.dlpage :is(img, svg) { display: block; }
.dlpage :is(a) { color: inherit; }
.wrap { max-width: 1060px; margin: 0 auto; padding: 0 24px; }

.site-header { position: sticky; top: 0; z-index: 20; border-bottom: 1px solid var(--line); background: rgba(255, 255, 255, .86); backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px); }
.header-inner { display: flex; align-items: center; justify-content: space-between; height: 64px; }
.brand { display: inline-flex; align-items: center; gap: 10px; text-decoration: none; font-weight: 700; font-size: 17px; letter-spacing: .02em; }
.brand svg { width: 26px; height: 26px; color: var(--primary); flex: none; }
.site-nav { display: flex; gap: 22px; }
.site-nav a { font-size: 14.5px; color: var(--muted); text-decoration: none; transition: color .15s; cursor: pointer; }
.site-nav a:hover { color: var(--ink); }
.logout-link { display: inline-flex; align-items: center; gap: 5px; }
.logout-link svg { width: 14px; height: 14px; }

.hero { padding: 64px 0 8px; }
.hero-main { display: grid; grid-template-columns: 1.05fr .95fr; grid-template-areas: "copy card" "chips card"; column-gap: 56px; row-gap: 28px; align-items: start; }
.hero-copy { grid-area: copy; }
.hero-copy h1 { margin: 0; font-size: clamp(30px, 4.6vw, 44px); line-height: 1.22; font-weight: 800; letter-spacing: .01em; }
.tagline { margin: 18px 0 0; font-size: clamp(15px, 1.6vw, 17.5px); color: var(--ink-2); max-width: 32em; }
.chips { grid-area: chips; list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: 10px; }
.chips li { display: inline-flex; align-items: center; gap: 8px; padding: 8px 14px; background: var(--surface); border: 1px solid var(--line); border-radius: 999px; font-size: 14px; color: var(--ink-2); }
.chips svg { width: 16px; height: 16px; color: var(--primary); flex: none; }

.dl-card { grid-area: card; background: var(--surface); border: 1px solid var(--line); border-radius: 18px; box-shadow: var(--shadow-card); padding: 22px 22px 20px; }
.tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; padding: 4px; background: #edf0f6; border-radius: 12px; }
.tab { appearance: none; border: 0; background: transparent; font: inherit; font-size: 15px; font-weight: 600; color: var(--muted); padding: 9px 0; border-radius: 9px; cursor: pointer; transition: background .15s, color .15s, box-shadow .15s; }
.tab:hover { color: var(--ink-2); }
.tab.is-active { background: #fff; color: var(--ink); box-shadow: 0 1px 3px rgba(21, 32, 54, .14); }
.dl-body { padding-top: 20px; }
.dl-version-row { display: flex; align-items: center; gap: 12px; }
.badge-latest { display: inline-flex; align-items: center; gap: 5px; background: var(--ok-soft); color: var(--ok); font-size: 12.5px; font-weight: 700; padding: 4px 10px; border-radius: 999px; letter-spacing: .02em; }
.badge-latest svg { width: 13px; height: 13px; }
.dl-version { margin-left: auto; font-size: 22px; font-weight: 800; font-variant-numeric: tabular-nums; letter-spacing: .01em; }
.dlpage .dl-button { display: flex; align-items: center; justify-content: center; gap: 10px; width: 100%; height: 54px; margin-top: 18px; background: var(--primary); color: #fff; font-size: 16.5px; font-weight: 700; text-decoration: none; border-radius: 12px; box-shadow: 0 10px 24px -10px rgba(29, 78, 216, .55); transition: background .15s, transform .15s, box-shadow .15s; }
.dl-button svg { width: 19px; height: 19px; flex: none; }
.dl-button:hover { background: var(--primary-hover); transform: translateY(-1px); box-shadow: 0 14px 28px -10px rgba(29, 78, 216, .6); }
.dl-button:active { transform: translateY(0); }
.dl-meta { margin: 18px 0 0; border-top: 1px solid var(--line); }
.dl-meta > div { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; padding: 10px 2px; border-bottom: 1px solid var(--line); font-size: 14.5px; }
.dl-meta > div:last-child { border-bottom: 0; padding-bottom: 0; }
.dl-meta dt { color: var(--muted); flex: none; }
.dl-meta dd { margin: 0; font-weight: 600; text-align: right; font-variant-numeric: tabular-nums; }
.dl-note { display: flex; gap: 9px; align-items: flex-start; margin: 18px 0 0; padding: 11px 13px; background: var(--primary-soft); border-radius: 10px; font-size: 13.5px; line-height: 1.6; color: var(--primary-ink); }
.dl-note svg { width: 16px; height: 16px; flex: none; margin-top: 4px; }

.section { padding: 52px 0 4px; }
.section-head { margin-bottom: 18px; }
.section-head h2 { margin: 0; font-size: 23px; font-weight: 800; letter-spacing: .01em; }
.section-sub { margin: 4px 0 0; font-size: 14px; color: var(--muted); }
.card { background: var(--surface); border: 1px solid var(--line); border-radius: 16px; padding: 24px 26px; box-shadow: var(--shadow-card); }
.two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
.card-title { margin: 0 0 8px; font-size: 16.5px; font-weight: 700; }

.notes-list, .req-list { list-style: none; margin: 0; padding: 0; }
.notes-list li { display: flex; gap: 12px; padding: 11px 0; font-size: 15px; color: var(--ink-2); }
.notes-list li + li { border-top: 1px solid var(--line); }
.notes-list li::before { content: ""; flex: none; width: 7px; height: 7px; margin-top: 9px; border-radius: 50%; background: var(--primary); }
.req-list li { display: flex; gap: 10px; align-items: flex-start; padding: 9px 0; font-size: 15px; color: var(--ink-2); }
.req-list li::before { content: ""; flex: none; width: 16px; height: 16px; margin-top: 5px; border-radius: 50%; background-color: var(--ok-soft); background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%230c9268' stroke-width='3.4' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m5 13 4 4 10-10'/%3E%3C/svg%3E"); background-size: 10px; background-position: center; background-repeat: no-repeat; }

.step-list { list-style: none; margin: 0; padding: 0; counter-reset: step; }
.step-list li { position: relative; padding: 8px 0 8px 40px; font-size: 15px; color: var(--ink-2); counter-increment: step; }
.step-list li::before { content: counter(step); position: absolute; left: 0; top: 8px; width: 26px; height: 26px; border-radius: 50%; background: var(--primary-soft); color: var(--primary-ink); font-size: 13.5px; font-weight: 700; display: flex; align-items: center; justify-content: center; }

.faq-card { padding: 6px 26px; }
.faq-item { border-bottom: 1px solid var(--line); }
.faq-item:last-child { border-bottom: 0; }
.faq-item summary { display: flex; align-items: center; justify-content: space-between; gap: 16px; cursor: pointer; list-style: none; padding: 16px 0; font-weight: 700; font-size: 15.5px; }
.faq-item summary::-webkit-details-marker { display: none; }
.faq-item summary .chev { flex: none; width: 16px; height: 16px; color: var(--muted); transition: transform .2s; }
.faq-item[open] summary .chev { transform: rotate(180deg); }
.faq-item p { margin: 0 0 16px; font-size: 14.5px; color: var(--ink-2); max-width: 60em; }

.site-footer { margin-top: 64px; border-top: 1px solid var(--line); padding: 26px 0 34px; }
.footer-inner { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; font-size: 13.5px; color: var(--muted); }
.toast { position: fixed; left: 50%; bottom: 28px; transform: translate(-50%, 12px); background: #152036; color: #fff; font-size: 13.5px; line-height: 1.5; padding: 10px 18px; border-radius: 999px; box-shadow: 0 12px 30px -8px rgba(21, 32, 54, .45); opacity: 0; pointer-events: none; transition: opacity .25s, transform .25s; z-index: 50; max-width: calc(100vw - 48px); text-align: center; }
.toast.show { opacity: 1; transform: translate(-50%, 0); }

@media (max-width: 900px) {
  .hero { padding-top: 44px; }
  .hero-main { grid-template-columns: 1fr; grid-template-areas: "copy" "card" "chips"; row-gap: 22px; }
  .dl-card { padding: 18px; }
  .two-col { grid-template-columns: 1fr; }
  .section { padding-top: 40px; }
}
@media (max-width: 520px) {
  .wrap { padding: 0 18px; }
  .card { padding: 20px 18px; }
  .faq-card { padding: 4px 18px; }
  .dl-meta > div { font-size: 14px; }
  .site-nav { gap: 16px; }
  .site-nav a[data-hide-sm] { display: none; }
  .chips li { font-size: 13px; }
}
@media (prefers-reduced-motion: reduce) { .dlpage * { transition: none !important; } }
</style>
