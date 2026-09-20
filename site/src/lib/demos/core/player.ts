import { MC_TEXTURES } from "./mcTextures"

const BUNDLE = "https://cdn.jsdelivr.net/npm/skinview3d@3.4.2/bundles/skinview3d.bundle.js"

export const STEVE_SKIN = MC_TEXTURES["entity/player/wide/steve"]
const FLOOR_TEXTURE = MC_TEXTURES["block/grass_block_top"]

export type Vec3 = {
  x: number
  y: number
  z: number
  set: (x: number, y: number, z: number) => void
  clone: () => Vec3
}

export type Euler = { x: number; y: number; z: number; set: (x: number, y: number, z: number) => void }

export type Object3d = {
  visible: boolean
  position: Vec3
  rotation: Euler
  getWorldPosition: (target: Vec3) => Vec3
}

type TextureLike = {
  needsUpdate: boolean
  magFilter: number
  minFilter: number
  wrapS: number
  wrapT: number
  repeat: { set: (x: number, y: number) => void }
}

type MeshLike = Object3d & {
  constructor: unknown
  geometry: { constructor: unknown }
  material: { constructor: unknown; map: TextureLike & { constructor: unknown } }
}

export type SkinPart = Object3d & { innerLayer: MeshLike; outerLayer: Object3d }

export type PlayerSkin = {
  head: SkinPart
  body: SkinPart
  rightArm: SkinPart
  leftArm: SkinPart
  rightLeg: SkinPart
  leftLeg: SkinPart
}

export type SkinViewer = {
  playerObject: Object3d & { skin: PlayerSkin }
  camera: Object3d & { lookAt: (x: number, y: number, z: number) => void }
  controls: { target: Vec3; update: () => void }
  scene: { add: (object: unknown) => void }
  globalLight: { intensity: number }
  cameraLight: { intensity: number }
  autoRotate: boolean
  animation: unknown
  background: unknown
  renderPaused: boolean
  setSize: (width: number, height: number) => void
  dispose?: () => void
}

type SkinViewerOptions = {
  canvas: HTMLCanvasElement
  width: number
  height: number
  skin: string
  fov: number
  zoom: number
  enableControls: boolean
}

type SkinView3d = { SkinViewer: new (options: SkinViewerOptions) => SkinViewer }

const scope = globalThis as typeof globalThis & { skinview3d?: SkinView3d }

let loading: Promise<SkinView3d | null> | null = null

function loadSkinview3d() {
  if (scope.skinview3d) return Promise.resolve(scope.skinview3d)
  if (!loading) {
    loading = new Promise<SkinView3d | null>((resolve) => {
      const script = document.createElement("script")
      script.src = BUNDLE
      script.onload = () => resolve(scope.skinview3d ?? null)
      script.onerror = () => resolve(null)
      document.head.appendChild(script)
    })
  }
  return loading
}

export function addPlayerFloor(viewer: SkinViewer) {
  const sample = viewer.playerObject.skin.body.innerLayer
  if (!sample || !sample.material || !sample.material.map) return null
  const Mesh = sample.constructor as new (geometry: unknown, material: unknown) => Object3d
  const Box = sample.geometry.constructor as new (x: number, y: number, z: number) => unknown
  const Material = sample.material.constructor as new (options: { map: TextureLike; color: number }) => unknown
  const Texture = sample.material.map.constructor as new (image: HTMLImageElement) => TextureLike
  const image = new Image()
  const texture = new Texture(image)
  image.onload = () => {
    texture.needsUpdate = true
  }
  image.src = FLOOR_TEXTURE
  texture.magFilter = 1003
  texture.minFilter = 1003
  texture.wrapS = texture.wrapT = 1000
  texture.repeat.set(250, 250)
  const material = new Material({ map: texture, color: 0x86b75f })
  const mesh = new Mesh(new Box(2000, 0.2, 2000), material)
  viewer.scene.add(mesh)
  return mesh
}

export function aimPlayer(viewer: SkinViewer, from: [number, number, number], at: [number, number, number]) {
  viewer.controls.target.set(at[0], at[1], at[2])
  viewer.camera.position.set(from[0], from[1], from[2])
  viewer.camera.lookAt(at[0], at[1], at[2])
  viewer.controls.update()
}

export type PlayerViewOptions = { fov?: number; zoom?: number; host?: HTMLElement | null }

export type PlayerHandle = {
  readonly viewer: SkinViewer | null
  readonly ready: Promise<SkinViewer | null>
  destroy: () => void
}

export function createPlayerView(canvas: HTMLCanvasElement, options: PlayerViewOptions = {}): PlayerHandle {
  const host = options.host ?? canvas.parentElement ?? canvas
  let viewer: SkinViewer | null = null
  let size: ResizeObserver | null = null
  let watcher: IntersectionObserver | null = null
  let dead = false

  const ready = loadSkinview3d().then((module) => {
    if (!module || dead) return null
    const view = new module.SkinViewer({
      canvas,
      width: host.clientWidth || 720,
      height: host.clientHeight || 250,
      skin: STEVE_SKIN,
      fov: options.fov ?? 40,
      zoom: options.zoom ?? 0.9,
      enableControls: false,
    })
    view.autoRotate = false
    view.animation = null
    view.background = null
    view.globalLight.intensity = 3
    view.cameraLight.intensity = 0.6
    const skin = view.playerObject.skin
    const parts: (keyof PlayerSkin)[] = ["head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"]
    parts.forEach((part) => {
      if (skin[part] && skin[part].outerLayer) skin[part].outerLayer.visible = false
    })
    if (typeof ResizeObserver !== "undefined") {
      size = new ResizeObserver(() => {
        if (host.clientWidth && host.clientHeight) view.setSize(host.clientWidth, host.clientHeight)
      })
      size.observe(host)
    }
    if (typeof IntersectionObserver !== "undefined") {
      watcher = new IntersectionObserver((entries) => {
        view.renderPaused = !entries[entries.length - 1].isIntersecting
      })
      watcher.observe(host)
    }
    viewer = view
    return view
  })

  return {
    get viewer() {
      return viewer
    },
    ready,
    destroy() {
      dead = true
      size?.disconnect()
      watcher?.disconnect()
      size = null
      watcher = null
      viewer?.dispose?.()
      viewer = null
    },
  }
}
