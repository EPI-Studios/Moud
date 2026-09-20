import postgres from "postgres"

const sql = postgres(process.env.DATABASE_URL, { max: 1 })

const BADGES = [
  ["staff", "Staff", "Builds the engine.", "wrench", "purple", 0],
  ["founder", "Early tester", "Was here before the first release.", "rocket-launch", "yellow", 1],
  ["plugin-author", "Plugin author", "Published an editor plugin or a language addon.", "puzzle-piece", "blue", 2],
  ["bug-hunter", "Bug hunter", "Reported a bug that turned out to be real.", "bug", "green", 3],
  ["answerer", "Answerer", "Wrote a reply someone marked as the answer.", "check-circle", "green", 4],
  ["linked", "Verified in game", "Linked a Minecraft account from inside Moud.", "cube", "blue", 5],
]

for (const [id, name, blurb, icon, tone, position] of BADGES) {
  await sql`insert into badge (id, name, blurb, icon, tone, position)
            values (${id}, ${name}, ${blurb}, ${icon}, ${tone}, ${position})
            on conflict (id) do update set name = excluded.name, blurb = excluded.blurb,
            icon = excluded.icon, tone = excluded.tone, position = excluded.position`
}

const staff = await sql`select id from "user" where role = 'staff'`
for (const row of staff) {
  await sql`insert into "userBadge" ("userId", "badgeId") values (${row.id}, 'staff')
            on conflict do nothing`
}

console.log(`${BADGES.length} badges ready`)
await sql.end()
