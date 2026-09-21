import satori from "satori"
import { Resvg } from "@resvg/resvg-js"
import boldFont from "./fonts/gabarito-700.ttf?inline"
import mediumFont from "./fonts/gabarito-500.ttf?inline"
import logoImage from "../../../static/logo.png?inline"

function bytesOf(dataUrl: string) {
  return Buffer.from(dataUrl.slice(dataUrl.indexOf(",") + 1), "base64")
}

const COLOURS = {
  bg: "#181818",
  panel: "#1f1f1f",
  line: "#2a2a2a",
  main: "#f0f0f0",
  text: "#d0d0d0",
  muted: "#9a9a9a",
  light: "#6a6a6a",
  accent: "#c6c6c6",
  line2: "#383838",
}

type Font = { name: string; data: Buffer; weight: 400 | 500 | 700; style: "normal" }

let fonts: Font[] | null = null

async function loadFonts(): Promise<Font[]> {
  if (fonts) return fonts
  fonts = [
    { name: "Gabarito", data: bytesOf(boldFont), weight: 700, style: "normal" },
    { name: "Gabarito", data: bytesOf(mediumFont), weight: 500, style: "normal" },
  ]
  return fonts
}

async function dataUri(url: string) {
  try {
    const answer = await fetch(url, {
      headers: { "user-agent": "MoudForum/1.0 (+https://moud.epistudios.fr)" },
      signal: AbortSignal.timeout(3000),
    })
    if (!answer.ok) return null
    const type = answer.headers.get("content-type") ?? "image/png"
    const bytes = Buffer.from(await answer.arrayBuffer())
    return `data:${type};base64,${bytes.toString("base64")}`
  } catch {
    return null
  }
}

function logo() {
  return logoImage
}

type Box = {
  type: string
  props: Record<string, unknown> & { children?: unknown }
}

function box(type: string, style: Record<string, unknown>, children?: unknown): Box {
  return { type, props: { style, children } }
}

function image(src: string, size: number, radius = 0): Box {
  return {
    type: "img",
    props: { src, width: size, height: size, style: { borderRadius: `${radius}px` } },
  }
}

export type CardInput = {
  eyebrow: string
  title: string
  blurb?: string
  author?: { name: string; head: string | null }
  facts?: string[]
}

export async function card(input: CardInput) {
  const head = input.author?.head ? await dataUri(input.author.head) : null
  const size = input.title.length > 58 ? 64 : input.title.length > 30 ? 76 : 88

  const tree = box(
    "div",
    {
      width: "1200px",
      height: "630px",
      display: "flex",
      flexDirection: "column",
      background: COLOURS.bg,
      padding: "62px 76px",
      fontFamily: "Gabarito",
    },
    [
      box("div", { display: "flex", alignItems: "center", gap: "15px" }, [
        image(logo(), 42),
        box("div", { fontSize: "30px", fontWeight: 700, color: COLOURS.main }, "Moud"),
        box("div", { width: "1px", height: "26px", background: COLOURS.line, margin: "0 6px" }),
        box("div", { fontSize: "27px", fontWeight: 500, color: COLOURS.muted }, input.eyebrow),
      ]),

      box(
        "div",
        {
          display: "flex",
          flexDirection: "column",
          flexGrow: 1,
          justifyContent: "center",
          gap: "24px",
          paddingRight: "40px",
        },
        [
          box(
            "div",
            {
              fontSize: `${size}px`,
              fontWeight: 700,
              lineHeight: 1.1,
              letterSpacing: "-0.025em",
              color: COLOURS.main,
              display: "block",
              lineClamp: 3,
            },
            input.title,
          ),
          input.blurb
            ? box(
                "div",
                {
                  fontSize: "29px",
                  fontWeight: 500,
                  lineHeight: 1.45,
                  color: COLOURS.muted,
                  display: "block",
                  lineClamp: 2,
                },
                input.blurb,
              )
            : null,
        ].filter(Boolean),
      ),

      box(
        "div",
        {
          display: "flex",
          alignItems: "center",
          gap: "14px",
          paddingTop: "26px",
          borderTop: `1px solid ${COLOURS.line}`,
        },
        [
          head ? image(head, 46, 6) : null,
          input.author
            ? box("div", { fontSize: "26px", fontWeight: 700, color: COLOURS.text }, input.author.name)
            : null,
          ...(input.facts ?? []).flatMap((fact, index) => [
            index === 0 && !input.author
              ? null
              : box("div", { fontSize: "24px", color: COLOURS.line2 }, "\u00b7"),
            box("div", { fontSize: "25px", fontWeight: 500, color: COLOURS.light }, fact),
          ]),
        ].filter(Boolean),
      ),
    ],
  )

  const svg = await satori(tree as never, {
    width: 1200,
    height: 630,
    fonts: await loadFonts(),
  })

  return new Resvg(svg, { fitTo: { mode: "width", value: 1200 } }).render().asPng()
}
