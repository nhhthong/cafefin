/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Dev-only convenience: `npm run dev` serves the SPA on Vite's own
      // port, but any fetch('/api/...') from the browser gets forwarded to
      // cafefin-api so the frontend can call the real backend during local
      // development without CORS. In production the SPA's dist/ is copied
      // into cafefin-api's static resources instead, so both are already
      // same-origin and no proxy is involved (Task 0.9).
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    // jsdom: fake DOM in Node, so component tests can render without a
    // real browser (Task 0.8 requirement).
    environment: 'jsdom',
  },
})
