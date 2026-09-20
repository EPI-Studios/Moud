<script lang="ts">
  let { text, label }: { text: string; label: string } = $props()
</script>

<span class="badge badge-text" title={label}>{text}<span class="sr-only">{label}</span></span>
