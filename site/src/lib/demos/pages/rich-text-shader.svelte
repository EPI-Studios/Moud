<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { whileVisible } from "../core/frames"
  import { cssVar } from "../core/surface"

  let base = $state(0.75)
  let depth = $state(0.25)
  let speed = $state(4)
  let spread = $state(20)

  let canvas = $state<HTMLCanvasElement | null>(null)

  const mask = typeof document === "undefined" ? null : document.createElement("canvas")
  let width = 0
  let height = 0
  let split = 0
  let alpha: Uint8ClampedArray | null = null

  function num(v: number) {
    const text = String(v)
    return text.includes(".") ? text : `${text}.0`
  }

  const source = $derived(
    "// res://chat/glow.glsl\n" +
      "vec4 textColor(vec4 color, vec2 uv, vec2 glyph, float time) {\n" +
      `    float pulse = ${num(base)} + ${num(depth)} * sin(time * ${num(speed)} + uv.x * ${num(spread)});\n` +
      "    return vec4(color.rgb * vec3(1.0, 0.85, 0.3) * pulse * 1.4, color.a);\n" +
      "}",
  )

  function size() {
    if (!canvas || !mask) return
    const mctx = mask.getContext("2d", { willReadFrequently: true })
    if (!mctx) return
    const ratio = window.devicePixelRatio || 1
    const w = canvas.clientWidth || 600
    const h = 76
    width = Math.round(w * ratio)
    height = Math.round(h * ratio)
    canvas.width = mask.width = width
    canvas.height = mask.height = height

    const fontSize = Math.min(30, w / 14) * ratio
    mctx.clearRect(0, 0, width, height)
    mctx.font = `700 ${fontSize}px ${cssVar("--font-mono") || "monospace"}`
    mctx.textBaseline = "middle"
    mctx.fillStyle = "#fff"
    const first = "LEGENDARY"
    const rest = " item found"
    const total = mctx.measureText(first + rest).width
    const x = Math.max(8 * ratio, (width - total) / 2)
    mctx.fillText(first, x, height / 2)
    split = Math.ceil(x + mctx.measureText(first).width)
    mctx.fillText(rest, x + mctx.measureText(first).width, height / 2)
    alpha = mctx.getImageData(0, 0, width, height).data
  }

  whileVisible(
    () => canvas,
    (now) => {
      if (!canvas) return
      const ctx = canvas.getContext("2d", { willReadFrequently: true })
      if (!ctx) return
      const wanted = Math.round((canvas.clientWidth || 600) * (window.devicePixelRatio || 1))
      if (!alpha || canvas.width !== wanted) size()
      if (!alpha) return

      const time = now / 1000
      const image = ctx.createImageData(width, height)
      const data = image.data
      const columns: number[] = []
      for (let x = 0; x < width; x++) {
        const pulse = base + depth * Math.sin(time * speed + (x / width) * spread)
        columns.push(x < split ? pulse * 1.4 : -1)
      }
      for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
          const at = (y * width + x) * 4
          const a = alpha[at + 3]
          if (!a) continue
          const m = columns[x]
          if (m < 0) {
            data[at] = data[at + 1] = data[at + 2] = 255
          } else {
            data[at] = Math.min(255, 255 * m)
            data[at + 1] = Math.min(255, 255 * 0.85 * m)
            data[at + 2] = Math.min(255, 255 * 0.3 * m)
          }
          data[at + 3] = a
        }
      }
      ctx.putImageData(image, 0, 0)
    },
  )

  $effect(() => {
    const forget = () => {
      alpha = null
    }
    window.addEventListener("resize", forget)
    return () => window.removeEventListener("resize", forget)
  })
</script>

<Demo label="Text shader">
  <canvas bind:this={canvas} class="rt-shader"></canvas>

  <div class="demo-controls">
    <Slider label="base" min={0} max={1} step={0.05} bind:value={base} />
    <Slider label="depth" min={0} max={0.75} step={0.05} bind:value={depth} />
    <Slider label="speed" min={0} max={12} step={0.5} bind:value={speed} />
    <Slider label="uv.x spread" min={0} max={60} step={1} bind:value={spread} />
  </div>

  <CodePanel {source} />
</Demo>
