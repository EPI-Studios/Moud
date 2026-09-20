import { error, redirect } from "@sveltejs/kit"
import { eq } from "drizzle-orm"
import { db, schema } from "$lib/server/db"
import { startSession } from "$lib/server/auth"
import type { RequestHandler } from "./$types"

const PEOPLE = {
  staff: {
    handle: "dev-staff",
    name: "Dev Staff",
    role: "staff",
    minecraftId: "069a79f444e94726a5befca90e38aaf5",
    minecraftName: "Notch",
  },
  member: {
    handle: "dev-member",
    name: "Dev Member",
    role: "member",
    minecraftId: "853c80ef3c3749fdaa49938b674adae6",
    minecraftName: "jeb_",
  },
}

export const GET: RequestHandler = async ({ url, cookies }) => {
  if (import.meta.env.PROD) error(404, "Not available")

  const as = url.searchParams.get("as") === "member" ? "member" : "staff"
  const person = PEOPLE[as]

  let user = await db.query.users.findFirst({ where: eq(schema.users.handle, person.handle) })
  if (!user) {
    const [made] = await db
      .insert(schema.users)
      .values({ ...person, email: `${person.handle}@localhost` })
      .returning()
    user = made
  }

  await startSession(user.id, cookies)
  redirect(303, "/forum")
}
