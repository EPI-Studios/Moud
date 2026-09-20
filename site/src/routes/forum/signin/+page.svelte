<script lang="ts">
  import Icon from "$lib/components/Icon.svelte"

  type Form = { message?: string; found?: { code: string; name: string } } | null

  let { data, form }: { data: { dev: boolean }; form: Form } = $props()
</script>

<svelte:head><title>Sign in | Moud Forum</title></svelte:head>

<main class="forum-shell">
  <nav class="crumbs">
    <a href="/forum">Forum</a>
    <span>Sign in</span>
  </nav>

  <div class="page-head">
    <div>
      <h1>Sign in</h1>
      <p>Your forum account is your Minecraft account. Moud proves it to Mojang for you.</p>
    </div>
  </div>

  {#if form?.message}<p class="form-error">{form.message}</p>{/if}

  {#if form?.found}
    <div class="confirm">
      <img
        class="confirm-bust"
        src="https://visage.surgeplay.com/bust/128/{form.found.name}.png"
        alt=""
      />
      <div>
        <h2>Sign in as <b>{form.found.name}</b>?</h2>
        <p>
          Check that name. If it is not yours, somebody sent you their code and you would be posting
          under their account.
        </p>
        <form method="POST" action="?/confirm">
          <input type="hidden" name="code" value={form.found.code} />
          <button class="button button-primary" type="submit">
            <Icon name="cube" size="15px" />
            Yes, that is me
          </button>
        </form>
      </div>
    </div>
  {:else}
    <ol class="steps">
      <li>
        <span class="step-number">1</span>
        <div>
          <h2>Open Moud in Minecraft</h2>
          <p>In the project hub, click your name in the sidebar, then <b>Get a code</b>.</p>
        </div>
      </li>
      <li>
        <span class="step-number">2</span>
        <div>
          <h2>Type the code here</h2>
          <form class="code-form" method="POST" action="?/check">
            <input
              class="field code-field"
              name="code"
              maxlength="7"
              placeholder="ABC123"
              autocomplete="off"
              spellcheck="false"
              required
            />
            <button class="button button-primary" type="submit">Check it</button>
          </form>
          <p class="composer-hint">The code lasts ten minutes and works once.</p>
        </div>
      </li>
      <li>
        <span class="step-number">3</span>
        <div>
          <h2>Confirm the name</h2>
          <p>We show you which Minecraft account the code belongs to before signing you in.</p>
        </div>
      </li>
    </ol>

    {#if data.dev}
      <div class="signin-dev">
        <span class="composer-hint">Local only</span>
        <a class="button" href="/forum/dev?as=staff">Sign in as dev staff</a>
        <a class="button" href="/forum/dev?as=member">Sign in as dev member</a>
      </div>
    {/if}
  {/if}
</main>
