import { readFileSync } from "node:fs"
import { createRequire } from "node:module"
import { fileURLToPath } from "node:url"
import adapter from "@sveltejs/adapter-vercel"
import { sveltekit } from "@sveltejs/kit/vite"
import { defineConfig, type Plugin } from "vite"

const WASM = "virtual:harfbuzz-wasm"

function harfbuzzWasm(): Plugin {
  const resolved = "\0" + WASM
  return {
    name: "harfbuzz-wasm",
    resolveId: (id) => (id === WASM ? resolved : undefined),
    load(id) {
      if (id !== resolved) return
      const bytes = readFileSync(createRequire(import.meta.url).resolve("harfbuzzjs/hb.wasm"))
      return `export default "${bytes.toString("base64")}"`
    },
  }
}

export default defineConfig({
  plugins: [
    harfbuzzWasm(),
    sveltekit({
      compilerOptions: {
        runes: ({ filename }) =>
          filename.split(/[/\\]/).includes("node_modules") ? undefined : true,
      },
      adapter: adapter({ runtime: "nodejs22.x" }),
      prerender: { handleHttpError: "warn", handleMissingId: "warn" },
    }),
  ],
  ssr: { noExternal: ["satori"] },
  resolve: {
    alias: [
      {
        find: /^harfbuzzjs$/,
        replacement: fileURLToPath(new URL("./src/lib/server/harfbuzz.ts", import.meta.url)),
      },
    ],
  },
})
