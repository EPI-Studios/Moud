import type { DefaultSession } from "@auth/sveltekit"

declare global {
  namespace App {
    interface Error {
      message: string
      id?: string
    }
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

declare module "*?inline" {
  const value: string
  export default value
}
