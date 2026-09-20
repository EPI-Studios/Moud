<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log from "../ui/Log.svelte"
  import type { Line } from "../ui/Log.svelte"
  import PageIcon from "$lib/components/PageIcon.svelte"
  import { highlightLuau } from "$lib/render"

  type Key = { name: string; value: string; target: string | null }

  const KEYS: Key[] = [
    { name: "skin", value: '"res://skins/guard.png"', target: "appearance" },
    { name: "slim", value: "false", target: "appearance" },
    { name: "hat", value: '"minecraft:textures/entity/skeleton/skeleton.png"', target: "armour" },
    { name: "chest", value: '"minecraft:textures/entity/equipment/humanoid/iron.png"', target: "armour" },
    { name: "walkSpeed", value: "8", target: "humanoid" },
    { name: "jumpPower", value: "9", target: "humanoid" },
    { name: "maxHealth", value: "40", target: "humanoid" },
    { name: "health", value: "40", target: "humanoid" },
    { name: "scale", value: "1.2", target: "body" },
    { name: "height", value: "2.4", target: "body" },
    { name: "radius", value: "0.4", target: "body" },
    { name: "walkspeed", value: "8", target: null },
  ]

  const TARGETS = [
    { key: "body", label: "the body itself", icon: "CharacterBody3D" },
    { key: "appearance", label: "body.appearance", icon: "Node3D" },
    { key: "armour", label: "body.armour", icon: "Node3D" },
    { key: "humanoid", label: "body.humanoid", icon: "CharacterBody3D" },
  ]

  const READABLE = ["skin", "slim", "ears", "scale", "height", "radius", "walkSpeed", "jumpPower", "maxHealth"]

  let on = $state<Record<string, boolean>>({
    skin: true,
    chest: true,
    walkSpeed: true,
    maxHealth: true,
    health: true,
    scale: true,
  })
  let lines = $state<Line[]>([])
  let next = 0

  const chosen = $derived(KEYS.filter((key) => on[key.name]))
  const bad = $derived(chosen.filter((key) => key.target === null))

  const source = $derived(
    `body.humanoid:applyDescription({\n${chosen.map((key) => `    ${key.name} = ${key.value},`).join("\n")}\n})`,
  )

  const back = $derived.by(() => {
    if (bad.length) return "-- the call raised an error"
    const applied = READABLE.map((name) => {
      const hit = chosen.filter((key) => key.name === name)[0]
      return hit ? `    ${name} = ${hit.value},` : `    ${name} = ...,   -- as it was`
    })
    return `body.humanoid:getAppliedDescription()\n-- no armour, no current health\n{\n${applied.join("\n")}\n}`
  })

  function shorten(value: string) {
    return value.replace(/^"minecraft:textures\/entity\//, '"...')
  }

  function report() {
    const text = bad.length
      ? `error: "${bad[0].name}" is not a key applyDescription knows`
      : "each key landed on the instance that owns it"
    lines = [{ id: next++, text, kind: bad.length ? "out" : "in" }, ...lines]
  }

  report()
</script>

<Demo label="applyDescription">
  <div class="character-keys">
    {#each KEYS as key (key.name)}
      <button
        type="button"
        class="demo-pill"
        aria-pressed={!!on[key.name]}
        onclick={() => {
          on[key.name] = !on[key.name]
          report()
        }}
      >
        {key.name}
      </button>
    {/each}
  </div>

  <div class="character-desc-stage">
    <div class="character-targets" class:character-failed={bad.length > 0}>
      {#each TARGETS as target (target.key)}
        {@const entries = chosen.filter((key) => key.target === target.key)}
        <div class="character-target">
          <div class="character-target-name">
            <PageIcon icon="e:{target.icon}" />
            <span>{target.label}</span>
          </div>
          <div class="character-target-keys">
            {#each entries as key (key.name)}
              <span class="character-key">{key.name} = {shorten(key.value)}</span>
            {:else}
              <span class="character-none">untouched</span>
            {/each}
          </div>
        </div>
      {/each}
    </div>
    <pre class="demo-code character-back">{@html highlightLuau(back)}</pre>
  </div>

  <Log {lines} keep={2} />
  <CodePanel {source} />
</Demo>
