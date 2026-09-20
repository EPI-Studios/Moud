import { drizzle } from "drizzle-orm/postgres-js"
import postgres from "postgres"
import { env } from "$env/dynamic/private"
import * as schema from "./schema"

if (!env.DATABASE_URL) throw new Error("DATABASE_URL is missing")

const client = postgres(env.DATABASE_URL, { max: 1, prepare: false })

export const db = drizzle(client, { schema })
export { schema }
