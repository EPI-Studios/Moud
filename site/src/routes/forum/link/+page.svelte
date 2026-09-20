<script lang="ts">
  import Icon from "$lib/components/Icon.svelte"

  let { data, form } = $props()

  const linked = $derived(form?.linked ?? (form?.unlinked ? null : data.minecraftName))
</script>

<svelte:head><title>Link your Minecraft account | Moud Forum</title></svelte:head>

<main class="forum-shell">
  <nav class="crumbs">
    <a href="/forum">Forum</a>
    <span>Link your account</span>
  </nav>

  <div class="page-head">
    <div>
      <h1>Link your Minecraft account</h1>
      <p>Your name, skin and badge on the forum come from the account you play on.</p>
    </div>
  </div>

  {#if form?.message}<p class="form-error">{form.message}</p>{/if}

  {#if linked}
    <div class="empty linked-row">
      <span>This account is linked to <b>{linked}</b>.</span>
      <form method="POST" action="?/unlink">
        <button class="post-tool" type="submit">Unlink it</button>
      </form>
    </div>
  {/if}

  {#if form?.found}
    <div class="confirm">
      <img
        class="confirm-bust"
        src="https://visage.surgeplay.com/bust/128/{form.found.name}.png"
        alt=""
      />
      <div>
        <h2>Link <b>{form.found.name}</b> to this account?</h2>
        <p>
          Check that name. If it is not yours, somebody sent you their code and you would be putting
          their account on your profile.
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
          <p>
            On the project hub, or in <b>Project settings → Account</b>, choose
            <b>Link forum account</b>. Moud proves to Mojang who you are and shows you a code.
          </p>
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
          <p>We show you which Minecraft account the code belongs to before anything is linked.</p>
        </div>
      </li>
    </ol>
  {/if}
</main>
