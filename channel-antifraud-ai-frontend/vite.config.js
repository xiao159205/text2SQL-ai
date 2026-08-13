import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 后端接入时，将 mockApi 中的调用替换为 /api/chat，代理到此处的目标地址
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
