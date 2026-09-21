declare module "virtual:harfbuzz-wasm" {
  const value: string
  export default value
}

declare module "harfbuzzjs/hb.js" {
  const create: (module: Record<string, unknown>) => Promise<unknown>
  export default create
}

declare module "harfbuzzjs/hbjs.js" {
  const bind: (module: unknown) => unknown
  export default bind
}
