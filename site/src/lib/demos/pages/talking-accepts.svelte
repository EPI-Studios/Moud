<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"

  type Arg = { text: string; kind: string }
  type Take = { kind: string; optional: boolean }

  const KINDS: Record<string, string> = {
    bool: "bool", boolean: "bool", number: "number", num: "number", string: "string", text: "string",
    vec3: "vec3", vector3: "vec3", quat: "quat", quaternion: "quat", cframe: "cframe", color: "color",
    colour: "color", udim2: "udim2", instance: "instance", list: "list", array: "list", table: "table", map: "table",
  }

  const ARGS: Arg[] = [
    { text: "true", kind: "bool" },
    { text: "4", kind: "number" },
    { text: '"go"', kind: "string" },
    { text: "vec3(1, 2, 3)", kind: "vec3" },
    { text: "cframe(0, 65, 0)", kind: "cframe" },
    { text: "color(1, 0, 0)", kind: "color" },
    { text: 'world:find("door")', kind: "instance" },
    { text: "{ 1, 2, 3 }", kind: "list" },
    { text: "{ hp = 3 }", kind: "table" },
  ]

  let accepts = $state("vec3, number?")
  let sent = $state<Arg[]>([ARGS[3], ARGS[1]])

  const source = $derived(`aim:fireServer(${sent.map((arg) => arg.text).join(", ")})`)

  function parse(text: string): { error: string; takes?: undefined } | { takes: Take[]; error?: undefined } {
    const takes: Take[] = []
    let seenOptional = false
    for (const part of text.split(",")) {
      let word = part.trim()
      if (!word) continue
      const optional = word.endsWith("?")
      if (optional) word = word.slice(0, -1).trim()
      const kind = KINDS[word.toLowerCase()]
      if (!kind) {
        return {
          error: `unknown argument kind "${word}", expected bool, number, string, vec3, quat, cframe, color, udim2, instance, list or table`,
        }
      }
      if (seenOptional && !optional) return { error: `"${text}": required argument after an optional one` }
      seenOptional = seenOptional || optional
      takes.push({ kind, optional })
    }
    return { takes }
  }

  const verdict = $derived.by(() => {
    const parsed = parse(accepts)
    if (parsed.error) return { good: false, text: `The remote cannot be made: ${parsed.error}` }

    const takes = parsed.takes ?? []
    if (sent.length > takes.length) {
      const room = takes.length ? `${takes.length} argument${takes.length === 1 ? "" : "s"}` : "no arguments"
      return { good: false, text: `Refused: aim takes ${room} and this sends ${sent.length}.` }
    }
    for (let n = 0; n < takes.length; n++) {
      const arg = sent[n]
      if (!arg) {
        if (takes[n].optional) continue
        return { good: false, text: `Refused: argument ${n + 1} must be ${takes[n].kind}, got nil.` }
      }
      if (arg.kind !== takes[n].kind) {
        return {
          good: false,
          text: `Refused: argument ${n + 1} must be ${takes[n].kind}, and ${arg.text} is a ${arg.kind}.`,
        }
      }
    }
    const tail = sent.length ? sent.map((arg) => arg.text).join(", ") : "nothing else"
    return { good: true, text: `Delivered: the server's onServer handler runs with the sender's body, then ${tail}.` }
  })
</script>

<Demo label="What a remote accepts">
  <div class="demo-field">
    <span class="demo-field-prefix">{'world:add("Remote", { name = "aim", accepts = "'}</span>
    <input type="text" class="demo-input" spellcheck="false" aria-label="accepts" bind:value={accepts} />
    <span class="demo-field-prefix">{'" })'}</span>
  </div>

  <div class="tk-args">
    <div class="tk-arg-row">
      <span class="demo-slider-name">add</span>
      <div class="demo-choice-buttons">
        {#each ARGS as arg (arg.text)}
          <button
            type="button"
            class="demo-pill tk-arg"
            onclick={() => {
              if (sent.length < 4) sent = [...sent, arg]
            }}
          >
            {arg.text}
          </button>
        {/each}
        <button type="button" class="demo-pill tk-arg tk-clear" onclick={() => (sent = [])}>clear</button>
      </div>
    </div>
  </div>

  <CodePanel {source} />
  <p class="tk-verdict" class:tk-good={verdict.good} class:tk-bad={!verdict.good}>{verdict.text}</p>
</Demo>
