<script lang="ts">
  import { onMount } from "svelte"
  import Demo from "../ui/Demo.svelte"
  import Stage from "../ui/Stage.svelte"
  import Slider from "../ui/Slider.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { clamp } from "../core/pointer"
  import { whileVisible } from "../core/frames"
  import { cssVar, fitScaled, mono } from "../core/surface"
  import { tinted, type Rgb } from "../core/tint"

  const W = 420
  const H = 200
  const SCALE = 40
  const originX = W / 2
  const originY = H - 24

  type PresetName = "embers" | "burst"
  type LookName = "flame" | "spark_4" | "glint" | "big_smoke_4" | "heart" | "note"

  type Preset = {
    part: { w: number; h: number }
    rate: number
    lifetimeMin: number
    lifetimeMax: number
    speedMin: number
    speedMax: number
    spreadAngle: number
    accel: number
    drag: number
  }

  type Look = {
    sizeStart: number
    sizeEnd: number
    colorStart: Rgb
    colorEnd: Rgb
    transparencyStart: number
    transparencyEnd: number
    brightness: number
    lightEmission: number
  }

  const PRESETS: Record<PresetName, Preset> = {
    embers: {
      part: { w: 1, h: 0.2 },
      rate: 6, lifetimeMin: 0.8, lifetimeMax: 1.6, speedMin: 2, speedMax: 4,
      spreadAngle: 25, accel: -3, drag: 1,
    },
    burst: {
      part: { w: 1, h: 1 },
      rate: 0, lifetimeMin: 0.4, lifetimeMax: 0.9, speedMin: 3, speedMax: 7,
      spreadAngle: 180, accel: -9.8, drag: 0,
    },
  }

  const LOOKS: Record<LookName, Look> = {
    flame: {
      sizeStart: 0.4, sizeEnd: 0.1, colorStart: [1, 1, 1], colorEnd: [1, 0.4, 0.2],
      transparencyStart: 0, transparencyEnd: 0, brightness: 1, lightEmission: 1,
    },
    spark_4: {
      sizeStart: 0.3, sizeEnd: 0, colorStart: [1, 0.9, 0.4], colorEnd: [1, 1, 1],
      transparencyStart: 0, transparencyEnd: 0, brightness: 1, lightEmission: 1,
    },
    glint: {
      sizeStart: 0.3, sizeEnd: 0, colorStart: [1, 1, 1], colorEnd: [1, 1, 1],
      transparencyStart: 0, transparencyEnd: 0, brightness: 1, lightEmission: 1,
    },
    big_smoke_4: {
      sizeStart: 0.5, sizeEnd: 1.2, colorStart: [1, 1, 1], colorEnd: [1, 1, 1],
      transparencyStart: 0.3, transparencyEnd: 1, brightness: 1, lightEmission: 0,
    },
    heart: {
      sizeStart: 0.4, sizeEnd: 0.4, colorStart: [1, 1, 1], colorEnd: [1, 1, 1],
      transparencyStart: 0, transparencyEnd: 1, brightness: 1, lightEmission: 0,
    },
    note: {
      sizeStart: 0.4, sizeEnd: 0.4, colorStart: [0.3, 1, 0.4], colorEnd: [0.3, 0.6, 1],
      transparencyStart: 0, transparencyEnd: 0, brightness: 1, lightEmission: 0,
    },
  }

  const LOOK_NAMES = Object.keys(LOOKS) as LookName[]

  type Particle = { x: number; y: number; vx: number; vy: number; age: number; life: number }

  let preset = $state<PresetName>("embers")
  let texture = $state<LookName>("flame")

  let rate = $state(6)
  let lifetimeMax = $state(1.6)
  let sizeStart = $state(0.4)
  let spreadAngle = $state(25)
  let accel = $state(-3)
  let drag = $state(1)

  let lifetimeMin = $state(0.8)
  let speedMin = $state(2)
  let speedMax = $state(4)
  let part = $state({ w: 1, h: 0.2 })

  let sizeEnd = $state(0.1)
  let colorStart = $state<Rgb>([1, 1, 1])
  let colorEnd = $state<Rgb>([1, 0.4, 0.2])
  let transparencyStart = $state(0)
  let transparencyEnd = $state(0)
  let brightness = $state(1)
  let lightEmission = $state(1)

  let canvas = $state<HTMLCanvasElement | null>(null)
  let readout = $state("")

  let particles: Particle[] = []
  let owed = 0
  let last = 0

  const num = (v: number) => String(Math.round(v * 100) / 100)
  const colorText = (c: Rgb) => `color(${num(c[0])}, ${num(c[1])}, ${num(c[2])})`
  const mix = (a: number, b: number, t: number) => a + (b - a) * t

  const source = $derived.by(() => {
    const same = colorStart.join() === colorEnd.join()
    const lines: string[] = []
    lines.push(preset === "burst" ? 'local burst = crate:add("ParticleEmitter", {' : 'pit:add("ParticleEmitter", {')
    lines.push(`    texture = "${texture}",`)
    lines.push(`    rate = ${num(rate)},`)
    lines.push(`    lifetimeMin = ${num(lifetimeMin)}, lifetimeMax = ${num(lifetimeMax)},`)
    lines.push(`    speedMin = ${num(speedMin)}, speedMax = ${num(speedMax)},`)
    lines.push(`    spreadAngle = ${num(spreadAngle)},`)
    lines.push(`    acceleration = vec3(0, ${num(accel)}, 0),`)
    if (drag) lines.push(`    drag = ${num(drag)},`)
    lines.push(`    sizeStart = ${num(sizeStart)}, sizeEnd = ${num(sizeEnd)},`)
    lines.push(`    colorStart = ${colorText(colorStart)}${same ? "" : `, colorEnd = ${colorText(colorEnd)}`},`)
    if (transparencyStart || transparencyEnd) {
      lines.push(`    transparencyStart = ${num(transparencyStart)}, transparencyEnd = ${num(transparencyEnd)},`)
    }
    if (brightness !== 1) lines.push(`    brightness = ${num(brightness)},`)
    if (lightEmission) lines.push(`    lightEmission = ${num(lightEmission)},`)
    lines.push("})")
    if (preset === "burst") lines.push("", "burst:emit(40)")
    return lines.join("\n")
  })

  function spawn() {
    const theta = Math.acos(1 - Math.random() * (1 - Math.cos((spreadAngle * Math.PI) / 180)))
    const phi = Math.random() * Math.PI * 2
    const speed = mix(speedMin, speedMax, Math.random())
    particles.push({
      x: (Math.random() - 0.5) * part.w,
      y: (Math.random() - 0.5) * part.h,
      vx: Math.sin(theta) * Math.cos(phi) * speed,
      vy: Math.cos(theta) * speed,
      age: 0,
      life: mix(lifetimeMin, lifetimeMax, Math.random()),
    })
    if (particles.length > 8192) particles.shift()
  }

  function draw(dt: number) {
    if (!canvas) return
    const view = fitScaled(canvas, W, H)
    if (!view) return
    const { ctx, unit } = view

    ctx.fillStyle = cssVar("--bg")
    ctx.fillRect(0, 0, W, H)
    ctx.strokeStyle = cssVar("--line")
    ctx.lineWidth = 1
    for (let m = 1; m * SCALE < originY; m++) {
      ctx.beginPath()
      ctx.moveTo(0, originY - m * SCALE + 0.5)
      ctx.lineTo(W, originY - m * SCALE + 0.5)
      ctx.stroke()
    }
    ctx.fillStyle = cssVar("--text-light")
    ctx.font = mono(unit, 11)
    for (let n = 1; n * SCALE < originY; n++) ctx.fillText(`${n} m`, 6, originY - n * SCALE - 3)
    ctx.fillStyle = cssVar("--bg-4")
    ctx.fillRect(0, originY + part.h * SCALE * 0.5, W, H)

    if (dt > 0) {
      owed += rate * dt
      while (owed >= 1) {
        spawn()
        owed -= 1
      }
      const keep = 0.5 ** (drag * dt)
      for (let i = particles.length - 1; i >= 0; i--) {
        const p = particles[i]
        p.age += dt
        if (p.age >= p.life) {
          particles.splice(i, 1)
          continue
        }
        p.vy += accel * dt
        p.vx *= keep
        p.vy *= keep
        p.x += p.vx * dt
        p.y += p.vy * dt
      }
    }

    ctx.strokeStyle = cssVar("--text-main")
    ctx.fillStyle = cssVar("--surface")
    const pw = part.w * SCALE
    const ph = part.h * SCALE
    ctx.fillRect(originX - pw / 2, originY - ph / 2, pw, ph)
    ctx.strokeRect(originX - pw / 2 + 0.5, originY - ph / 2 + 0.5, pw, ph)

    ctx.imageSmoothingEnabled = false
    ctx.globalCompositeOperation = lightEmission ? "lighter" : "source-over"
    for (const p of particles) {
      const t = p.age / p.life
      const size = mix(sizeStart, sizeEnd, t) * SCALE
      if (size <= 0.5) continue
      const color: Rgb = [
        mix(colorStart[0], colorEnd[0], t) * brightness,
        mix(colorStart[1], colorEnd[1], t) * brightness,
        mix(colorStart[2], colorEnd[2], t) * brightness,
      ]
      const image = tinted(texture, color, false)
      if (!image) continue
      ctx.globalAlpha = clamp(1 - mix(transparencyStart, transparencyEnd, t), 0, 1)
      ctx.drawImage(image, originX + p.x * SCALE - size / 2, originY - p.y * SCALE - size / 2, size, size)
    }
    ctx.globalAlpha = 1
    ctx.globalCompositeOperation = "source-over"

    const keeps = Math.round(rate * lifetimeMax)
    readout =
      `${particles.length} alive now. rate ${num(rate)} times lifetimeMax ` +
      `${num(lifetimeMax)} keeps up to ${keeps} alive from the steady flow.`
  }

  $effect(() => {
    const look = LOOKS[texture]
    sizeStart = look.sizeStart
    sizeEnd = look.sizeEnd
    colorStart = look.colorStart
    colorEnd = look.colorEnd
    transparencyStart = look.transparencyStart
    transparencyEnd = look.transparencyEnd
    brightness = look.brightness
    lightEmission = look.lightEmission
  })

  $effect(() => {
    const shape = PRESETS[preset]
    part = shape.part
    rate = shape.rate
    lifetimeMin = shape.lifetimeMin
    lifetimeMax = shape.lifetimeMax
    speedMin = shape.speedMin
    speedMax = shape.speedMax
    spreadAngle = shape.spreadAngle
    accel = shape.accel
    drag = shape.drag
    particles = []
    owed = 0
  })

  $effect(() => {
    if (lifetimeMin > lifetimeMax) lifetimeMin = lifetimeMax
  })

  onMount(() => draw(0))

  whileVisible(
    () => canvas,
    (now) => {
      const dt = last ? Math.min((now - last) / 1000, 0.05) : 0
      last = now
      draw(dt)
    },
  )
