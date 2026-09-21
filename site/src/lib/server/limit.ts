import { sql } from "drizzle-orm"
import { db, schema } from "./db"

export type Verdict = { ok: boolean; left: number; resetAt: Date }

export async function take(key: string, limit: number, windowMs: number): Promise<Verdict> {
  const seconds = Math.ceil(windowMs / 1000)
  const window = sql`now() + make_interval(secs => ${seconds})`

  const [row] = await db
    .insert(schema.rateHits)
    .values({ key, count: 1, resetAt: new Date(Date.now() + windowMs) })
    .onConflictDoUpdate({
      target: schema.rateHits.key,
      set: {
        count: sql`case when ${schema.rateHits.resetAt} < now() then 1 else ${schema.rateHits.count} + 1 end`,
        resetAt: sql`case when ${schema.rateHits.resetAt} < now() then ${window} else ${schema.rateHits.resetAt} end`,
      },
    })
    .returning({ count: schema.rateHits.count, resetAt: schema.rateHits.resetAt })

  return { ok: row.count <= limit, left: Math.max(0, limit - row.count), resetAt: row.resetAt }
}

export function clientAddress(request: Request, fallback: string) {
  const forwarded = request.headers.get("x-forwarded-for")
  if (forwarded) return forwarded.split(",")[0].trim()
  return request.headers.get("x-real-ip") ?? fallback
}

export async function sweep() {
  await db.delete(schema.rateHits).where(sql`${schema.rateHits.resetAt} < now() - interval '1 day'`)
}
