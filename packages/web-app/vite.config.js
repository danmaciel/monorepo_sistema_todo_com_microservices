import { readFileSync } from 'node:fs'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Secure refresh cookies require HTTPS. Provide locally trusted mkcert files,
// never commit private keys or issue insecure cookies for development.
const certPath = process.env.VITE_DEV_HTTPS_CERT
const keyPath = process.env.VITE_DEV_HTTPS_KEY
const sessionMode = process.env.VITE_SESSION_AUTH === 'true'
if (sessionMode && (!certPath || !keyPath)) {
  throw new Error('VITE_SESSION_AUTH=true requires VITE_DEV_HTTPS_CERT and VITE_DEV_HTTPS_KEY')
}

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    host: true,
    https: certPath && keyPath ? {
      cert: readFileSync(certPath),
      key: readFileSync(keyPath),
    } : undefined,
    proxy: {
      '/api': {
        target: process.env.VITE_API_URL || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})