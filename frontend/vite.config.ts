import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// Fejlesztéskor a Vite (5173) a /api kéréseket a Spring Boot backendre (8080) továbbítja.
// A "npm run build" a kész fájlokat a backend static mappájába írja, így egyetlen jar-ból fut az egész.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // Hálózati / leképezett meghajtón (pl. H:) a fájlfigyelés nem működik: VITE_POLLING=1 esetén lekérdezéses figyelés.
    watch: process.env.VITE_POLLING === "1" ? { usePolling: true, interval: 500 } : undefined,
    proxy: {
      "/api": { target: "http://localhost:8080", changeOrigin: false },
    },
  },
  build: {
    outDir: "../backend/src/main/resources/static",
    emptyOutDir: true,
  },
});
