<script lang="ts">
  import Demo from "../ui/Demo.svelte"
  import Choice from "../ui/Choice.svelte"
  import CodePanel from "../ui/CodePanel.svelte"
  import Note from "../ui/Note.svelte"

  type LayoutName = "qwerty" | "azerty" | "qwertz" | "dvorak"
  type Layout = { digits: string[]; rows: string[] }

  const LAYOUTS: Record<LayoutName, Layout> = {
    qwerty: {
      digits: ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"],
      rows: ["qwertyuiop", "asdfghjkl;", "zxcvbnm,./"],
    },
    azerty: {
      digits: ["&", "é", '"', "'", "(", "-", "è", "_", "ç", "à"],
      rows: ["azertyuiop", "qsdfghjklm", "wxcvbn,;:!"],
    },
    qwertz: {
      digits: ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"],
      rows: ["qwertzuiop", "asdfghjklö", "yxcvbnm,.-"],
    },
    dvorak: {
      digits: ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"],
      rows: ["',.pyfgcrl", "aoeuidhtns", ";qjkxbmwvz"],
    },
  }

  const MOVES: Record<string, string> = { "1,1": "forward", "2,0": "left", "2,1": "back", "2,2": "right" }
  const MOVES_ROW: Record<string, [number, number]> = {
    forward: [1, 1],
    left: [2, 0],
    back: [2, 1],
    right: [2, 2],
  }
  const ROWS = [0, 1, 2, 3]
  const COLUMNS = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]

  let layout = $state<LayoutName>("azerty")
  let row = $state(1)
  let col = $state(1)

  function quote(value: string) {
    return '"' + value.replace(/\\/g, "\\\\").replace(/"/g, '\\"') + '"'
  }

  function digit(at: number) {
    return String((at + 1) % 10)
  }

  function typed(atRow: number, atCol: number) {
    const board = LAYOUTS[layout]
    return atRow === 0 ? board.digits[atCol] : board.rows[atRow - 1].charAt(atCol)
  }

  const code = $derived(row === 0 ? digit(col) : typed(row, col))
  const move = $derived(MOVES[`${row},${col}`])

  const source = $derived.by(() => {
    const lines = [
      "input.inputBegan:connect(function(object, gameProcessed)",
      `    -- object.keyCode is ${quote(code)}`,
      "end)",
      `input:isKeyDown(${quote(code)})`,
    ]
    if (row > 0 && /^[a-z]$/.test(code)) {
      lines.push(`input:keyName(${quote(code)})   -- ${quote(code.toUpperCase())}`)
    }
    if (move) {
      const bound = typed(MOVES_ROW[move][0], MOVES_ROW[move][1])
      lines.push("")
      lines.push(`input:down("${move}")`)
      if (/^[a-z]$/.test(bound)) {
        lines.push(`input:keyName("${move}")   -- ${quote(bound.toUpperCase())} with the default bindings`)
      }
    }
    return lines.join("\n")
  })

  const note = $derived.by(() => {
    if (row === 0) {
      return layout === "azerty"
        ? `Without shift this key types ${typed(0, col)}, but digits are always "0" to "9", so it is ${quote(code)}.`
        : 'Digits are always "0" to "9", whatever the digit row types.'
    }
    return (
      "The key is named by what it types on this layout." +
      (move
        ? ` It sits where a qwerty board has ${LAYOUTS.qwerty.rows[row - 1].charAt(col).toUpperCase()}, so it moves the player ${move}: ask for the action, never the letter.`
        : "")
    )
  })
</script>

<Demo label="Key names">
  <div class="demo-controls cam-keys-controls">
    <Choice
      label="layout"
      options={["qwerty", "azerty", "qwertz", "dvorak"] as const}
      bind:value={layout}
    />
  </div>

  <div class="cam-board">
    {#each ROWS as boardRow (boardRow)}
      <div class="cam-row cam-row-{boardRow}">
        {#each COLUMNS as boardCol (boardCol)}
          {@const face = typed(boardRow, boardCol)}
          {@const action = MOVES[`${boardRow},${boardCol}`]}
          <button
            type="button"
            class="cam-key"
            aria-pressed={boardRow === row && boardCol === col}
            onclick={() => {
              row = boardRow
              col = boardCol
            }}
          >
            <span class="cam-key-face">{boardRow === 0 ? digit(boardCol) : face.toUpperCase()}</span>
            {#if boardRow === 0 && face !== digit(boardCol)}
              <span class="cam-key-sub">{face}</span>
            {/if}
            {#if action}
              <span class="cam-key-action">{action}</span>
            {/if}
          </button>
        {/each}
      </div>
    {/each}
  </div>

  <Note>{note}</Note>
  <CodePanel {source} />
</Demo>
