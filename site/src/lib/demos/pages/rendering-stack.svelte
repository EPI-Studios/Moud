<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Slider from "../ui/Slider.svelte"
  import Note from "../ui/Note.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import { whileVisible } from "../core/frames"
  import { PHOTOS } from "../core/renderAssets"

  type Param = { name: string; min: number; max: number; step: number; value: number; child?: boolean }
  type Effect = {
    klass: string
    order: number
    on: boolean
    shader?: boolean
    animated?: boolean
    params: Param[]
    glsl: string
  }
  type Pass = { klass: string; order: number; shader: boolean; params: { name: string; value: number; child: boolean }[] }
  type Uniforms = Record<string, number | number[]>

  const POST_HEADER = `#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 vUV;
uniform sampler2D SceneColorSampler;
uniform vec2 ScreenSize;
uniform float Time;
uniform float Intensity;
#define FragColor gl_FragColor
vec4 sceneColor(vec2 uv) {
    return texture2D(SceneColorSampler, uv);
}
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
`

  const POST_VERTEX = `attribute vec2 aPos;
varying vec2 vUV;
void main() {
    vUV = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
}`

  const COPY_SHADER = `uniform vec2 Scale;
void main() {
    FragColor = sceneColor((vUV - 0.5) * Scale + 0.5);
}`

  let effects = $state<Effect[]>([
    {
      klass: "BlurEffect",
      order: 40,
      on: false,
      params: [{ name: "size", min: 0, max: 24, step: 1, value: 8 }],
      glsl: `uniform float Size;
void main() {
    vec4 c = sceneColor(vUV);
    vec2 texel = 1.0 / ScreenSize;
    vec3 sum = vec3(0.0);
    float total = 0.0;
    for (int x = -3; x <= 3; x++) {
        for (int y = -3; y <= 3; y++) {
            vec2 o = vec2(float(x), float(y)) / 3.0;
            float w = exp(-dot(o, o) * 2.0);
            sum += sceneColor(vUV + o * Size * texel).rgb * w;
            total += w;
        }
    }
    FragColor = vec4(mix(c.rgb, sum / total, Intensity), c.a);
}`,
    },
    {
      klass: "ChromaticAberrationEffect",
      order: 50,
      on: false,
      params: [{ name: "amount", min: 0, max: 0.03, step: 0.001, value: 0.004 }],
      glsl: `uniform float Amount;
void main() {
    vec4 c = sceneColor(vUV);
    vec2 off = (vUV - 0.5) * 2.0 * Amount;
    vec3 split = vec3(sceneColor(vUV + off).r, c.g, sceneColor(vUV - off).b);
    FragColor = vec4(mix(c.rgb, split, Intensity), c.a);
}`,
    },
    {
      klass: "VignetteEffect",
      order: 60,
      on: true,
      params: [
        { name: "radius", min: 0, max: 1.5, step: 0.05, value: 0.75 },
        { name: "softness", min: 0.05, max: 1, step: 0.05, value: 0.45 },
      ],
      glsl: `uniform float Radius;
uniform float Softness;
uniform vec4 Color;
void main() {
    vec4 c = sceneColor(vUV);
    float dist = length((vUV - 0.5) * 2.0) / 1.41421356;
    float v = smoothstep(Radius, Radius + Softness, dist);
    FragColor = vec4(mix(c.rgb, Color.rgb, v * Intensity), c.a);
}`,
    },
    {
      klass: "PixelateEffect",
      order: 70,
      on: true,
      params: [{ name: "pixelSize", min: 1, max: 16, step: 1, value: 4 }],
      glsl: `uniform float PixelSize;
void main() {
    vec2 block = max(PixelSize, 1.0) / ScreenSize;
    vec2 uv = (floor(vUV / block) + 0.5) * block;
    vec4 c = sceneColor(vUV);
    FragColor = vec4(mix(c.rgb, sceneColor(uv).rgb, Intensity), c.a);
}`,
    },
    {
      klass: "PosterizeEffect",
      order: 75,
      on: false,
      params: [{ name: "levels", min: 2, max: 16, step: 1, value: 6 }],
      glsl: `uniform float Levels;
void main() {
    vec4 c = sceneColor(vUV);
    float steps = max(Levels - 1.0, 1.0);
    vec3 banded = floor(clamp(c.rgb, 0.0, 1.0) * steps + 0.5) / steps;
    FragColor = vec4(mix(c.rgb, banded, Intensity), c.a);
}`,
    },
    {
      klass: "SharpenEffect",
      order: 80,
      on: false,
      params: [{ name: "amount", min: 0, max: 3, step: 0.1, value: 0.5 }],
      glsl: `uniform float Amount;
void main() {
    vec4 c = sceneColor(vUV);
    vec2 t = 1.0 / ScreenSize;
    vec3 around = sceneColor(vUV + vec2(t.x, 0.0)).rgb + sceneColor(vUV - vec2(t.x, 0.0)).rgb
            + sceneColor(vUV + vec2(0.0, t.y)).rgb + sceneColor(vUV - vec2(0.0, t.y)).rgb;
    vec3 sharp = c.rgb + (c.rgb * 4.0 - around) * Amount;
    FragColor = vec4(mix(c.rgb, max(sharp, 0.0), Intensity), c.a);
}`,
    },
    {
      klass: "PostShader",
      order: 85,
      on: true,
      shader: true,
      params: [{ name: "Lines", min: 20, max: 480, step: 20, value: 240, child: true }],
      glsl: `uniform float Lines;

void main() {
    vec4 c = sceneColor(vUV);
    float line = 0.85 + 0.15 * sin(vUV.y * Lines * 3.14159);
    FragColor = vec4(mix(c.rgb, c.rgb * line, Intensity), c.a);
}`,
    },
    {
      klass: "FilmGrainEffect",
      order: 90,
      on: false,
      animated: true,
      params: [
        { name: "amount", min: 0, max: 0.5, step: 0.01, value: 0.08 },
        { name: "size", min: 1, max: 8, step: 0.5, value: 1.5 },
      ],
      glsl: `uniform float Amount;
uniform float Size;
void main() {
    vec4 c = sceneColor(vUV);
    vec2 cell = floor(vUV * ScreenSize / max(Size, 1.0));
    float n = hash12(cell + floor(Time * 24.0) * 17.0) - 0.5;
    FragColor = vec4(c.rgb + n * Amount * Intensity, c.a);
}`,
    },
  ])

  let shaderOrder = $state(85)
  let canvas = $state<HTMLCanvasElement | null>(null)
  let photo = $state<HTMLImageElement | null>(null)
  let loaded = $state(false)
  let failure = $state("")

  let renderer: { render: (list: Pass[], time: number) => void } | null = null
  const started = performance.now()

  function fmt(value: number) {
    let text = value.toFixed(3)
    if (text.indexOf(".") !== -1) text = text.replace(/0+$/, "").replace(/\.$/, "")
    return text === "-0" ? "0" : text
  }

  function orderOf(effect: Effect) {
    return effect.shader ? shaderOrder : effect.order
  }

  function uniformName(param: { name: string; child: boolean }) {
    return param.child ? param.name : param.name.charAt(0).toUpperCase() + param.name.slice(1)
  }

  const passes = $derived(
    effects
      .map((effect, index) => ({ effect, index }))
      .filter(({ effect }) => effect.on)
      .sort((a, b) => orderOf(a.effect) - orderOf(b.effect) || a.index - b.index)
      .map(({ effect }) => ({
        klass: effect.klass,
        order: orderOf(effect),
        shader: effect.shader === true,
        params: effect.params.map((param) => ({
          name: param.name,
          value: param.value,
          child: param.child === true,
        })),
      })),
  )

  const animated = $derived(effects.some((effect) => effect.on && effect.animated))

  const note = $derived.by(() => {
    const shaderOn = effects.some((effect) => effect.shader && effect.on)
    const pixelateOn = effects.some((effect) => effect.klass === "PixelateEffect" && effect.on)
    if (!shaderOn || !pixelateOn) {
      return "Every screen effect draws over what the ones before it made. Lowest order first."
    }
    return shaderOrder < 70
      ? "The scan lines run before PixelateEffect at 70, so they get pixelated with everything else."
      : "The scan lines run after PixelateEffect at 70, so they stay one line apart over the blocky picture."
  })

  const source = $derived.by(() => {
    const lines: string[] = []
    for (const pass of passes) {
      if (pass.shader) {
        lines.push(
          'local crt = world:add("PostShader", { shader = "res://post/scanlines.glsl", order = ' +
            shaderOrder +
            " })",
        )
        lines.push('crt:add("NumberValue", { name = "Lines", value = ' + fmt(pass.params[0].value) + " })")
        continue
      }
      const props = pass.params.map((param) => param.name + " = " + fmt(param.value)).join(", ")
      lines.push('world:add("' + pass.klass + '", { ' + props + " })")
    }
    return lines.length ? lines.join("\n") : "-- no screen effects in the world"
  })

  function postRenderer(view: HTMLCanvasElement, image: HTMLImageElement, onRestored: () => void) {
    const options = { alpha: false, antialias: false, depth: false, stencil: false, preserveDrawingBuffer: false }
    const context = view.getContext("webgl", options) || view.getContext("experimental-webgl", options)
    if (!context) return null
    const gl = context as WebGLRenderingContext

    type Entry = { program: WebGLProgram; at: (name: string) => WebGLUniformLocation | null }
    let programs: Record<string, Entry> = {}
    let targets: { tex: WebGLTexture; fbo: WebGLFramebuffer }[] = []
    let source: WebGLTexture | null = null
    let width = 0
    let height = 0
    let ready = false

    function compile(type: number, text: string) {
      const shader = gl.createShader(type)!
      gl.shaderSource(shader, text)
      gl.compileShader(shader)
      if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS) && !gl.isContextLost()) {
        throw new Error(gl.getShaderInfoLog(shader) || "shader did not compile")
      }
      return shader
    }

    function link(fragment: string): Entry {
      const program = gl.createProgram()!
      gl.attachShader(program, compile(gl.VERTEX_SHADER, POST_VERTEX))
      gl.attachShader(program, compile(gl.FRAGMENT_SHADER, POST_HEADER + fragment))
      gl.bindAttribLocation(program, 0, "aPos")
      gl.linkProgram(program)
      if (!gl.getProgramParameter(program, gl.LINK_STATUS) && !gl.isContextLost()) {
        throw new Error(gl.getProgramInfoLog(program) || "program did not link")
      }
      const locations: Record<string, WebGLUniformLocation | null> = {}
      return {
        program,
        at(name) {
          if (!(name in locations)) locations[name] = gl.getUniformLocation(program, name)
          return locations[name]
        },
      }
    }

    function texture() {
      const tex = gl.createTexture()!
      gl.bindTexture(gl.TEXTURE_2D, tex)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE)
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)
      return tex
    }

    function setup() {
      programs = { copy: link(COPY_SHADER) }
      for (const effect of effects) programs[effect.klass] = link(effect.glsl)
      const quad = gl.createBuffer()
      gl.bindBuffer(gl.ARRAY_BUFFER, quad)
      gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW)
      gl.enableVertexAttribArray(0)
      gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0)
      source = texture()
      gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true)
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image)
      gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false)
      targets = []
      width = 0
      height = 0
      ready = true
    }

    function size(w: number, h: number) {
      if (w === width && h === height && targets.length) return
      width = w
      height = h
      for (const target of targets) {
        gl.deleteTexture(target.tex)
        gl.deleteFramebuffer(target.fbo)
      }
      targets = [0, 1].map(() => {
        const tex = texture()
        gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null)
        const fbo = gl.createFramebuffer()!
        gl.bindFramebuffer(gl.FRAMEBUFFER, fbo)
        gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, tex, 0)
        return { tex, fbo }
      })
      gl.bindFramebuffer(gl.FRAMEBUFFER, null)
    }

    function draw(
      entry: Entry,
      input: WebGLTexture | null,
      output: WebGLFramebuffer | null,
      uniforms: Uniforms,
      time: number,
    ) {
      gl.bindFramebuffer(gl.FRAMEBUFFER, output)
      gl.viewport(0, 0, width, height)
      gl.useProgram(entry.program)
      gl.activeTexture(gl.TEXTURE0)
      gl.bindTexture(gl.TEXTURE_2D, input)
      gl.uniform1i(entry.at("SceneColorSampler"), 0)
      gl.uniform2f(entry.at("ScreenSize"), width, height)
      gl.uniform1f(entry.at("Time"), time)
      gl.uniform1f(entry.at("Intensity"), 1)
      for (const name of Object.keys(uniforms)) {
        const value = uniforms[name]
        if (Array.isArray(value) && value.length === 4) {
          gl.uniform4f(entry.at(name), value[0], value[1], value[2], value[3])
        } else if (Array.isArray(value) && value.length === 2) {
          gl.uniform2f(entry.at(name), value[0], value[1])
        } else if (!Array.isArray(value)) {
          gl.uniform1f(entry.at(name), value)
        }
      }
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4)
    }

    function render(list: Pass[], time: number) {
      if (!ready || gl.isContextLost()) return
      const w = view.width
      const h = view.height
      if (w < 1 || h < 1) return
      size(w, h)
      const photoAspect = image.naturalWidth / image.naturalHeight
      const viewAspect = w / h
      const scale = viewAspect > photoAspect ? [1, photoAspect / viewAspect] : [viewAspect / photoAspect, 1]
      let read = 0
      draw(programs.copy, source, targets[read].fbo, { Scale: scale }, time)
      for (const pass of list) {
        const uniforms: Uniforms = {}
        for (const param of pass.params) uniforms[uniformName(param)] = param.value
        if (pass.klass === "VignetteEffect") uniforms.Color = [0, 0, 0, 1]
        draw(programs[pass.klass], targets[read].tex, targets[1 - read].fbo, uniforms, time)
        read = 1 - read
      }
      draw(programs.copy, targets[read].tex, null, { Scale: [1, 1] }, time)
    }

    view.addEventListener("webglcontextlost", (event) => {
      event.preventDefault()
      ready = false
    })
    view.addEventListener("webglcontextrestored", () => {
      try {
        setup()
        onRestored()
      } catch {
        ready = false
      }
    })

    setup()
    return { render }
  }

  function paint() {
    if (!renderer || !canvas) return
    const dpr = window.devicePixelRatio || 1
    const w = Math.max(1, Math.round(canvas.clientWidth * dpr))
    const h = Math.max(1, Math.round(canvas.clientHeight * dpr))
    if (canvas.width !== w) canvas.width = w
    if (canvas.height !== h) canvas.height = h
    renderer.render(passes, ((performance.now() - started) / 1000) % 3600)
  }

  $effect(() => {
    if (!canvas || !photo || !loaded) return
    try {
      renderer = postRenderer(canvas, photo, paint)
    } catch {
      renderer = null
    }
    if (!renderer) {
      failure = "This browser cannot run WebGL, so the effects are not drawn. The photo is shown as it comes in."
      return
    }
    return () => {
      renderer = null
    }
  })

  $effect(() => {
    if (loaded) paint()
  })

  $effect(() => {
    const node = canvas
    if (!node) return
    const watcher = new ResizeObserver(() => paint())
    watcher.observe(node)
    return () => watcher.disconnect()
  })

  whileVisible(
    () => canvas,
    () => {
      if (animated) paint()
    },
  )
