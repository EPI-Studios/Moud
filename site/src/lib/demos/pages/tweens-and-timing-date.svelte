<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Note from "../ui/Note.svelte"
  import { whileVisible } from "../core/frames"

  const DAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
  const MONTHS = [
    "January", "February", "March", "April", "May", "June", "July", "August", "September",
    "October", "November", "December",
  ]
  const PRESETS = ["%H:%M", "!%Y-%m-%d", "%Y-%m-%d %H:%M:%S", "%a %b %d", "day %j of %Y", "%q stays"]

  function pad(value: number, width: number) {
    return String(value).padStart(width, "0")
  }

  function stamp(format: string, when: Date) {
    const utc = format.charAt(0) === "!"
    const pattern = utc ? format.slice(1) : format
    const y = utc ? when.getUTCFullYear() : when.getFullYear()
    const mo = utc ? when.getUTCMonth() : when.getMonth()
    const d = utc ? when.getUTCDate() : when.getDate()
    const h = utc ? when.getUTCHours() : when.getHours()
    const mi = utc ? when.getUTCMinutes() : when.getMinutes()
    const s = utc ? when.getUTCSeconds() : when.getSeconds()
    const wd = utc ? when.getUTCDay() : when.getDay()
    const start = utc ? Date.UTC(y, 0, 1) : new Date(y, 0, 1).getTime()
    const today = utc ? Date.UTC(y, mo, d) : new Date(y, mo, d).getTime()
    const yday = Math.round((today - start) / 86400000) + 1
    const map: Record<string, string> = {
      Y: pad(y, 4), y: pad(y % 100, 2), m: pad(mo + 1, 2), d: pad(d, 2), H: pad(h, 2), M: pad(mi, 2),
      S: pad(s, 2), j: pad(yday, 3), A: DAYS[wd], a: DAYS[wd].slice(0, 3), B: MONTHS[mo], b: MONTHS[mo].slice(0, 3),
      p: h < 12 ? "AM" : "PM", I: pad(h % 12 === 0 ? 12 : h % 12, 2), "%": "%",
    }
    let out = ""
    for (let n = 0; n < pattern.length; n++) {
      const ch = pattern.charAt(n)
      if (ch !== "%" || n + 1 >= pattern.length) {
        out += ch
        continue
      }
      const f = pattern.charAt(++n)
      out += Object.prototype.hasOwnProperty.call(map, f) ? map[f] : `%${f}`
    }
    return out
  }

  let result = $state<HTMLDivElement | null>(null)
  let pattern = $state("%A %d %B, %I:%M %p")
  let when = $state(new Date())

  const text = $derived(`"${stamp(pattern, when)}"`)
  const note = $derived.by(() => {
    const unknown = pattern.replace(/%%/g, "").match(/%[^YymdHMSjAaBbpI]/g)
    return (
      (pattern.charAt(0) === "!"
        ? "The ! reads the time in UTC. "
        : "No ! in front, so this is your machine's own time zone. ") +
      (unknown
        ? `${unknown.join(" ")} is not one of the table's, so it is left as it is.`
        : "Names are English whatever the player's language is.")
    )
  })

  let lastSecond = Math.floor(Date.now() / 1000)

  whileVisible(
    () => result,
    () => {
      const second = Math.floor(Date.now() / 1000)
      if (second !== lastSecond) {
        lastSecond = second
        when = new Date()
      }
    },
  )
</script>

<Demo label="os.date">
  <div class="demo-field">
    <span class="demo-field-prefix">os.date("</span>
    <input type="text" class="demo-input" spellcheck="false" aria-label="date format" bind:value={pattern} />
    <span class="demo-field-prefix">")</span>
  </div>

  <div class="demo-chat tw-date" bind:this={result}>{text}</div>

  <div class="demo-controls demo-row">
    {#each PRESETS as preset (preset)}
      <button type="button" class="demo-pill tw-mono" onclick={() => (pattern = preset)}>{preset}</button>
    {/each}
  </div>

  <Note>{note}</Note>
</Demo>
