<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Note from "../ui/Note.svelte"
  import { SOUNDS } from "../core/soundAssets"

  type Owner = "horn" | "bus"
  type Klass =
    | "DistortionSoundEffect"
    | "EqualizerSoundEffect"
    | "ReverbSoundEffect"
    | "CompressorSoundEffect"

  type Fx = {
    owner: Owner
    klass: Klass
    props: string
    amount: number
    priority: number
    enabled: boolean
    at: number
  }

  type Stage = { input: AudioNode; output: AudioNode }

  let effects = $state<Fx[]>([
    { owner: "horn", klass: "DistortionSoundEffect", props: "level = 0.3", amount: 0.3, priority: 1, enabled: true, at: 0 },
    { owner: "horn", klass: "EqualizerSoundEffect", props: "highGain = -12", amount: -12, priority: 0, enabled: true, at: 1 },
    { owner: "horn", klass: "ReverbSoundEffect", props: "decayTime = 4", amount: 4, priority: 2, enabled: true, at: 2 },
    { owner: "bus", klass: "CompressorSoundEffect", props: "threshold = -18", amount: -18, priority: 0, enabled: true, at: 0 },
  ])
  let stream = $state(false)

  let host = $state<HTMLDivElement | null>(null)
  let playing = $state(false)
  let loading = $state(false)

  let ctx: AudioContext | null = null
  let buffer: AudioBuffer | null = null
  let input: GainNode | null = null
  let master: GainNode | null = null
  let built: Stage[] = []
  let voices: AudioBufferSourceNode[] = []
  let timer = 0

  function sorted(owner: Owner) {
    return effects
      .filter((fx) => fx.owner === owner && fx.enabled)
      .sort((a, b) => a.priority - b.priority || a.at - b.at)
  }

  const steps = $derived([...sorted("horn"), ...sorted("bus")])
  const chain = $derived(stream ? [] : steps)

  const source = $derived.by(() => {
    const lines = [
      `local horn = deck:add("Sound", { soundId = "res://sounds/horn.ogg", bus = "sfx"${stream ? ", stream = true" : ""} })`,
    ]
    const write = (fx: Fx, holder: string) =>
      `${holder}:add("${fx.klass}", { ${fx.props}, priority = ${fx.priority}${fx.enabled ? "" : ", enabled = false"} })`
    for (const fx of effects) if (fx.owner === "horn") lines.push(write(fx, "horn"))
    lines.push("")
    lines.push('local bus = world:add("SoundBus", { bus = "sfx" })')
    for (const fx of effects) if (fx.owner === "bus") lines.push(write(fx, "bus"))
    return lines.join("\n")
  })

  function dataBytes(url: string) {
    const text = atob(url.slice(url.indexOf(",") + 1))
    const bytes = new Uint8Array(text.length)
    for (let i = 0; i < text.length; i++) bytes[i] = text.charCodeAt(i)
    return bytes.buffer
  }

  function effectNode(audio: AudioContext, fx: Fx): Stage {
    if (fx.klass === "DistortionSoundEffect") {
      const shaper = audio.createWaveShaper()
      const drive = 1 + fx.amount * 30
      const curve = new Float32Array(1024)
      for (let i = 0; i < curve.length; i++) {
        const x = (i / (curve.length - 1)) * 2 - 1
        curve[i] = Math.tanh(drive * x) / Math.tanh(drive)
      }
      shaper.curve = curve
      shaper.oversample = "4x"
      return { input: shaper, output: shaper }
    }

    if (fx.klass === "EqualizerSoundEffect") {
      const shelf = audio.createBiquadFilter()
      shelf.type = "highshelf"
      shelf.frequency.value = 3000
      shelf.gain.value = fx.amount
      return { input: shelf, output: shelf }
    }

    if (fx.klass === "ReverbSoundEffect") {
      const head = audio.createGain()
      const tail = audio.createGain()
      const dry = audio.createGain()
      const wet = audio.createGain()
      wet.gain.value = 10 ** (-6 / 20)
      const convolver = audio.createConvolver()
      const length = Math.floor(audio.sampleRate * fx.amount)
      const impulse = audio.createBuffer(2, length, audio.sampleRate)
      for (let c = 0; c < 2; c++) {
        const data = impulse.getChannelData(c)
        for (let j = 0; j < length; j++) data[j] = (Math.random() * 2 - 1) * Math.exp((-6.91 * j) / length)
      }
      convolver.buffer = impulse
      head.connect(dry)
      dry.connect(tail)
      head.connect(convolver)
      convolver.connect(wet)
      wet.connect(tail)
      return { input: head, output: tail }
    }

    const squeeze = audio.createDynamicsCompressor()
    squeeze.threshold.value = fx.amount
    squeeze.ratio.value = 5
    squeeze.attack.value = 0.1
    squeeze.release.value = 0.1
    return { input: squeeze, output: squeeze }
  }

  function route(plan: Fx[]) {
    if (!ctx || !input || !master) return
    input.disconnect()
    for (const node of built) node.output.disconnect()
    built = plan.map((fx) => effectNode(ctx as AudioContext, fx))
    let tail: AudioNode = input
    for (const node of built) {
      tail.connect(node.input)
      tail = node.output
    }
    tail.connect(master)
  }

  function fire() {
    if (!ctx || !buffer || !input) return
    const voice = ctx.createBufferSource()
    voice.buffer = buffer
    voice.connect(input)
    voice.onended = () => {
      voices = voices.filter((other) => other !== voice)
    }
    voices.push(voice)
    voice.start()
  }

  function start() {
    playing = true
    route(chain)
    fire()
    timer = window.setInterval(fire, 2600)
  }

  function stop() {
    if (!playing) return
    playing = false
    clearInterval(timer)
    for (const voice of voices) voice.stop()
    voices = []
  }

  function toggle() {
    if (playing) {
      stop()
      return
    }
    if (loading) return
    if (!ctx) {
      ctx = new AudioContext()
      input = ctx.createGain()
      master = ctx.createGain()
      master.gain.value = 0.7
      master.connect(ctx.destination)
    }
    if (ctx.state === "suspended") ctx.resume()
    if (buffer) {
      start()
      return
    }
    loading = true
    ctx.decodeAudioData(
      dataBytes(SOUNDS.sax),
      (decoded) => {
        loading = false
        buffer = decoded
        start()
      },
      () => {
        loading = false
      },
    )
  }

  $effect(() => {
    const plan = chain
    if (playing) route(plan)
  })

  $effect(() => {
    if (!host || typeof IntersectionObserver === "undefined") return
    const watcher = new IntersectionObserver((entries) => {
      if (!entries[entries.length - 1].isIntersecting) stop()
    })
    watcher.observe(host)
    return () => watcher.disconnect()
  })