</script>

<Demo label="Screen effects in order">
  <div class="rendering-stack">
    <div class="rendering-photo">
      {#if failure}
        <img class="rendering-canvas" src="/art/photos/controller-960.jpg" alt="A green hillside under a grey sky" />
        <p class="rendering-gl-note">{failure}</p>
      {:else}
        <canvas bind:this={canvas} class="rendering-canvas rendering-gl" aria-label="the photo with the effects on it"
        ></canvas>
      {/if}
    </div>

    <ol class="rendering-chain">
      {#if passes.length === 0}
        <li class="rendering-chain-empty">no screen effects</li>
      {/if}
      {#each passes as pass (pass.klass)}
        <li class="rendering-chain-row" class:is-shader={pass.shader}>
          <span class="rendering-chain-order">{pass.order}</span>
          <span class="rendering-chain-name">{pass.klass}</span>
        </li>
      {/each}
    </ol>
  </div>

  <div class="demo-controls">
    <div class="demo-choice">
      <span class="demo-slider-name">in the world</span>
      <div class="demo-choice-buttons">
        {#each effects as effect (effect.klass)}
          <button
            type="button"
            class="demo-pill"
            aria-pressed={effect.on}
            onclick={() => (effect.on = !effect.on)}
          >
            {effect.klass.replace(/Effect$/, "")}
          </button>
        {/each}
      </div>
    </div>
    <Slider label="PostShader order" min={0} max={100} step={5} bind:value={shaderOrder} />
  </div>

  <div class="rendering-params">
    {#each effects as effect (effect.klass)}
      {#if effect.on}
        <div class="rendering-param-group">
          <span class="rendering-param-head">{effect.klass}</span>
          <div class="demo-controls">
            {#each effect.params as param (param.name)}
              <Slider
                label={param.name}
                min={param.min}
                max={param.max}
                step={param.step}
                bind:value={param.value}
              />
            {/each}
          </div>
        </div>
      {/if}
    {/each}
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
  <p class="rendering-credit">Photo by Pawel Kadysz on Unsplash.</p>

  <img
    bind:this={photo}
    src={PHOTOS.controller}
    alt=""
    hidden
    onload={() => (loaded = true)}
    onerror={() => (failure = "The photo did not load, so there is nothing to draw the effects over.")}
  />
</Demo>
