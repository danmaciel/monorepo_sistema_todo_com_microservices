import { readFileSync } from 'node:fs'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Secure refresh cookies require HTTPS when running the local Vite server.
// Production builds must not need or bundle private TLS certificate files.
export default defineConfig(({ command }) => {
  const certPath = process.env.DEV_HTTPS_CERT
  const keyPath = process.env.DEV_HTTPS_KEY
  const sessionMode = process.env.VITE_SESSION_AUTH === 'true'
  if (command === 'serve' && sessionMode && (!certPath || !keyPath)) {
    throw new Error('VITE_SESSION_AUTH=true requires DEV_HTTPS_CERT and DEV_HTTPS_KEY for local Vite HTTPS')
  }

  return {
    plugins: [react()],
    server: {
      port: 3000,
      host: true,
      https: command === 'serve' && certPath && keyPath ? {
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
  }
})
