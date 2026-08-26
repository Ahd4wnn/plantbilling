import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";
import path from "node:path";

// Brand colour for the PWA theme/splash (Android's task-switcher tint and the
// install splash background). Keep in sync with tailwind primary-600 and with
// Brand.Orange in the Android app's ui/theme/Color.kt.
const BRAND = "#F05B01";

export default defineConfig({
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src"),
    },
  },
  plugins: [
    react(),
    VitePWA({
      // Auto-update: when a new build is deployed, the service worker updates in
      // the background and activates on the next load (see registerSW in main.tsx).
      registerType: "autoUpdate",
      includeAssets: ["favicon.png", "icons/apple-touch-icon.png"],
      manifest: {
        name: "PlantBill",
        short_name: "PlantBill",
        description: "Simple billing for plant shops.",
        theme_color: BRAND,
        background_color: BRAND,
        display: "standalone",
        orientation: "portrait",
        start_url: "/",
        scope: "/",
        icons: [
          { src: "icons/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
          { src: "icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
          { src: "icons/maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
        ],
      },
      workbox: {
        // Cache the app shell (precache build assets). We deliberately do NOT
        // cache API/billing data — offline sync is a future concern, not now.
        globPatterns: ["**/*.{js,css,html,svg,png,ico,woff2}"],
        cleanupOutdatedCaches: true,
        navigateFallback: "index.html",
      },
      devOptions: {
        // Enable the SW in `vite dev` so it can be exercised locally.
        enabled: false,
      },
    }),
  ],
  server: {
    port: 5173,
    host: true,
  },
});
