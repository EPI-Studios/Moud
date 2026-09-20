import adapter from "@sveltejs/adapter-vercel"
import { sveltekit } from "@sveltejs/kit/vite"
import { defineConfig } from "vite"

export default defineConfig({
  plugins: [
    sveltekit({
      compilerOptions: {
        runes: ({ filename }) =>
          filename.split(/[/\\]/).includes("node_modules") ? undefined : true,
      },
      adapter: adapter({ runtime: "nodejs22.x" }),
      prerender: { handleHttpError: "warn", handleMissingId: "warn" },
    }),
  ],
})
