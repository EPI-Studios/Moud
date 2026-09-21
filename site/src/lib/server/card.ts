import satori from "satori"
import { Resvg } from "@resvg/resvg-js"
import boldFont from "./fonts/gabarito-700.ttf?inline"
import mediumFont from "./fonts/gabarito-500.ttf?inline"
import monoFont from "./fonts/jetbrains-400.ttf?inline"
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
}

type Font = { name: string; data: Buffer; weight: 400 | 500 | 700; style: "normal" }

let fonts: Font[] | null = null

async function loadFonts(): Promise<Font[]> {
  if (fonts) return fonts
  fonts = [
    { name: "Gabarito", data: bytesOf(boldFont), weight: 700, style: "normal" },
    { name: "Gabarito", data: bytesOf(mediumFont), weight: 500, style: "normal" },
    { name: "JetBrains Mono", data: bytesOf(monoFont), weight: 400, style: "normal" },
  ]
  return fonts
}

async function dataUri(url: string) {
  try {
    const answer = await fetch(url, { signal: AbortSignal.timeout(3000) })
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
  const mark = logo()
  const head = input.author?.head ? await dataUri(input.author.head) : null

  const tree = box(
    "div",
    {
      width: "1200px",
      height: "630px",
      display: "flex",
      flexDirection: "column",
      justifyContent: "space-between",
      background: COLOURS.bg,
      padding: "64px 72px",
      fontFamily: "Gabarito",
      borderLeft: `16px solid ${COLOURS.accent}`,
    },
    [
      box("div", { display: "flex", alignItems: "center", gap: "16px" }, [
        image(mark, 44),
        box(
          "div",
          { fontSize: "30px", fontWeight: 700, color: COLOURS.main, letterSpacing: "-0.01em" },
          "Moud",
        ),
        box(
          "div",
          {
            marginLeft: "10px",
            padding: "6px 14px",
            borderRadius: "999px",
            background: COLOURS.panel,
            border: `1px solid ${COLOURS.line}`,
            fontFamily: "JetBrains Mono",
            fontSize: "19px",
            color: COLOURS.muted,
          },
          input.eyebrow,
        ),
      ]),

      box("div", { display: "flex", flexDirection: "column", gap: "22px" }, [
        box(
          "div",
          {
            fontSize: input.title.length > 60 ? "62px" : "76px",
            fontWeight: 700,
            lineHeight: 1.08,
            letterSpacing: "-0.02em",
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
                fontSize: "28px",
                fontWeight: 500,
                lineHeight: 1.45,
                color: COLOURS.muted,
                display: "block",
                lineClamp: 2,
              },
              input.blurb,
            )
          : null,
      ]),

      box(
        "div",
        {
          display: "flex",
          alignItems: "center",
          gap: "18px",
          paddingTop: "28px",
          borderTop: `1px solid ${COLOURS.line}`,
        },
        [
          head ? image(head, 52, 8) : null,
          input.author
            ? box(
                "div",
                { fontSize: "26px", fontWeight: 700, color: COLOURS.text },
                input.author.name,
              )
            : null,
          ...(input.facts ?? []).map((fact) =>
            box(
              "div",
              {
                fontFamily: "JetBrains Mono",
                fontSize: "22px",
                color: COLOURS.light,
              },
              `· ${fact}`,
            ),
          ),
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
