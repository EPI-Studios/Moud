import createHarfBuzz from "harfbuzzjs/hb.js"
import hbjs from "harfbuzzjs/hbjs.js"
import encoded from "virtual:harfbuzz-wasm"

const bytes = Buffer.from(encoded, "base64")

export default createHarfBuzz({
  instantiateWasm(imports: WebAssembly.Imports, done: (instance: WebAssembly.Instance) => void) {
    WebAssembly.instantiate(bytes, imports).then((made) => done(made.instance))
  },
}).then(hbjs)
