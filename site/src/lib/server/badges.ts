import { and, eq } from "drizzle-orm"
import { db, schema } from "./db"

export const BADGES = [
  { id: "staff", name: "Staff", blurb: "Builds the engine.", icon: "wrench", tone: "purple", position: 0 },
  { id: "founder", name: "Early tester", blurb: "Was here before the first release.", icon: "rocket-launch", tone: "yellow", position: 1 },
  { id: "plugin-author", name: "Plugin author", blurb: "Published an editor plugin or a language addon.", icon: "puzzle-piece", tone: "blue", position: 2 },
  { id: "bug-hunter", name: "Bug hunter", blurb: "Reported a bug that turned out to be real.", icon: "bug", tone: "green", position: 3 },
  { id: "answerer", name: "Answerer", blurb: "Wrote a reply someone marked as the answer.", icon: "check-circle", tone: "green", position: 4 },
  { id: "linked", name: "Verified in game", blurb: "Linked a Minecraft account from inside Moud.", icon: "cube", tone: "blue", position: 5 },
]

export async function seedBadges() {
  await db.insert(schema.badges).values(BADGES).onConflictDoNothing()
}

export async function grant(userId: string, badgeId: string) {
  await db.insert(schema.userBadges).values({ userId, badgeId }).onConflictDoNothing()
}

export async function revoke(userId: string, badgeId: string) {
  await db
    .delete(schema.userBadges)
    .where(and(eq(schema.userBadges.userId, userId), eq(schema.userBadges.badgeId, badgeId)))
}