</script>

<Demo label="The order effects run in">
  <div class="sound-effects" bind:this={host}>
    {#each effects as fx (fx.klass)}
      <div class="sound-effect-row">
        <span class="sound-owner">{fx.owner === "horn" ? "horn" : 'bus "sfx"'}</span>
        <span class="sound-name">
          <img class="engine-icon" src="/art/icons/AudioStreamPlayer3D.png" alt="" width="14" height="14" />
          {fx.klass}
        </span>
        <span class="sound-step">
          <span class="sound-step-name">priority</span>
          <button
            type="button"
            class="demo-pill"
            aria-label="lower priority"
            onclick={() => (fx.priority = Math.max(-3, fx.priority - 1))}
          >-</button>
          <span class="sound-priority">{fx.priority}</span>
          <button
            type="button"
            class="demo-pill"
            aria-label="raise priority"
            onclick={() => (fx.priority = Math.min(5, fx.priority + 1))}
          >+</button>
          <button
            type="button"
            class="demo-pill"
            aria-pressed={fx.enabled}
            onclick={() => (fx.enabled = !fx.enabled)}
          >enabled</button>
        </span>
      </div>
    {/each}
  </div>

  <div class="sound-flow">
    <span class="sound-node sound-node-end">horn.ogg</span>
    {#each chain as fx (fx.klass)}
      <span class="sound-arrow">→</span>
      <span class="sound-node" class:sound-node-bus={fx.owner === "bus"}>
        {fx.klass.replace("SoundEffect", "")}
      </span>
    {/each}
    <span class="sound-arrow">→</span>
    <span class="sound-node sound-node-end">out</span>
    {#if stream}
      <span class="sound-flow-note">stream = true: the sound skips the chain</span>
    {/if}
  </div>

  <Note>
    Lowest priority first; a tie keeps the order the children sit in. The horn's own effects all run
    before any of its bus's, whatever the bus effect's priority is. Press Play and change the order
    while it loops to hear it. Sound: Kenney, CC0.
  </Note>

  <div class="demo-controls demo-row">
    <button type="button" class="demo-pill demo-pill-wide" aria-pressed={playing} onclick={toggle}>
      {playing ? "Stop" : loading ? "Loading" : "Play"}
    </button>
  </div>

  <div class="demo-controls">
    <Choice label="stream" options={[false, true]} bind:value={stream} />
  </div>

  <CodePanel {source} />
</Demo>
