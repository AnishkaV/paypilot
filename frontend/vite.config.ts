import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// `npm run build` writes straight into the gateway, which serves the console at http://localhost:8080.
// `npm run dev` serves on http://localhost:5173 and proxies /api to the running gateway.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    outDir: '../api-gateway/src/main/resources/static',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1200,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
