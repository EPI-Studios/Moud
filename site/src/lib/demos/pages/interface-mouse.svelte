<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Log from "../ui/Log.svelte"
  import type { Line } from "../ui/Log.svelte"

  type Widget = {
    name: string
    kind: string
    x: number
    y: number
    w: number
    h: number
    z: number
    takes: boolean
    text?: string
  }

  const WIDGETS: Widget[] = [
    { name: "panel", kind: "Frame", x: 24, y: 24, w: 240, h: 130, z: 1, takes: false },
    { name: "buy", kind: "TextButton", x: 40, y: 84, w: 130, h: 44, z: 1, takes: true, text: "Buy" },
    { name: "sale", kind: "TextLabel", x: 134, y: 72, w: 70, h: 24, z: 2, takes: false, text: "SALE" },
  ]

  const SOURCE = `local panel = hud:add("Frame", {
    position = udim2(0, 24, 0, 24), size = udim2(0, 240, 0, 130),
})
local buy = panel:add("TextButton", {
    position = udim2(0, 16, 0, 60), size = udim2(0, 130, 0, 44), text = "Buy",
})
local sale = panel:add("TextLabel", {
    position = udim2(0, 110, 0, 48), size = udim2(0, 70, 0, 24), text = "SALE", zIndex = 2,
})

for _, widget in { panel, buy, sale } do
    widget.mouseEnter:connect(function(w) end)
    widget.mouseButton1Down:connect(function(x, y) end)
end
buy.activated:connect(function() end)`

  let screen = $state<HTMLDivElement | null>(null)
  let inside = $state<Record<string, boolean>>({})
  let held = $state<Widget | null>(null)
  let status = $state("Point at the panel, click, right click, turn the wheel.")
  let lines = $state<Line[]>([])
  let next = 0

  function record(text: string, kind?: Line["kind"]) {
    lines = [{ id: next++, text, kind }, ...lines]
  }

  function point(event: PointerEvent | MouseEvent | WheelEvent) {
    const box = screen!.getBoundingClientRect()
    return { x: Math.round(event.clientX - box.left), y: Math.round(event.clientY - box.top) }
  }

  function under(at: { x: number; y: number }) {
    return WIDGETS.filter(
      (widget) => at.x >= widget.x && at.x < widget.x + widget.w && at.y >= widget.y && at.y < widget.y + widget.h,
    ).sort((a, b) => b.z - a.z || WIDGETS.indexOf(b) - WIDGETS.indexOf(a))
  }

  function hover(at: { x: number; y: number } | null) {
    const now = at ? under(at) : []
    for (const widget of WIDGETS) {
      const on = now.includes(widget)
      if (on && !inside[widget.name]) record(`${widget.name}.mouseEnter(${widget.name})`, "in")
      if (!on && inside[widget.name]) record(`${widget.name}.mouseLeave(${widget.name})`, "out")
      inside[widget.name] = on
    }
    if (at) {
      status = now.length
        ? `mouseMoved(${at.x}, ${at.y}) on ${now.map((widget) => widget.name).join(", ")}`
        : "over nothing"
    }
  }

  function press(at: { x: number; y: number }, signal: string) {
    const list = under(at)
    if (!list.length) {
      record(`${signal} at ${at.x}, ${at.y}: no widget there`, "idle")
      return null
    }
    let stopped: Widget | null = null
    let index = 0
    for (; index < list.length; index++) {
      record(`${list[index].name}.${signal}(${at.x}, ${at.y})`)
      if (list[index].takes) {
        stopped = list[index]
        break
      }
    }
    if (stopped && index < list.length - 1) {
      const rest = list.slice(index + 1).map((widget) => widget.name)
      record(
        `stopped at ${stopped.name}, a ${stopped.kind}; ${rest.join(", ")}${
          list.length - index - 1 > 1 ? " never hear it" : " never hears it"
        }`,
        "idle",
      )
    }
    return stopped
  }
</script>

<Demo label="Mouse events">
  <div
    bind:this={screen}
    class="demo-screen ui-screen ui-mouse-screen"
    role="application"
    aria-label="a screen that reports mouse events"
    onpointermove={(event) => hover(point(event))}
    onpointerleave={() => {
      hover(null)
      status = "over nothing"
    }}
    onpointerdown={(event) => {
      if (event.button !== 0) return
      held = press(point(event), "mouseButton1Down")
    }}
    onpointerup={(event) => {
      if (event.button !== 0) return
      const top = press(point(event), "mouseButton1Up")
      if (held && top === held && held.name === "buy") record("buy.activated()", "in")
      held = null
    }}
    oncontextmenu={(event) => {
      event.preventDefault()
      press(point(event), "mouseButton2Click")
    }}
    onwheel={(event) => {
      event.preventDefault()
      press(point(event), event.deltaY < 0 ? "mouseWheelForward" : "mouseWheelBackward")
    }}
  >
    {#each WIDGETS as widget (widget.name)}
      <div
        class="ui-widget ui-{widget.kind.toLowerCase()}"
        class:ui-hover={inside[widget.name]}
        class:ui-pressed={held === widget}
        style="left: {widget.x}px; top: {widget.y}px; width: {widget.w}px; height: {widget.h}px; z-index: {widget.z}"
      >{widget.text ?? ""}{#if widget.kind === "Frame"}<span class="ui-widget-name">panel  Frame</span>{/if}</div>
    {/each}
  </div>

  <p class="demo-note ui-status">{status}</p>

  <div class="ui-log">
    <Log {lines} keep={9} />
  </div>

  <CodePanel source={SOURCE} />
</Demo>
