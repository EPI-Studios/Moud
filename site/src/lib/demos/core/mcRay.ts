import { MC_TEXTURES, readPixels, type LoadedTexture } from "./mcTextures"

const AIR = 0
const GRASS = 1
const DIRT = 2
const STONE = 3
const SAND = 4
const LOG = 5
const LEAVES = 6
const WATER = 7
const COBBLE = 8

const NAMES = [
  "grass_block_top",
  "grass_block_side",
  "grass_block_side_overlay",
  "dirt",
  "stone",
  "sand",
  "oak_log",
  "oak_log_top",
  "oak_leaves",
  "water_still",
  "cobblestone",
] as const

type BlockName = (typeof NAMES)[number]
type RayPixels = Record<BlockName | "clouds" | "sun", LoadedTexture>

const TINT = { grass: [0x91, 0xbd, 0x59], foliage: [0x77, 0xab, 0x2f], water: [0x3f, 0x76, 0xe4] }
const SKY = [0x78, 0xa7, 0xff]
const FOG = [0xc0, 0xd8, 0xff]

let pixels: RayPixels | null = null
let loading: Promise<RayPixels> | null = null

function loadAll() {
  if (pixels) return Promise.resolve(pixels)
  if (!loading) {
    const wanted: [string, string, number?, number?][] = NAMES.map((name) => [
      name,
      MC_TEXTURES["block/" + name],
      16,
      16,
    ])
    wanted.push(["clouds", MC_TEXTURES["environment/clouds"]])
    wanted.push(["sun", MC_TEXTURES["environment/celestial/sun"]])
    loading = Promise.all(wanted.map(([, src, w, h]) => readPixels(src, w, h))).then((list) => {
      const out = {} as RayPixels
      wanted.forEach(([key], i) => {
        out[key as keyof RayPixels] = list[i]
      })
      pixels = out
      return out
    })
  }
  return loading
}

function noise2(seed: number) {
  const p = new Float32Array(1024)
  let r = seed
  for (let i = 0; i < 1024; i++) {
    r = (r * 16807) % 2147483647
    p[i] = r / 2147483647
  }
  const at = (x: number, z: number) => p[((x * 374761393 + z * 668265263) >>> 0) % 1024]
  const s = (t: number) => t * t * (3 - 2 * t)
  return (x: number, z: number) => {
    const x0 = Math.floor(x)
    const z0 = Math.floor(z)
    const fx = s(x - x0)
    const fz = s(z - z0)
    const a = at(x0, z0)
    const b = at(x0 + 1, z0)
    const c = at(x0, z0 + 1)
    const d = at(x0 + 1, z0 + 1)
    const top = a + (b - a) * fx
    const bottom = c + (d - c) * fx
    return top + (bottom - top) * fz
  }
}

export type RayWorld = {
  N: number
  H: number
  SEA: number
  cells: Uint8Array
  height: Int16Array
  idx: (x: number, y: number, z: number) => number
}

export type RayView = {
  x: number
  y: number
  z: number
  yaw: number
  pitch: number
  fov: number
  far: number
  sun?: [number, number, number]
  cloudShift?: number
}

