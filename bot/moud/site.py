import hashlib
import hmac
import json
import time

import aiohttp


class SiteError(Exception):
    pass


class Site:
    def __init__(self, base: str, secret: str, session: aiohttp.ClientSession):
        self.base = base
        self.secret = secret.encode()
        self.session = session

    async def _call(self, path: str, payload: dict) -> dict:
        body = json.dumps(payload, separators=(",", ":"))
        at = str(int(time.time() * 1000))
        signature = hmac.new(self.secret, f"{at}.{body}".encode(), hashlib.sha256).hexdigest()

        try:
            async with self.session.post(
                f"{self.base}{path}",
                data=body,
                headers={
                    "content-type": "application/json",
                    "x-moud-at": at,
                    "x-moud-signature": signature,
                },
                timeout=aiohttp.ClientTimeout(total=15),
            ) as answer:
                if answer.status == 401:
                    raise SiteError("the site refused the signature, check BOT_SECRET")
                read = await answer.json(content_type=None)
        except aiohttp.ClientError as problem:
            raise SiteError(f"the site did not answer: {problem}") from problem

        if not isinstance(read, dict):
            raise SiteError("the site answered something that is not an object")
        return read

    async def link(self, code: str, discord_id: int, discord_name: str) -> dict:
        return await self._call(
            "/api/bot/link",
            {"code": code, "discordId": str(discord_id), "discordName": discord_name},
        )

    async def unlink(self, discord_id: int) -> dict:
        return await self._call("/api/bot/unlink", {"discordId": str(discord_id)})

    async def profile(self, **asked: str) -> dict:
        return await self._call("/api/bot/profile", asked)

    async def members(self) -> list[dict]:
        answer = await self._call("/api/bot/members", {})
        return answer.get("members", [])

    async def topics(self, since: str, limit: int = 10) -> list[dict]:
        answer = await self._call("/api/bot/topics", {"since": since, "limit": limit})
        if not answer.get("ok"):
            raise SiteError(answer.get("error", "the site refused to list topics"))
        return answer.get("topics", [])

    async def sections(self) -> list[dict]:
        async with self.session.get(
            f"{self.base}/docs/search.json",
            timeout=aiohttp.ClientTimeout(total=30),
        ) as answer:
            if answer.status != 200:
                raise SiteError(f"the docs index answered {answer.status}")
            return await answer.json(content_type=None)
