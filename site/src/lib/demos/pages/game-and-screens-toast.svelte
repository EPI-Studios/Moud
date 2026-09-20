<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log from "../ui/Log.svelte"
  import type { Line } from "../ui/Log.svelte"
  import Mc2dCredit from "../ui/Mc2dCredit.svelte"
  import { whileVisible } from "../core/frames"
  import { createRayScene, createRayView, createRayWorld } from "../core/mcRay"

  type Toast = { id: number; title: string; text: string; buttons: string[]; left: number }

  let title = $state("Wave 3")
  let text = $state("Hold the gate")
  let button1 = $state("Ready")
  let button2 = $state("Later")
  let duration = $state(5)

  let toasts = $state<Toast[]>([])
  let lines = $state<Line[]>([
    { id: 0, text: "send a few; they stack in the order they were sent", kind: "idle" },
  ])
  let backdrop = $state<HTMLCanvasElement | null>(null)

  let nextToast = 1
  let nextLine = 1
  let last = 0

  const source = $derived.by(() => {
    const out = ["ui.sendNotification({", "    title = " + quote(title) + ","]
    if (text) out.push("    text = " + quote(text) + ",")
    out.push("    duration = " + duration + ",")
    if (button1) out.push("    button1 = " + quote(button1) + ",")
    if (button2) out.push("    button2 = " + quote(button2) + ",")
    out.push('    callback = function(button) print(button, "was pressed") end,')
    out.push("})")
    return out.join("\n")
  })

  function quote(value: string) {
    return '"' + value.replace(/\\/g, "\\\\").replace(/"/g, '\\"') + '"'
  }

  function record(line: string, kind: Line["kind"]) {
    lines = [{ id: nextLine++, text: line, kind }, ...lines].slice(0, 5)
  }

  function send() {
    if (!title) {
      record("error: a notification with no title is an error", "out")
      return
    }
    toasts = [
      ...toasts,
      {
        id: nextToast++,
        title,
        text,
        buttons: [button1, button2].filter(Boolean),
        left: duration,
      },
    ]
  }

  function press(toast: Toast, name: string) {
    record(name + " was pressed", "in")
    toasts = toasts.filter((entry) => entry.id !== toast.id)
  }

  whileVisible(
    () => backdrop,
    (now) => {
      const dt = last ? Math.min((now - last) / 1000, 0.1) : 0
      last = now
      if (!toasts.length) return
      const over: number[] = []
      for (const toast of toasts) {
        toast.left -= dt
        if (toast.left <= 0) {
          over.push(toast.id)
          record('"' + toast.title + '" ran out of time; the callback never runs', "idle")
        }
      }
      if (over.length) toasts = toasts.filter((toast) => !over.includes(toast.id))
    },
  )

  $effect(() => {
    const node = backdrop
    if (!node) return
    const land = createRayWorld(1 + Math.floor(Math.random() * 100000))
    const scene = createRayScene(node, land, createRayView(land))
    return () => scene.destroy()
  })
</script>

<Demo label="Notifications">
  <div class="game-and-screens-screen game-and-screens-toasts-screen">
    <canvas bind:this={backdrop} class="game-and-screens-world" aria-label="a world seen from a player's eyes"
    ></canvas>
    <div class="game-and-screens-stack">
      {#each toasts as toast (toast.id)}
        <div class="game-and-screens-toast" style="opacity: {Math.min(1, toast.left).toFixed(2)}">
          <div class="game-and-screens-toast-words">
            <span class="game-and-screens-toast-title">{toast.title}</span>
            {#if toast.text}
              <span class="game-and-screens-toast-text">{toast.text}</span>
            {/if}
          </div>
          {#if toast.buttons.length}
            <div class="game-and-screens-toast-buttons">
              {#each toast.buttons as name, i (i)}
                <button
                  type="button"
                  class="game-and-screens-toast-button"
                  onclick={() => press(toast, name)}
                >
                  {name}
                </button>
              {/each}
            </div>
          {/if}
        </div>
      {/each}
    </div>
  </div>
  <Mc2dCredit />

  <div class="game-and-screens-fields">
    <label class="demo-field">
      <span class="demo-field-prefix">title =</span>
      <input type="text" class="demo-input" spellcheck="false" bind:value={title} />
    </label>
    <label class="demo-field">
      <span class="demo-field-prefix">text =</span>
      <input type="text" class="demo-input" spellcheck="false" bind:value={text} />
    </label>
    <label class="demo-field">
      <span class="demo-field-prefix">button1 =</span>
      <input type="text" class="demo-input" spellcheck="false" bind:value={button1} />
    </label>
    <label class="demo-field">
      <span class="demo-field-prefix">button2 =</span>
      <input type="text" class="demo-input" spellcheck="false" bind:value={button2} />
    </label>
  </div>

  <div class="demo-controls">
    <Slider label="duration" min={1} max={12} step={1} bind:value={duration} />
  </div>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" onclick={send}>send</button>
  </div>

  <Log {lines} keep={5} />
  <CodePanel {source} />
</Demo>
