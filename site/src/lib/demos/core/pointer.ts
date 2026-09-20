export type Point = { x: number; y: number }

export function pointIn(node: Element, event: PointerEvent | MouseEvent, width: number, height: number): Point {
  const box = node.getBoundingClientRect()
  return {
    x: ((event.clientX - box.left) / box.width) * width,
    y: ((event.clientY - box.top) / box.height) * height,
  }
}

export function clamp(value: number, low: number, high: number) {
  return Math.max(low, Math.min(high, value))
}