export function createRayWorld(seed: number): RayWorld {
  const N = 128
  const H = 48
  const SEA = 14
  const cells = new Uint8Array(N * H * N)
  const n = noise2(seed)
  const height = new Int16Array(N * N)
  const idx = (x: number, y: number, z: number) => (y * N + z) * N + x
  for (let x = 0; x < N; x++) {
    for (let z = 0; z < N; z++) {
      const e = n(x / 36, z / 36) * 1.0 + n(x / 14 + 50, z / 14 + 50) * 0.45 + n(x / 6 + 90, z / 6 + 90) * 0.12
      const h = Math.round(7 + Math.pow(e / 1.57, 1.35) * 30)
      height[z * N + x] = h
      for (let y = 0; y <= h; y++) {
        let id = y < h - 3 ? STONE : y < h ? DIRT : GRASS
        if (h <= SEA + 1 && y >= h - 2) id = SAND
        cells[idx(x, y, z)] = id
      }
      for (let w = h + 1; w <= SEA; w++) cells[idx(x, w, z)] = WATER
    }
  }
  let r = seed
  const rand = () => {
    r = (r * 48271) % 2147483647
    return r / 2147483647
  }
  const trees: [number, number][] = []
  for (let t = 0; t < 260; t++) {
    const tx = 3 + Math.floor(rand() * (N - 6))
    const tz = 3 + Math.floor(rand() * (N - 6))
    const g = height[tz * N + tx]
    if (g <= SEA + 1 || g > H - 10) continue
    if (trees.some((q) => Math.abs(q[0] - tx) < 6 && Math.abs(q[1] - tz) < 6)) continue
    trees.push([tx, tz])
    const trunk = 4 + Math.floor(rand() * 2)
    for (let ly = trunk - 1; ly <= trunk + 2; ly++) {
      const rad = ly >= trunk + 1 ? 1 : 2
      for (let lx = -rad; lx <= rad; lx++) {
        for (let lz = -rad; lz <= rad; lz++) {
          if (rad === 2 && Math.abs(lx) === 2 && Math.abs(lz) === 2 && rand() < 0.6) continue
          if (ly === trunk + 2 && Math.abs(lx) + Math.abs(lz) > 1) continue
          const k = idx(tx + lx, g + ly, tz + lz)
          if (!cells[k]) cells[k] = LEAVES
        }
      }
    }
    for (let ty = 1; ty <= trunk; ty++) cells[idx(tx, g + ty, tz)] = LOG
  }
  for (let b = 0; b < 30; b++) {
    const bx = 2 + Math.floor(rand() * (N - 4))
    const bz = 2 + Math.floor(rand() * (N - 4))
    const bg = height[bz * N + bx]
    if (bg > SEA + 2) cells[idx(bx, bg, bz)] = rand() < 0.5 ? COBBLE : STONE
  }
  return { N, H, SEA, cells, height, idx }
}

export function createRayView(world: RayWorld): RayView {
  const N = world.N
  let best: { x: number; z: number; h: number } | null = null
  for (let z = 4; z < 16; z++) {
    for (let x = 16; x < N - 16; x++) {
      const h = world.height[z * N + x]
      if (h < world.H - 14 && (!best || h > best.h)) best = { x, z, h }
    }
  }
  if (!best) best = { x: Math.floor(N / 2), z: 8, h: world.SEA + 2 }
  for (let y = 0; y < world.H; y++) {
    for (let dz = -8; dz <= 12; dz++) {
      for (let dx = -9; dx <= 9; dx++) {
        const cx = best.x + dx
        const cz = best.z + dz
        if (cx < 0 || cz < 0 || cx >= N || cz >= N) continue
        const k = world.idx(cx, y, cz)
        if (world.cells[k] === LOG || world.cells[k] === LEAVES) world.cells[k] = AIR
      }
    }
  }
  const yaw = Math.atan2(N / 2 - best.x, N * 0.7 - best.z)
  return { x: best.x + 0.5, y: best.h + 7.5, z: best.z + 0.5, yaw, pitch: -0.3, fov: 72, far: 125 }
}

type Rgba = [number, number, number, number]

function sample(tex: LoadedTexture, u: number, v: number, out: Rgba) {
  const px = Math.min(15, Math.max(0, Math.floor(u * 16)))
  const py = Math.min(15, Math.max(0, Math.floor(v * 16)))
  const i = (py * 16 + px) * 4
  out[0] = tex.data[i]
  out[1] = tex.data[i + 1]
  out[2] = tex.data[i + 2]
  out[3] = tex.data[i + 3]
  return out
}

function tint(c: Rgba, t: number[]) {
  c[0] = (c[0] * t[0]) / 255
  c[1] = (c[1] * t[1]) / 255
  c[2] = (c[2] * t[2]) / 255
}

