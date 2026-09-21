import logging

import aiohttp
import discord
from discord.ext import commands

from .brain import Brain
from .config import Config
from .docs import Library
from .site import Site

COGS = ("moud.cogs.accounts", "moud.cogs.announce", "moud.cogs.library")

log = logging.getLogger("moud")


class Moud(commands.Bot):
    def __init__(self, config: Config):
        super().__init__(
            command_prefix=commands.when_mentioned,
            intents=discord.Intents(guilds=True, members=True, messages=True, message_content=True),
            help_command=None,
        )
        self.config = config
        self.session: aiohttp.ClientSession | None = None
        self.site: Site | None = None
        self.library: Library | None = None
        self.brain: Brain | None = None

    async def setup_hook(self) -> None:
        self.session = aiohttp.ClientSession()
        self.site = Site(self.config.site, self.config.secret, self.session)
        self.library = Library(self.site)
        self.brain = Brain(self.config.deepseek_key, self.config.deepseek_model, self.session)

        for cog in COGS:
            await self.load_extension(cog)

        if self.config.guild:
            guild = discord.Object(id=self.config.guild)
            self.tree.copy_global_to(guild=guild)
            await self.tree.sync(guild=guild)
        else:
            await self.tree.sync()

    async def close(self) -> None:
        await super().close()
        if self.session:
            await self.session.close()

    async def on_ready(self) -> None:
        log.info("signed in as %s, watching %s", self.user, self.config.site)
