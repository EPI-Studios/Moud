<script lang="ts">
  import { renderPost } from "$lib/render"
  import Icon from "./Icon.svelte"

  let {
    name = "body",
    value = $bindable(""),
    label,
    placeholder,
    hint,
    submit,
    action,
    small = false,
    fields = {},
  }: {
    name?: string
    value?: string
    label: string
    placeholder: string
    hint: string
    submit: string
    action?: string
    small?: boolean
    fields?: Record<string, string>
  } = $props()

  let tab = $state<"write" | "preview">("write")
  let field = $state<HTMLTextAreaElement | null>(null)
  let uploading = $state(false)
  let problem = $state("")

  async function send(files: FileList | null) {
    if (!files?.length) return
    uploading = true
    problem = ""

    for (const file of files) {
      const payload = new FormData()
      payload.set("file", file)
      const answer = await fetch("/api/upload", { method: "POST", body: payload })
      const result = await answer.json()
      if (result.error) {
        problem = result.error
        continue
      }
      value = value ? `${value.trimEnd()}\n\n${result.url}\n` : `${result.url}\n`
    }

    uploading = false
    tab = "write"
  }

  export function focus() {
    field?.focus()
  }

  export function insert(text: string) {
    value = value ? `${value.trimEnd()}\n\n${text}\n\n` : `${text}\n\n`
    tab = "write"
    field?.focus()
  }
</script>

<form class="composer" method="POST" {action}>
  {#each Object.entries(fields) as [key, value] (key)}
    <input type="hidden" name={key} {value} />
  {/each}

  <div class="composer-head">
    <label for={name}>{label}</label>
    <div class="composer-tabs">
      <button type="button" class:active={tab === "write"} onclick={() => (tab = "write")}>
        Write
      </button>
      <button type="button" class:active={tab === "preview"} onclick={() => (tab = "preview")}>
        Preview
      </button>
    </div>
  </div>

  {#if tab === "write"}
    <textarea
      bind:this={field}
      bind:value
      class="field"
      class:field-small={small}
      id={name}
      {name}
      {placeholder}
      required
      onpaste={(event) => {
        const files = event.clipboardData?.files
        if (files?.length) {
          event.preventDefault()
          send(files)
        }
      }}
      ondragover={(event) => event.preventDefault()}
      ondrop={(event) => {
        event.preventDefault()
        send(event.dataTransfer?.files ?? null)
      }}
    ></textarea>
  {:else}
    <div class="post-body composer-preview">
      {#if value.trim()}
        {@html renderPost(value)}
      {:else}
        <p class="composer-hint">Nothing to preview yet.</p>
      {/if}
    </div>
    <textarea {name} value={value} hidden></textarea>
  {/if}

  <div class="composer-foot">
    <span class="composer-hint">
      {#if uploading}
        Uploading…
      {:else if problem}
        {problem}
      {:else}
        {hint} Paste or drop an image to upload it.
      {/if}
    </span>
    <button class="button button-primary" type="submit">
      <Icon name="pencil-simple" size="15px" />
      {submit}
    </button>
  </div>
</form>
