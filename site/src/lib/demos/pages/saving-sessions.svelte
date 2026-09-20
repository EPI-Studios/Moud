<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Log from "../ui/Log.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import type { Line } from "../ui/Log.svelte"

  type Act = "session" | "save" | "release" | "crash" | "restart"
  type Server = { id: number; session: { coins: number } | null; down: boolean }

  const SOURCE =
    'local session = players:session("meek")   -- errors when another server holds it\n' +
    "session.data.coins = (session.data.coins or 0) + 1\n" +
    "session:save()\n" +
    "session:release()                          -- saves and lets go"

  const ACTS: [Act, string][] = [
    ["session", 'players:session("meek")'],
    ["save", "session:save()"],
    ["release", "session:release()"],
    ["crash", "crash"],
    ["restart", "start again"],
  ]

  let servers = $state<Server[]>([
    { id: 1, session: null, down: false },
    { id: 2, session: null, down: false },
  ])
  let stored = $state(0)
  let holder = $state<number | null>(null)
  let expiry = $state<number | null>(null)
  let clock = $state(0)
  let lines = $state<Line[]>([{ id: 0, text: "take the session on one server, then try the other", kind: "idle" }])
  let next = 1

  const lock = $derived.by(() => {
    let text = `players/meek  stored coins ${stored}  `
    if (holder === null) text += "unlocked"
    else if (expiry !== null) {
      const left = Math.max(0, (expiry - clock) / 1000)
      text += `held by server ${holder}, which is gone: lets go in ${Math.ceil(left * 10)} s`
    } else text += `held by server ${holder}`
    return text
  })

  function record(text: string, kind?: Line["kind"]) {
    lines = [{ id: next++, text, kind }, ...lines]
  }

  function act(server: Server, what: Act) {
    if (what === "session") {
      if (holder !== null && (holder !== server.id || expiry !== null)) {
        record(
          `server ${server.id}: error: another server holds players/meek, which is the save it is still writing`,
          "out",
        )
        return
      }
      holder = server.id
      server.session = { coins: stored + 1 }
      record(`server ${server.id}: took the session and loaded coins ${stored}, then added 1`, "in")
    } else if (what === "save") {
      stored = server.session!.coins
      record(`server ${server.id}: session:save() wrote coins ${stored} and kept the lock`)
    } else if (what === "release") {
      stored = server.session!.coins
      server.session = null
      holder = null
      record(`server ${server.id}: session:release() saved coins ${stored} and let go`, "in")
    } else if (what === "crash") {
      server.down = true
      const held = server.session !== null
      server.session = null
      if (held) {
        clock = performance.now()
        expiry = clock + 6000
        record(`server ${server.id} crashed while holding meek. Its unsaved change is lost`, "out")
      } else {
        record(`server ${server.id} crashed`, "out")
      }
    } else if (what === "restart") {
      server.down = false
      record(`server ${server.id} is back up`)
    }
  }

  function describe(server: Server) {
    if (server.down) return "crashed"
    if (server.session) return `holds meek, session.data.coins = ${server.session.coins}`
    return "no session"
  }

  function blocked(server: Server, what: Act) {
    if (what === "session") return server.session !== null
    if (what === "save" || what === "release") return server.session === null
    return false
  }

  $effect(() => {
    if (expiry === null) return
    const timer = setInterval(() => {
      clock = performance.now()
      if (expiry !== null && expiry - clock <= 0) {
        expiry = null
        holder = null
        record("a minute passed: the crashed server's hold ran out, the save is free", "in")
      }
    }, 100)
    return () => clearInterval(timer)
  })
</script>

<Demo label="Two servers, one save">
  <div class="sv-servers">
    {#each servers as server (server.id)}
      <div class="sv-server" class:sv-down={server.down}>
        <span class="sv-name">server {server.id}</span>
        <span class="sv-state">{describe(server)}</span>
        <div class="sv-buttons">
          {#each ACTS as [what, text] (what)}
            <button
              type="button"
              class="demo-pill demo-pill-wide"
              hidden={what === "restart" ? !server.down : server.down}
              disabled={blocked(server, what)}
              onclick={() => act(server, what)}
            >
              {text}
            </button>
          {/each}
        </div>
      </div>
    {/each}
  </div>

  <div class="sv-lock" class:sv-held={holder !== null}>{lock}</div>

  <Log {lines} keep={6} />
  <CodePanel source={SOURCE} />
</Demo>
