<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Note from "../ui/Note.svelte"
  import Slider from "../ui/Slider.svelte"
  import Stage from "../ui/Stage.svelte"

  const NAMES = ["Play", "Settings", "Quit", "Credits", "Shop", "Help"]
  const FRAME = 180

  let count = $state(3)
  let gap = $state(6)
  let pad = $state(10)
  let width = $state<"full" | "100 px">("full")
  let align = $state<"left" | "center" | "right">("left")
  let visible = $state<"true" | "false">("true")
  let screenHeight = $state(290)

  const hideSecond = $derived(visible === "false")
  const innerW = $derived(FRAME - pad * 2)
  const shown = $derived(
    Array.from({ length: count }, (_, index) => index).filter((index) => !(hideSecond && index === 1)),
  )
  const height = $derived(pad * 2 + shown.length * 24 + Math.max(0, shown.length - 1) * gap)
  const top = $derived(Math.round((screenHeight || 290) / 2 - height / 2))
  const buttonW = $derived(width === "full" ? innerW : 100)
  const buttonX = $derived(align === "left" ? 0 : align === "center" ? (innerW - buttonW) / 2 : innerW - buttonW)

  const note = $derived.by(() => {
    const sum =
      `${pad} + ${shown.map(() => "24").join(" + ")}` +
      (shown.length > 1 ? ` + ${shown.length - 1} x ${gap}` : "") +
      ` + ${pad}`
    return (
      `Height: ${sum} = ${height} pixels. ` +
      (hideSecond ? "Settings has visible = false and takes no room. " : "") +
      (width === "full"
        ? `Each button is udim2(1, 0, ...) wide, so it fills the ${innerW} pixels the padding leaves.`
        : "The buttons are 100 pixels wide, so horizontalAlignment moves them inside the frame.")
    )
  })

  const source = $derived.by(() => {
    const lines = [
      'local menu = hud:add("Frame", {',
      "    position = udim2(0, 20, 0.5, 0), anchorY = 0.5,",
      '    size = udim2(0, 180, 0, 0), automaticSize = "y",',
      "})",
      `menu:add("UIListLayout", { padding = udim(0, ${gap})${
        align !== "left" ? `, horizontalAlignment = "${align}"` : ""
      } })`,
      'menu:add("UIPadding", {',
      `    paddingTop = udim(0, ${pad}), paddingBottom = udim(0, ${pad}),`,
      `    paddingLeft = udim(0, ${pad}), paddingRight = udim(0, ${pad}),`,
      "})",
      `for order, label in ipairs({ ${NAMES.slice(0, count)
        .map((name) => `"${name}"`)
        .join(", ")} }) do`,
      `    menu:add("TextButton", { name = label, text = label, layoutOrder = order, size = udim2(${
        width === "full" ? "1, 0" : "0, 100"
      }, 0, 24) })`,
      "end",
    ]
    if (hideSecond && count > 1) lines.push("menu.Settings.visible = false")
    return lines.join("\n")
  })
</script>

<Demo label="List layout">
  <Stage>
    <div class="demo-screen ui-screen ui-screen-tall" bind:clientHeight={screenHeight}>
      <div class="ui-frame" style="left: 20px; top: {top}px; width: {FRAME}px; height: {height}px">
        <div
          class="ui-padding"
          style="left: {pad}px; top: {pad}px; width: {innerW}px; height: {Math.max(0, height - pad * 2)}px"
        >
          {#each shown as index, place (index)}
            <div
              class="ui-button"
              style="left: {buttonX}px; top: {place * (24 + gap)}px; width: {buttonW}px; height: 24px"
            >
              {NAMES[index]}
            </div>
          {/each}
        </div>
      </div>
      <div
        class="ui-measure"
        data-size="{height} px"
        style="left: {20 + FRAME + 8}px; top: {top}px; width: 1px; height: {height}px"
      ></div>
    </div>
  </Stage>

  <div class="demo-controls">
    <Slider label="buttons" min={1} max={6} bind:value={count} />
    <Slider label="padding" min={0} max={16} bind:value={gap} />
    <Slider label="UIPadding" min={0} max={20} bind:value={pad} />
    <Choice label="button width" options={["full", "100 px"] as const} bind:value={width} />
    <Choice label="horizontalAlignment" options={["left", "center", "right"] as const} bind:value={align} />
    <Choice label="Settings.visible" options={["true", "false"] as const} bind:value={visible} />
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
