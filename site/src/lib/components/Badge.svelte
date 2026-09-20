<script lang="ts">
  import Icon from "./Icon.svelte"

  let {
    kind,
    label,
    large = false,
  }: { kind: "staff" | "pin" | "lock" | "solved"; label: string; large?: boolean } = $props()

  const ICON = { staff: "wrench", pin: "push-pin", lock: "lock-simple", solved: "check-circle" }
</script>

<span class="badge badge-{kind}" class:badge-lg={large} title={label}>
  <Icon name={ICON[kind]} size={large ? "15px" : "12px"} />
  <span class="sr-only">{label}</span>
</span>