</script>

<Demo label="Particle emitter">
  <Stage>
    <canvas
      bind:this={canvas}
      class="effects-canvas"
      style="aspect-ratio: {W} / {H}"
    ></canvas>
  </Stage>

  <p class="demo-note effects-readout">{readout}</p>

  <div class="demo-controls demo-row">
    <button
      type="button"
      class="demo-pill demo-pill-wide"
      onclick={() => {
        for (let i = 0; i < 40; i++) spawn()
      }}
    >emit(40)</button>
  </div>

  <div class="demo-controls">
    <Choice label="texture" options={LOOK_NAMES} bind:value={texture} />
    <Choice label="preset" options={["embers", "burst"] as const} bind:value={preset} />
    <Slider label="rate" min={0} max={60} step={1} bind:value={rate} />
    <Slider label="lifetimeMax" min={0.5} max={4} step={0.1} bind:value={lifetimeMax} />
    <Slider label="sizeStart" min={0.1} max={1.5} step={0.05} bind:value={sizeStart} />
    <Slider label="spreadAngle" min={0} max={180} step={1} bind:value={spreadAngle} />
    <Slider label="acceleration y" min={-12} max={4} step={0.1} bind:value={accel} />
    <Slider label="drag" min={0} max={4} step={0.1} bind:value={drag} />
  </div>

  <CodePanel {source} />

  <p class="demo-note effects-credit">Textures: Minecraft (Mojang)</p>
</Demo>
