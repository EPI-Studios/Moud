const UNITS: [number, Intl.RelativeTimeFormatUnit][] = [
  [60, "second"],
  [60, "minute"],
  [24, "hour"],
  [7, "day"],
  [4.348, "week"],
  [12, "month"],
]

export function when(date: Date) {
  const format = new Intl.RelativeTimeFormat("en", { numeric: "auto" })
  let value = (date.getTime() - Date.now()) / 1000
  for (const [step, unit] of UNITS) {
    if (Math.abs(value) < step) return format.format(Math.round(value), unit)
    value /= step
  }
  return format.format(Math.round(value), "year")
}

export function exact(date: Date) {
  return date.toISOString().slice(0, 16).replace("T", " ")
}
