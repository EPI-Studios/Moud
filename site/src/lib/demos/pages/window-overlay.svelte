<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log from "../ui/Log.svelte"
  import { whileVisible } from "../core/frames"
  import type { Line } from "../ui/Log.svelte"

  let clickable = $state<"Pet" | "nil">("Pet")
  let lines = $state<Line[]>([{ id: 0, text: "click the pet, then click anywhere else", kind: "idle" }])
  let x = $state(20)
  let facing = $state(1)

  let desk: HTMLDivElement | null = $state(null)
  let pet: HTMLDivElement | null = $state(null)
  let editor: HTMLDivElement | null = $state(null)
  let browser: HTMLDivElement | null = $state(null)

  let last = 0
  let next = 1

  const source = $derived(
    'local overlay = window.overlay({ gui = world:find("DesktopPet") })\n' +
      (clickable === "Pet"
        ? 'overlay.clickable = world:find("DesktopPet/Pet")    -- only the pet catches clicks'
        : "overlay.clickable = nil    -- every click passes through"),
  )

  function record(text: string, kind: Line["kind"]) {
    lines = [{ id: next++, text, kind }, ...lines]
  }

  function behind(clientX: number, clientY: number) {
    for (const app of [browser, editor]) {
      if (!app) continue
      const box = app.getBoundingClientRect()
      if (clientX >= box.left && clientX <= box.right && clientY >= box.top && clientY <= box.bottom) {
        return app.textContent
      }
    }
    return "the desktop"
  }

  function click(event: MouseEvent) {
    const onPet = pet !== null && pet.contains(event.target as Node)
    if (onPet && clickable === "Pet") {
      record("the pet caught the click", "in")
      pet?.classList.remove("window-pet-hop")
      void pet?.offsetWidth
      pet?.classList.add("window-pet-hop")
      return
    }
    record("the click passed through to " + behind(event.clientX, event.clientY), "out")
  }

  whileVisible(
    () => desk,
    (now) => {
      if (!desk || !pet) return
      const dt = last ? Math.min((now - last) / 1000, 0.05) : 0
      last = now
      const room = desk.clientWidth - pet.offsetWidth - 20
      let at = x + facing * 40 * dt
      if (at > room) {
        at = room
        facing = -1
      }
      if (at < 20) {
        at = 20
        facing = 1
      }
      x = at
    },
  )
</script>

<Demo label="Overlay clicks">
  <div
    class="window-desk window-overlay-desk"
    bind:this={desk}
    role="button"
    tabindex="0"
    aria-label="click the desktop"
    onkeydown={(event) => {
      if (event.key !== "Enter" && event.key !== " ") return
      event.preventDefault()
      const box = desk?.getBoundingClientRect()
      if (box) click({ clientX: box.left + box.width / 2, clientY: box.top + box.height / 2 } as MouseEvent)
    }}

    onclick={click}
  >
    <div class="window-app" bind:this={editor}>
      <span class="window-app-title">text editor</span>
    </div>
    <div class="window-app window-app-b" bind:this={browser}>
      <span class="window-app-title">browser</span>
    </div>
    <div class="window-overlay-layer">
      <div
        class="window-pet"
        class:window-pet-live={clickable === "Pet"}
        bind:this={pet}
        style="left: {x}px; transform: {facing < 0 ? 'scaleX(-1)' : 'none'}"
      >
        <span class="window-pet-eye"></span>
        <span class="window-pet-eye"></span>
      </div>
      <span class="window-overlay-hint">overlay: covers the primary monitor</span>
    </div>
  </div>

  <div class="demo-controls">
    <Choice label="clickable" options={["Pet", "nil"] as const} bind:value={clickable} />
  </div>

  <Log {lines} keep={5} />
  <CodePanel {source} />
</Demo>
