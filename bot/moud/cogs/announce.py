import json
import logging
from datetime import datetime, timedelta, timezone
from pathlib import Path

import discord
from discord.ext import commands, tasks

from ..site import SiteError
from ..style import topic_card

log = logging.getLogger("moud.announce")


class Announce(commands.Cog):
    def __init__(self, bot):
        self.bot = bot
        self.state = Path(bot.config.state_file)
        self.since = self.read_since()
        self.missing: set[str] = set()
        self.watch.start()

    def cog_unload(self):
        self.watch.cancel()

    def read_since(self) -> str:
        try:
            saved = json.loads(self.state.read_text())
            return str(saved["topicsSince"])
        except (OSError, KeyError, ValueError):
            return (datetime.now(timezone.utc) - timedelta(minutes=5)).isoformat().replace("+00:00", "Z")

    def write_since(self, when: str) -> None:
        self.since = when
        try:
            self.state.write_text(json.dumps({"topicsSince": when}, indent=1))
        except OSError as problem:
            log.warning("could not keep the mark: %s", problem)

    def channel_for(self, category: str) -> discord.abc.Messageable | None:
        where = self.bot.config.announce.get(category)
        if not where:
            if category not in self.missing:
                self.missing.add(category)
                log.info("no channel set for %s, skipping those topics", category)
            return None

        channel = self.bot.get_channel(where)
        if channel is None:
            log.warning("channel %s for %s is not visible to the bot", where, category)
        return channel

    @tasks.loop(seconds=60)
    async def watch(self):
        try:
            topics = await self.bot.site.topics(self.since, 10)
        except SiteError as problem:
            log.warning("no topics: %s", problem)
            return

        for topic in topics:
            channel = self.channel_for(topic["category"]["id"])
            if channel is not None:
                try:
                    await channel.send(view=topic_card(topic))
                except discord.HTTPException as problem:
                    log.warning("could not post %s: %s", topic["url"], problem)
            self.write_since(topic["createdAt"])

    @watch.before_loop
    async def wait(self):
        await self.bot.wait_until_ready()


async def setup(bot):
    await bot.add_cog(Announce(bot))
