# Rich text

Rich text is text with tags in it. You write the tags inside the string, and the engine draws the
result: bold words, coloured names, letters that wave, a tooltip, an item icon. Chat messages,
prefixes and speech bubbles are all drawn this way, so anywhere [chat](chat.md) takes a string, it
takes these tags.

```lua
game.chat:system("<color=gold><b>meek</b></color> joined")
```

A tag that is not one of these is shown as text, so a stray `<` is never eaten. `\<` and `&lt;` are
always a plain `<`; `&gt;`, `&amp;`, `&quot;` and `&apos;` work too. Tags close by name: `</b>`
closes the nearest open `<b>` and everything opened inside it.

> [!NOTE]
> Because unknown tags are shown rather than swallowed, a player typing `<3` sees `<3`. A channel
> decides which tags its members may actually use through its `richText` property, so letting people
> type does not let them paint. See [Chat](chat.md).

## Style

These change how the letters look. They nest, so `<b><color=red>` gives you bold red text:

| Tag | Effect |
|---|---|
| `<b>` `<i>` `<u>` `<s>` | bold, italic, underline, strikethrough |
| `<obf>` | scrambled letters |
| `<uc>` / `<uppercase>` | upper case |
| `<sc>` / `<smallcaps>` | small capitals |
| `<br/>` | a line break |
| `<!-- ... -->` | hidden |
| `<color=#ff8800>` | colour: `#rgb`, `#rrggbb`, `#rrggbbaa`, or `red`, `gold`, `aqua`... (the game's sixteen) |
| `<font color="#fff" size="1.5" face="minecraft:uniform" transparency="0.2">` | several at once |
| `<size=1.5>` | size, relative to the text around it |
| `<alpha=0.5>` / `<transparency=0.5>` | fade |
| `<stroke color="#000" thickness="1">` | outline |
| `<mark color="#ffff0066">` | highlight behind the text |
| `<shadow>` / `<shadow color="#400">` / `<noshadow>` | drop shadow |
| `<gradient from="#f00" to="#00f">` or `<gradient=#f00,#00f>` | colour across the text |
| `<rainbow>` / `<rainbow speed="2">` | moving rainbow |

A few of them are worth knowing before you reach for the others:

- `<color>` accepts hex with or without alpha, and the sixteen names the game already uses, so
  `<color=gold>` matches vanilla gold exactly.
- `<size>` is a multiple of the surrounding text's size, so `1.5` stays proportionate inside a small
  bubble and inside a chat line.
- `<font>` sets colour, size, face and transparency in one tag, which saves nesting four of them.
- `<!-- ... -->` is hidden in the drawn message. Use it to leave a note in a string a script builds.

<!-- demo:richtext -->

## Moving text

These animate the letters. Each takes `strength` and `speed`: `<wave strength=2 speed=1>`. They
stack, so `<wave><rainbow>` gives you letters that bob and change colour at the same time.

| Tag | Effect |
|---|---|
| `<wave>` | letters bob in a wave |
| `<bounce>` | letters hop |
| `<shake>` | letters jitter |
| `<pulse>` | the text breathes in and out |
| `<fade>` | letters fade in a wave |
| `<spin>` | letters turn |

<!-- demo:rich-text-moving -->

## Links and hover

These make part of a message do something when the player clicks it or points at it:

```
<click run="/spawn">spawn</click>          runs a command, or sends the text when it has no /
<click suggest="/msg Meek ">reply</click>  puts it in the chat box
<click url="https://example.com">site</click>   asks before opening
<click copy="ABC-123">code</click>         copies to the clipboard
<click callback="accept">accept</click>    fires game.chat.linkClicked("accept", message)
<hover text="<b>Meek</b> is online">Meek</hover>   a tooltip, itself rich text
<body id=42>Meek</body>                    fires game.chat.bodyClicked(body, message)
```

- `run` and `suggest` are the two chat-box ones: `run` acts straight away, `suggest` fills the box
  and waits for the player.
- `url` never opens anything by itself. The player is asked first.
- `callback` is the one to use for your own interface. The click fires `game.chat.linkClicked` on the
  client with the name you gave and the message it came from, so one message can carry an **accept**
  and a **decline** that your script tells apart by name.
- `<body id=42>` fires `game.chat.bodyClicked` with the body, which is how a name in a message
  becomes something a player can click to open a profile or a trade.
- The text inside `hover` is rich text as well, so a tooltip can be bold and coloured.

<!-- demo:rich-text-links -->

## Pictures

```
<img src="res://icons/crown.png" size="1"/>          size in lines; or width and height
<item id="minecraft:diamond_sword" count="1"/>       the item's icon, with its tooltip on hover
```

`img` sizes in lines of text rather than pixels, so a `size="1"` icon matches the line it sits on.
`item` draws a real Minecraft item, and pointing at it shows that item's own tooltip, count included.

## Custom text shaders

When none of the tags above draw what you want, you can write the drawing yourself. A
`ChatTextShader` names a GLSL file. Text wrapped in `<shader=name>` is drawn through it:

```lua
world:add("ChatTextShader", { name = "glow", shader = "res://chat/glow.glsl" })
game.chat:system("<shader=glow>LEGENDARY</shader> item found")
```

The instance's `name` is what `<shader=...>` looks for, and `shader` is the path to the file.

```glsl
// res://chat/glow.glsl
vec4 textColor(vec4 color, vec2 uv, vec2 glyph, float time) {
    float pulse = 0.75 + 0.25 * sin(time * 4.0 + uv.x * 20.0);
    return vec4(color.rgb * vec3(1.0, 0.85, 0.3) * pulse * 1.4, color.a);
}
```

The file defines one function, `textColor`, and it returns the colour to draw with:

- `color` is the glyph's colour, which is what the tags around it worked out.
- `uv` is where on the screen it is.
- `glyph` is where inside the letter, 0 to 1.
- `time` is seconds, which is what you drive an animation with.

<!-- demo:rich-text-shader -->

> [!TIP]
> Saving the file recompiles it, so you can leave the game open, change a number in the shader, and
> see the next message drawn with it.
