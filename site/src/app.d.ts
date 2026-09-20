import type { DefaultSession } from "@auth/sveltekit"

declare global {
  namespace App {
    interface Locals {
      auth(): Promise<import("@auth/sveltekit").Session | null>
    }
    interface PageData {
      session?: import("@auth/sveltekit").Session | null
    }
  }
}

declare module "@auth/sveltekit" {
  interface Session {
    user: {
      id: string
      handle: string
      role: string
      minecraftId: string | null
      minecraftName: string | null
    } & DefaultSession["user"]
  }
}

export {}