function faceColor(P: RayPixels, id: number, face: number, u: number, v: number, out: Rgba) {
  if (id === GRASS) {
    if (face === 1) {
      sample(P.grass_block_top, u, v, out)
      tint(out, TINT.grass)
      return out
    }
    if (face === 0) return sample(P.dirt, u, v, out)
    sample(P.grass_block_side, u, v, out)
    const o = sample(P.grass_block_side_overlay, u, v, [0, 0, 0, 0])
    if (o[3] > 0) {
      tint(o, TINT.grass)
      out[0] = o[0]
      out[1] = o[1]
      out[2] = o[2]
    }
    return out
  }
  if (id === DIRT) return sample(P.dirt, u, v, out)
  if (id === STONE) return sample(P.stone, u, v, out)
  if (id === SAND) return sample(P.sand, u, v, out)
  if (id === COBBLE) return sample(P.cobblestone, u, v, out)
  if (id === LOG) return sample(face === 1 || face === 0 ? P.oak_log_top : P.oak_log, u, v, out)
  if (id === LEAVES) {
    sample(P.oak_leaves, u, v, out)
    tint(out, TINT.foliage)
    return out
  }
  if (id === WATER) {
    sample(P.water_still, u, v, out)
    tint(out, TINT.water)
    return out
  }
  out[0] = out[1] = out[2] = 255
  out[3] = 255
  return out
}

export type RayHandle = { draw: () => void; destroy: () => void }

