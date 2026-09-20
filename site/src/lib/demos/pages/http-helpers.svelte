<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"

  function urlEncode(text: string) {
    return encodeURIComponent(text).replace(
      /[!'()~]/g,
      (ch) => `%${ch.charCodeAt(0).toString(16).toUpperCase()}`,
    )
  }

  function guid() {
    const hex = "0123456789ABCDEF"
    let out = ""
    for (let i = 0; i < 36; i++) {
      if (i === 8 || i === 13 || i === 18 || i === 23) out += "-"
      else if (i === 14) out += "4"
      else if (i === 19) out += hex.charAt(8 + Math.floor(Math.random() * 4))
      else out += hex.charAt(Math.floor(Math.random() * 16))
    }
    return out
  }

  function luau(value: unknown): string {
    if (value === null) return "nil"
    if (Array.isArray(value)) return `{ ${value.map(luau).join(", ")} }`
    if (typeof value === "object") {
      const record = value as Record<string, unknown>
      const keys = Object.keys(record)
      if (!keys.length) return "{}"
      const pairs = keys.map((key) => {
        const name = /^[A-Za-z_][A-Za-z0-9_]*$/.test(key) ? key : `["${key}"]`
        return `${name} = ${luau(record[key])}`
      })
      return `{ ${pairs.join(", ")} }`
    }
    if (typeof value === "string") return `"${value}"`
    return String(value)
  }

  let text = $state("meek & ana: round 2?")
  let json = $state('{"ok":true,"top":"meek"}')
  let id = $state(guid())
  let braces = $state(true)

  const decoded = $derived.by(() => {
    try {
      return `true, ${luau(JSON.parse(json))}`
    } catch {
      return 'false, "jsonDecode could not read that text: ..."'
    }
  })

  const source = $derived(
    `http.urlEncode("${text}")\n-- "${urlEncode(text)}"\n\n` +
      `pcall(http.jsonDecode, '${json}')\n-- ${decoded}\n\n` +
      `http.generateGuid(${braces ? "" : "false"})\n-- "${braces ? `{${id}}` : id}"`,
  )
</script>

<Demo label="Helpers">
  <div class="demo-field">
    <span class="demo-field-prefix">{'http.urlEncode("'}</span>
    <input type="text" class="demo-input" spellcheck="false" aria-label="text to escape" bind:value={text} />
    <span class="demo-field-prefix">{'")'}</span>
  </div>

  <div class="demo-field">
    <span class="demo-field-prefix">{"pcall(http.jsonDecode, '"}</span>
    <input type="text" class="demo-input" spellcheck="false" aria-label="json to decode" bind:value={json} />
    <span class="demo-field-prefix">{"')"}</span>
  </div>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={() => (id = guid())}>new guid</button>
    <button type="button" class="demo-pill demo-pill-wide" onclick={() => (braces = !braces)}>
      braces on or off
    </button>
  </div>

  <CodePanel {source} />
</Demo>
