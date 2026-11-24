import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const backend = process.env.BACKEND_URL ?? 'http://localhost:8080';
// Same-origin calls to the API (dev and preview): no CORS configuration needed.
const proxy = { '/api': { target: backend, changeOrigin: false } };

export default defineConfig({
  plugins: [react()],
  server: { proxy },
  preview: {
    host: '127.0.0.1',
    port: 4450,
    strictPort: true,
    proxy,
  },
});
