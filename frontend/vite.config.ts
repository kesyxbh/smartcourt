import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // When the browser requests /api/xxx, Vite forwards it to the backend
      '/api': 'http://localhost:8080',
    },
  },
})
