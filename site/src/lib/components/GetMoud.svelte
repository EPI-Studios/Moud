<script lang="ts">
  import Icon from "./Icon.svelte"
  import modrinth from "$lib/assets/modrinth.svg?raw"

  const MODRINTH = "https://modrinth.com/mod/moud"
  const DISCORD = "https://discord.gg/ruNs3cMqKB"
</script>

<div class="get-moud">
  <a class="button button-modrinth" href={MODRINTH} target="_blank" rel="noopener noreferrer">
    {@html modrinth}
    Download on Modrinth
  </a>
  <a class="button button-discord" href={DISCORD} target="_blank" rel="noopener noreferrer">
    <Icon name="discord-logo" size="18px" />
    Join the Discord
  </a>
</div>