export function createRayScene(canvas: HTMLCanvasElement, world: RayWorld, view: RayView): RayHandle {
  const ctx = canvas.getContext("2d")
  const N = world.N
  const H = world.H
  const cells = world.cells

  function at(x: number, y: number, z: number) {
    if (x < 0 || z < 0 || x >= N || z >= N || y < 0) return STONE
    if (y >= H) return AIR
    return cells[(y * N + z) * N + x]
  }

  function opaque(x: number, y: number, z: number) {
    const c = at(x, y, z)
    return c !== AIR && c !== WATER && c !== LEAVES
  }

  function ao(x: number, y: number, z: number, face: number, u: number, v: number) {
    let nx = 0
    let ny = 0
    let nz = 0
    if (face === 1) ny = 1
    else if (face === 0) ny = -1
    else if (face === 2) nz = -1
    else if (face === 3) nz = 1
    else if (face === 4) nx = -1
    else nx = 1
    let ax: number, ay: number, az: number, bx: number, by: number, bz: number
    if (ny) {
      ax = 1
      ay = 0
      az = 0
      bx = 0
      by = 0
      bz = 1
    } else if (nz) {
      ax = 1
      ay = 0
      az = 0
      bx = 0
      by = 1
      bz = 0
    } else {
      ax = 0
      ay = 0
      az = 1
      bx = 0
      by = 1
      bz = 0
    }
    const cx = x + nx
    const cy = y + ny
    const cz = z + nz
    const corner = (sa: number, sb: number) => {
      const s1 = opaque(cx + ax * sa, cy + ay * sa, cz + az * sa) ? 1 : 0
      const s2 = opaque(cx + bx * sb, cy + by * sb, cz + bz * sb) ? 1 : 0
      const c = opaque(cx + ax * sa + bx * sb, cy + ay * sa + by * sb, cz + az * sa + bz * sb) ? 1 : 0
      const level = s1 && s2 ? 0 : 3 - s1 - s2 - c
      return [0.5, 0.6, 0.8, 1][level]
    }
    const c00 = corner(-1, -1)
    const c10 = corner(1, -1)
    const c01 = corner(-1, 1)
    const c11 = corner(1, 1)
    return (c00 * (1 - u) + c10 * u) * (1 - v) + (c01 * (1 - u) + c11 * u) * v
  }

  function render(P: RayPixels) {
    if (!ctx) return
    const cw = canvas.clientWidth
    const ch = canvas.clientHeight
    if (!cw || !ch) return
    const scale = Math.min(1, 900 / cw)
    const W = Math.max(1, Math.round(cw * scale))
    const Hh = Math.max(1, Math.round(ch * scale))
    canvas.width = W
    canvas.height = Hh
    const img = ctx.createImageData(W, Hh)
    const data = img.data
    const yaw = view.yaw
    const pitch = view.pitch
    const fwd = [Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)]
    const right = [Math.cos(yaw), 0, -Math.sin(yaw)]
    let up = [
      fwd[1] * right[2] - fwd[2] * right[1],
      fwd[2] * right[0] - fwd[0] * right[2],
      fwd[0] * right[1] - fwd[1] * right[0],
    ]
    if (up[1] < 0) up = [-up[0], -up[1], -up[2]]
    const tanF = Math.tan(((view.fov || 70) * Math.PI) / 360)
    const aspect = W / Hh
    const raw = view.sun || [-0.55, 0.62, 0.56]
    const sl = Math.hypot(raw[0], raw[1], raw[2])
    const sun = [raw[0] / sl, raw[1] / sl, raw[2] / sl]
    let su = [sun[2], 0, -sun[0]]
    const sul = Math.hypot(su[0], su[2]) || 1
    su = [su[0] / sul, 0, su[2] / sul]
    const sv = [
      sun[1] * su[2] - sun[2] * su[1],
      sun[2] * su[0] - sun[0] * su[2],
      sun[0] * su[1] - sun[1] * su[0],
    ]
    const far = view.far || 110
    const fogStart = far * 0.55
    const cloudY = H + 26
    const c: Rgba = [0, 0, 0, 0]
    const ox = view.x
    const oy = view.y
    const oz = view.z
    const clouds = P.clouds
    const sunTex = P.sun

    function skyColor(dx: number, dy: number, dz: number, out: number[]) {
      const t = Math.max(0, Math.min(1, dy * 1.8 + 0.08))
      out[0] = FOG[0] + (SKY[0] - FOG[0]) * t
      out[1] = FOG[1] + (SKY[1] - FOG[1]) * t
      out[2] = FOG[2] + (SKY[2] - FOG[2]) * t
      const d = dx * sun[0] + dy * sun[1] + dz * sun[2]
      if (d > 0.95) {
        const a = (dx * su[0] + dz * su[2]) / d
        const b = (dx * sv[0] + dy * sv[1] + dz * sv[2]) / d
        const size = 0.11
        const uu = 0.5 + a / (2 * size)
        const vv = 0.5 - b / (2 * size)
        if (uu > 0 && uu < 1 && vv > 0 && vv < 1) {
          const i = (Math.floor(vv * sunTex.h) * sunTex.w + Math.floor(uu * sunTex.w)) * 4
          out[0] = Math.min(255, out[0] + sunTex.data[i])
          out[1] = Math.min(255, out[1] + sunTex.data[i + 1])
          out[2] = Math.min(255, out[2] + sunTex.data[i + 2])
        }
      }
      if (dy > 0.01) {
        const tt = (cloudY - oy) / dy
        const cx = ox + dx * tt + (view.cloudShift || 0)
        const cz = oz + dz * tt
        const px = Math.floor(cx / 12) & 255
        const pz = Math.floor(cz / 12) & 255
        const ci = (pz * 256 + px) * 4
        if (clouds.data[ci + 3] > 0) {
          const fade = Math.max(0, 1 - tt / 700) * 0.85
          out[0] += (250 - out[0]) * fade
          out[1] += (252 - out[1]) * fade
          out[2] += (255 - out[2]) * fade
        }
      }
      return out
    }

    const sky = [0, 0, 0]
    for (let py = 0; py < Hh; py++) {
      const sy = (1 - (2 * (py + 0.5)) / Hh) * tanF
      for (let px = 0; px < W; px++) {
        const sx = ((2 * (px + 0.5)) / W - 1) * tanF * aspect
        let dx = fwd[0] + right[0] * sx + up[0] * sy
        let dy = fwd[1] + right[1] * sx + up[1] * sy
        let dz = fwd[2] + right[2] * sx + up[2] * sy
        const dl = Math.hypot(dx, dy, dz)
        dx /= dl
        dy /= dl
        dz /= dl
        let x = Math.floor(ox)
        let y = Math.floor(oy)
        let z = Math.floor(oz)
        const stepX = dx > 0 ? 1 : -1
        const stepY = dy > 0 ? 1 : -1
        const stepZ = dz > 0 ? 1 : -1
        const tdx = Math.abs(1 / dx)
        const tdy = Math.abs(1 / dy)
        const tdz = Math.abs(1 / dz)
        let tmx = (dx > 0 ? x + 1 - ox : ox - x) * tdx
        let tmy = (dy > 0 ? y + 1 - oy : oy - y) * tdy
        let tmz = (dz > 0 ? z + 1 - oz : oz - z) * tdz
        let t = 0
        let face = -1
        let r = 0
        let g = 0
        let b = 0
        let keep = 1
        let done = false
        let waterSeen = false
        while (t < far) {
          if (tmx < tmy && tmx < tmz) {
            x += stepX
            t = tmx
            tmx += tdx
            face = dx > 0 ? 4 : 5
          } else if (tmy < tmz) {
            y += stepY
            t = tmy
            tmy += tdy
            face = dy > 0 ? 0 : 1
          } else {
            z += stepZ
            t = tmz
            tmz += tdz
            face = dz > 0 ? 2 : 3
          }
          if (y >= H && dy > 0) break
          if (x < 0 || z < 0 || x >= N || z >= N) break
          const id = at(x, y, z)
          if (id === AIR) continue
          if (id === WATER && waterSeen) continue
          const hx = ox + dx * t
          const hy = oy + dy * t
          const hz = oz + dz * t
          let u: number
          let v: number
          if (face === 0 || face === 1) {
            u = hx - x
            v = hz - z
          } else if (face === 2 || face === 3) {
            u = hx - x
            v = 1 - (hy - y)
            if (face === 2) u = 1 - u
          } else {
            u = hz - z
            v = 1 - (hy - y)
            if (face === 5) u = 1 - u
          }
          if (id === WATER && face === 1) {
            v = hz - z
            u = hx - x
          }
          faceColor(P, id, face, u, v, c)
          if (c[3] === 0) continue
          const shade = face === 1 ? 1 : face === 0 ? 0.5 : face === 2 || face === 3 ? 0.8 : 0.6
          const aoU = face === 0 || face === 1 ? hx - x : face === 2 || face === 3 ? hx - x : hz - z
          const aoV = face === 0 || face === 1 ? hz - z : hy - y
          const light = shade * ao(x, y, z, face, aoU, aoV)
          const fog = Math.max(0, Math.min(1, (t - fogStart) / (far - fogStart)))
          let cr = c[0] * light
          let cg = c[1] * light
          let cb = c[2] * light
          skyColor(dx, dy, dz, sky)
          cr += (sky[0] - cr) * fog
          cg += (sky[1] - cg) * fog
          cb += (sky[2] - cb) * fog
          if (id === WATER) {
            waterSeen = true
            const a = 0.62
            r += cr * a * keep
            g += cg * a * keep
            b += cb * a * keep
            keep *= 1 - a
            continue
          }
          r += cr * keep
          g += cg * keep
          b += cb * keep
          keep = 0
          done = true
          break
        }
        if (!done) {
          skyColor(dx, dy, dz, sky)
          r += sky[0] * keep
          g += sky[1] * keep
          b += sky[2] * keep
        }
        const o = (py * W + px) * 4
        data[o] = r
        data[o + 1] = g
        data[o + 2] = b
        data[o + 3] = 255
      }
    }
    ctx.putImageData(img, 0, 0)
  }

  let lastSize = ""
  function draw() {
    const key = canvas.clientWidth + "x" + canvas.clientHeight
    if (key === lastSize) return
    lastSize = key
    loadAll().then(render)
  }

  let watcher: ResizeObserver | null = null
  if (typeof ResizeObserver !== "undefined") {
    watcher = new ResizeObserver(() => draw())
    watcher.observe(canvas)
  }
  draw()

  return {
    draw() {
      lastSize = ""
      draw()
    },
    destroy() {
      watcher?.disconnect()
      watcher = null
    },
  }
}
