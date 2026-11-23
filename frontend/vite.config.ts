import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const backend = process.env.BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    // Same-origin calls to the API in development: no CORS configuration needed.
    proxy: { '/api': { target: backend, changeOrigin: false } },
  },
  preview: {
    host: '127.0.0.1',
    port: 4450,
    strictPort: true,
  },
});
