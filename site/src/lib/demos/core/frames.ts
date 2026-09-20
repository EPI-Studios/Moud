import { onMount } from "svelte"

export function whileVisible(node: () => Element | null, frame: (now: number) => void) {
  onMount(() => {
    const host = node()
    if (!host) return

    let visible = typeof IntersectionObserver === "undefined"
    let running = false
    let handle = 0

    const tick = (now: number) => {
      if (!visible) {
        running = false
        return
      }
      frame(now)
      handle = requestAnimationFrame(tick)
    }

    const start = () => {
      if (running) return
      running = true
      handle = requestAnimationFrame(tick)
    }

    let watcher: IntersectionObserver | null = null
    if (visible) {
      start()
    } else {
      watcher = new IntersectionObserver((entries) => {
        visible = entries[entries.length - 1].isIntersecting
        if (visible) start()
      })
      watcher.observe(host)
    }

    return () => {
      visible = false
      cancelAnimationFrame(handle)
      watcher?.disconnect()
    }
  })
}
