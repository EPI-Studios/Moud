<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import Log from "../ui/Log.svelte"
  import Note from "../ui/Note.svelte"
  import type { Line } from "../ui/Log.svelte"
  import { highlightLuau } from "$lib/render"

  type Outcome =
    | { ok: true; replyOk: true; status: number }
    | { ok: true; replyOk: false; status: number; body: string }
    | { ok: false; error: string }

  const CASES: Record<string, Outcome> = {
    "answers 204": { ok: true, replyOk: true, status: 204 },
    "answers 429": { ok: true, replyOk: false, status: 429, body: '{"message": "You are being rate limited."}' },
    unreachable: { ok: false, error: "(the error, as text)" },
    "httpRequests off": {
      ok: false,
      error: "http requests are off, turn on httpRequests in place.toml [features]",
    },
  }
  const NAMES = Object.keys(CASES)

  const LINES = [
    "task.spawn(function()",
    "    local ok, reply = pcall(function()",
    "        return http.post(hook, http.jsonEncode({",
    '            content = winner .. " won in " .. math.floor(seconds) .. " seconds",',
    "        }))",
    "    end)",
    "    if not ok then",
    '        print("the webhook did not go through:", reply)',
    "    elseif not reply.ok then",
    '        print("the webhook answered", reply.status, reply.body)',
    "    end",
    "end)",
  ]

  let pick = $state("answers 429")

  const outcome = $derived(CASES[pick])
  const hit = $derived(outcome.ok ? (outcome.replyOk ? -1 : 9) : 7)

  const lines = $derived.by<Line[]>(() => {
    if (outcome.ok && outcome.replyOk) {
      return [
        {
          id: 0,
          text: `ok = true, reply.ok = true, reply.status = ${outcome.status}. Nothing is printed.`,
          kind: "in",
        },
      ]
    }
    if (outcome.ok) {
      return [
        { id: 0, text: `[server] the webhook answered ${outcome.status} ${outcome.body}`, kind: "out" },
      ]
    }
    return [{ id: 0, text: `[server] the webhook did not go through: ${outcome.error}`, kind: "out" }]
  })

  const note = $derived.by(() => {
    if (outcome.ok && outcome.replyOk) return "The call got an answer and the answer was a yes."
    if (outcome.ok) {
      return "The call got an answer, and the answer was a refusal. pcall returns true, reply.ok is false, and reply.status and reply.body say why."
    }
    return pick === "unreachable"
      ? "The call never got an answer. It raised, so pcall returns false and the second value is the error."
      : "Without the feature every request errors, and pcall catches that the same way."
  })
</script>

<Demo label="Two ways to go wrong">
  <div class="demo-controls hp-top">
    <Choice label="the site" options={NAMES} bind:value={pick} />
  </div>

  <pre class="demo-code">{#each LINES as line, i (i)}{#if i}{"\n"}{/if}{#if i === hit}<span class="hp-hit"
        >{@html highlightLuau(line)}</span
      >{:else}{@html highlightLuau(line)}{/if}{/each}</pre>

  <Log {lines} />
  <Note>{note}</Note>
</Demo>
