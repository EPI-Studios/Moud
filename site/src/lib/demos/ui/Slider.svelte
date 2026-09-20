<script lang="ts">
  let {
    label,
    min,
    max,
    step = 1,
    value = $bindable(),
    format = (v: number) => String(v),
  }: {
    label: string
    min: number
    max: number
    step?: number
    value: number
    format?: (value: number) => string
  } = $props()
</script>

<label class="demo-slider">
  <span class="demo-slider-name">{label}</span>
  <input type="range" {min} {max} {step} bind:value />
  <span class="demo-slider-value">{format(value)}</span>
</label>
