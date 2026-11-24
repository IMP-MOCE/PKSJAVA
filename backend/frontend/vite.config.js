import { defineConfig } from 'vite'

const target = process.env.PARKING_API_URL || 'http://127.0.0.1:8081'

export default defineConfig({
  esbuild: { jsx: 'automatic' },
  server: {
    host: '127.0.0.1',
    proxy: {
      '/api': {
        target,
        changeOrigin: true,
        configure(proxy) {
          proxy.on('proxyReq', (proxyRequest, request) => {
            if (request.headers.origin === 'http://' + request.headers.host) {
              proxyRequest.setHeader('Origin', new URL(target).origin)
            }
          })
        }
      },
      '/swagger-ui': target,
      '/v3/api-docs': target,
      '/actuator': target
    }
  }
})
