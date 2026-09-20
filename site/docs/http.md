# Reaching websites

`http` is the global a server script uses to call an address on the web and read what comes back: a
webhook, a score board of your own, a Discord message. Client scripts cannot; a player's machine
never makes the call, so nothing a place asks for depends on what is installed on their computer or
what their network allows.

## Turning it on

Requests are off until the place asks for them. Add the feature to `place.toml`:

```toml
[features]
httpRequests = true
```

In the editor the same switch is **Project Settings > Scripts > httpRequests**. Without it every
request errors with "http requests are off".

## Asking

```lua
-- server
task.spawn(function()
    local reply = http.get("https://example.com/scores")
    print(reply.status, reply.ok)
    local scores = http.jsonDecode(reply.body)
end)
```

The call runs inside `task.spawn` because it waits for the website to answer. While it waits, the
rest of the place keeps running: the tick carries on, other players are served, and your script
resumes on the line after the call once the answer arrives.

There are three ways to ask, and they differ only in how much you spell out:

| Call | |
|---|---|
| `http.get(url, headers?)` | |
| `http.post(url, body, contentType?, headers?)` | `contentType` is `application/json` when left out |
| `http.request({ url = ..., method = ..., headers = ..., body = ... })` | `method` is `GET` when left out |

Use `http.request` when you need a method the other two do not cover, or when you want to build the
request as a table. `method` is one of `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD` or `OPTIONS`.
A method the engine does not know is an error that says so.

Each of them hands back one table:

| Field | |
|---|---|
| `status` | the code, like 200 or 404 |
| `ok` | true for 200 to 299 |
| `body` | the answer as text |
| `headers` | the answer's headers, joined with `, ` where one was sent twice |

`body` is always text. If the site answered with JSON, run it through `http.jsonDecode` yourself.

> [!IMPORTANT]
> A request waits, so it runs inside a function started with `task.spawn`, like anything else that
> waits. Calling it straight out of `game.stepped` holds up the tick everybody else is on.

<!-- demo:http-wait -->

## Helpers

These turn values into text and back, and generate the ids and escaped strings a web call usually
needs. They work on both sides, with the feature off, and never wait:

```lua
http.jsonEncode({ name = "meek", score = 12 })   -- '{"name":"meek","score":12}'
http.jsonDecode('{"ok":true}').ok                -- true
http.generateGuid()                              -- "{2E3A...}"
http.generateGuid(false)                         -- the same without the braces
http.urlEncode("a b&c")                          -- "a%20b%26c"
```

- `jsonEncode` takes what a store takes: numbers, text, booleans and nested tables with text keys.
- `jsonDecode` errors on text it cannot read, so catch it with `pcall` when the text came from
  outside.
- `generateGuid` gives you a unique id, with braces by default and without them when you pass
  `false`.
- `urlEncode` escapes a value so you can put it in a query string without the spaces and ampersands
  breaking the address.

<!-- demo:http-helpers -->

## Catch what you send

A website can be down, slow or angry, and a request that fails raises in the caller. Wrap it:

```lua
-- server
local hook = "https://example.com/hooks/rounds"

local function announce(winner: string, seconds: number)
    task.spawn(function()
        local ok, reply = pcall(function()
            return http.post(hook, http.jsonEncode({
                content = winner .. " won in " .. math.floor(seconds) .. " seconds",
            }))
        end)
        if not ok then
            print("the webhook did not go through:", reply)
        elseif not reply.ok then
            print("the webhook answered", reply.status, reply.body)
        end
    end)
end
```

There are two ways this goes wrong, and you check them separately:

- The call never got an answer at all, because the address is unreachable or it ran out of time.
  `pcall` returns `false` and the second value is the error.
- The call got an answer and the answer was a refusal, such as 401 or 429. `pcall` returns `true`,
  and `reply.ok` is false. `reply.status` and `reply.body` say why.

<!-- demo:http-outcome -->

## Limits

- Only `http://` and `https://` addresses. Anything else is an error before the call leaves.
- 30 seconds to connect and to answer.
- 16 MB each way. A longer body is refused; a longer answer is cut.
- Redirects are followed.
- A player is never told what the server asked or what it heard. Send it over a
  [channel](talking.md) yourself if they should see it.

> [!CAUTION]
> Treat everything in `body` as something a stranger wrote. Check it before you put it in the world:
> a name you paste into a `TextLabel`, a number you feed to `cframe`, or a block id you look up all
> come from somebody outside your place.
