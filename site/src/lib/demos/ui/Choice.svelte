<script lang="ts" generics="T extends string | number | boolean">
  let {
    label,
    options,
    value = $bindable(),
    labelOf = (option: T) => String(option),
  }: {
    label: string
    options: readonly T[]
    value: T
    labelOf?: (option: T) => string
  } = $props()
</script>

<div class="demo-choice">
  <span class="demo-slider-name">{label}</span>
  <div class="demo-choice-buttons">
    {#each options as option (option)}
      <button
        type="button"
        class="demo-pill"
        aria-pressed={option === value}
        onclick={() => (value = option)}
      >
        {labelOf(option)}
      </button>
    {/each}
  </div>
</div>
