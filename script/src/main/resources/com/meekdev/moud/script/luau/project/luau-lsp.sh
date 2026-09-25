#!/bin/sh
java=%1$s
[ -x "$java" ] || exec luau-lsp "$@"
exec "$java" -Xmx128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -cp %2$s %3$s "$@"
