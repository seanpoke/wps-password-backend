import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 构建产物输出到 Spring Boot 的 webroot 目录（与 verify.html 同目录）。
// 使用哈希路由（createWebHashHistory），免去服务端 SPA fallback 配置。
export default defineConfig({
  plugins: [vue()],
  base: './',
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/account': 'http://localhost:8081',
      '/admin': 'http://localhost:8081',
      '/doc': 'http://localhost:8081'
    }
  },
  build: {
    outDir: '../webroot',
    emptyOutDir: false
  }
})
