<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Stage from "../ui/Stage.svelte"
  import Slider from "../ui/Slider.svelte"
  import CodePanel from "../ui/CodePanel.svelte"

  let red = $state(0.35)
  let green = $state(0.42)
  let blue = $state(0.38)

  const bytes = $derived([red, green, blue].map((v) => Math.round(v * 255)))
  const source = $derived(
    "part.color = color(" + red + ", " + green + ", " + blue + ")\n" +
      "-- the same colour Minecraft would call " + bytes.join(", "),
  )
</script>

<Demo label="Colour">
  <Stage>
    <div class="demo-swatch" style="background: rgb({bytes.join(',')})"></div>
  </Stage>

  <div class="demo-controls">
    <Slider label="red" min={0} max={1} step={0.01} bind:value={red} />
    <Slider label="green" min={0} max={1} step={0.01} bind:value={green} />
    <Slider label="blue" min={0} max={1} step={0.01} bind:value={blue} />
  </div>

  <CodePanel {source} />
</Demo>
