import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api/a': {
        target: 'http://127.0.0.1:8080',
        rewrite: (path) => path.replace(/^\/api\/a/, ''),
      },
      '/api/b': {
        target: 'http://127.0.0.1:8081',
        rewrite: (path) => path.replace(/^\/api\/b/, ''),
      },
    },
  },
})
