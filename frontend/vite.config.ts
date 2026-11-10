import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  const proxyTo = (target: string) => ({
    target,
    changeOrigin: true,
    secure: false,
  })

  return {
    plugins: [react()],
    server: {
      host: true,
      port: 3000,
      strictPort: true,
      proxy: {
        '/api/users': proxyTo(env.VITE_DEV_USER_SERVICE_URL || 'https://localhost:8081'),
        '/api/activities': proxyTo(
          env.VITE_DEV_ACTIVITY_SERVICE_URL || 'https://localhost:8082',
        ),
        '/api/recommendations': proxyTo(
          env.VITE_DEV_RECOMMENDATION_SERVICE_URL || 'https://localhost:8083',
        ),
      },
    },
    preview: {
      host: true,
      port: 3000,
    },
  }
})
