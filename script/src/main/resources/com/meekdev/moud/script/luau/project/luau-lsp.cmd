@echo off
if not exist %1$s goto fallback
%1$s -Xmx128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -cp %2$s %3$s %%*
exit /b %%errorlevel%%
:fallback
luau-lsp %%*
