export type EasingName =
  | "linear" | "sine" | "quad" | "cubic" | "quart" | "quint"
  | "expo" | "circ" | "back" | "elastic" | "bounce"

export type Direction = "in" | "out" | "inOut"

const CURVES: Record<EasingName, (t: number) => number> = {
  linear: (t) => t,
  sine: (t) => 1 - Math.cos((t * Math.PI) / 2),
  quad: (t) => t * t,
  cubic: (t) => t ** 3,
  quart: (t) => t ** 4,
  quint: (t) => t ** 5,
  expo: (t) => (t === 0 ? 0 : 2 ** (10 * (t - 1))),
  circ: (t) => 1 - Math.sqrt(1 - t * t),
  back: (t) => 2.70158 * t ** 3 - 1.70158 * t * t,
  elastic: (t) =>
    t === 0 || t === 1 ? t : -(2 ** (10 * t - 10)) * Math.sin((t * 10 - 10.75) * ((2 * Math.PI) / 3)),
  bounce: (t) => {
    let u = 1 - t
    let v: number
    if (u < 1 / 2.75) v = 7.5625 * u * u
    else if (u < 2 / 2.75) v = 7.5625 * (u -= 1.5 / 2.75) * u + 0.75
    else if (u < 2.5 / 2.75) v = 7.5625 * (u -= 2.25 / 2.75) * u + 0.9375
    else v = 7.5625 * (u -= 2.625 / 2.75) * u + 0.984375
    return 1 - v
  },
}

export const EASING_NAMES = Object.keys(CURVES) as EasingName[]

export function ease(name: EasingName, direction: Direction, t: number) {
  const curve = CURVES[name] ?? CURVES.linear
  if (direction === "in") return curve(t)
  if (direction === "out") return 1 - curve(1 - t)
  return t < 0.5 ? curve(t * 2) / 2 : 1 - curve(2 - t * 2) / 2
}
