<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Slider from "../ui/Slider.svelte"
  import Stage from "../ui/Stage.svelte"

  let xs = $state(0.5)
  let xo = $state(0)
  let ys = $state(0.5)
  let yo = $state(0)
  let ax = $state(0.5)
  let ay = $state(0.5)

  const left = $derived(`calc(${xs * 100}% + ${xo}px)`)
  const top = $derived(`calc(${ys * 100}% + ${yo}px)`)

  const source = $derived(
    `hud:add("Frame", {\n    position = udim2(${xs}, ${xo}, ${ys}, ${yo}),\n    size = udim2(0, 96, 0, 40),\n    anchorX = ${ax}, anchorY = ${ay},\n})`,
  )
</script>

<Demo label="Position">
  <Stage>
    <div class="demo-screen">
      <div class="demo-guide demo-guide-x" style="left: {left}"></div>
      <div class="demo-guide demo-guide-y" style="top: {top}"></div>
      <div class="demo-box" style="left: {left}; top: {top}; transform: translate({-ax * 100}%, {-ay * 100}%)">
        Frame
      </div>
    </div>
  </Stage>

  <div class="demo-controls">
    <Slider label="position x scale" min={0} max={1} step={0.05} bind:value={xs} />
    <Slider label="position x offset" min={-60} max={60} step={2} bind:value={xo} />
    <Slider label="position y scale" min={0} max={1} step={0.05} bind:value={ys} />
    <Slider label="position y offset" min={-60} max={60} step={2} bind:value={yo} />
    <Slider label="anchorX" min={0} max={1} step={0.5} bind:value={ax} />
    <Slider label="anchorY" min={0} max={1} step={0.5} bind:value={ay} />
  </div>

  <CodePanel {source} />
</Demo>
