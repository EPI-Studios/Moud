import logging
import time

import discord
from discord import app_commands
from discord.ext import commands

from ..site import SiteError
from ..style import doc_card, note_card

log = logging.getLogger("moud.library")

ASKING = ("?", "comment ", "pourquoi ", "how ", "why ", "what ", "where ", "can i ", "is there ")
QUIET_FOR = 90.0
SHORTEST = 20
WEAK = 6.0


class Look(commands.Cog):
    def __init__(self, bot):
        self.bot = bot
        self.spoke_to: dict[int, float] = {}

    async def find(self, question: str) -> list[dict]:
        hits = await self.bot.library.look(question, 5)
        if self.bot.brain.awake and (not hits or hits[0][0] < WEAK):
            terms = await self.bot.brain.keywords(question)
            if terms:
                again = await self.bot.library.look(" ".join(terms), 5)
                if again and (not hits or again[0][0] > hits[0][0]):
                    hits = again
        return [section for _, section in hits]

    @app_commands.command(description="Search the Moud documentation")
    @app_commands.describe(query="What you are trying to do")
    async def docs(self, interaction: discord.Interaction, query: str):
        await interaction.response.defer()
        try:
            hits = await self.find(query)
        except SiteError as problem:
            await interaction.followup.send(view=note_card(f"**{problem}**"))
            return

        if not hits:
            await interaction.followup.send(
                view=note_card(f"Nothing in the docs matches **{query}**.")
            )
            return

        picked = await self.bot.brain.choose(query, hits)
        best = picked["section"] if picked else hits[0]
        note = picked["why"] if picked and picked["why"] else None
        others = [hit for hit in hits if hit is not best][:3]

        await interaction.followup.send(view=doc_card(self.bot.config.site, best, note, others))

    @app_commands.command(description="Look up a class in the engine reference")
    @app_commands.describe(name="A class name, like Vector3 or CFrame")
    async def api(self, interaction: discord.Interaction, name: str):
        await interaction.response.defer()
        try:
            hits = await self.bot.library.klass(name)
        except SiteError as problem:
            await interaction.followup.send(view=note_card(f"**{problem}**"))
            return

        if not hits:
            await interaction.followup.send(
                view=note_card(f"**{name}** is not in the reference.\n-# `/docs` searches the guides instead.")
            )
            return

        await interaction.followup.send(
            view=doc_card(self.bot.config.site, hits[0], None, hits[1:3])
        )

    @api.autocomplete("name")
    async def class_names(self, interaction: discord.Interaction, current: str):
        try:
            names = await self.bot.library.classes()
        except SiteError:
            return []
        wanted = current.lower()
        found = [name for name in names if wanted in name.lower()][:25]
        return [app_commands.Choice(name=name, value=name) for name in found]

    def too_soon(self, who: int) -> bool:
        last = self.spoke_to.get(who, 0.0)
        if time.monotonic() - last < QUIET_FOR:
            return True
        self.spoke_to[who] = time.monotonic()
        return False

    @commands.Cog.listener()
    async def on_message(self, message: discord.Message):
        if message.author.bot or message.channel.id not in self.bot.config.help_channels:
            return
        if not self.bot.brain.awake or len(message.content) < SHORTEST:
            return

        asked = message.content.lower()
        if not any(mark in asked for mark in ASKING):
            return
        if self.too_soon(message.author.id):
            return

        try:
            hits = await self.find(message.content)
        except SiteError:
            return
        if not hits:
            return

        picked = await self.bot.brain.choose(message.content, hits)
        if not picked:
            return

        section = picked["section"]
        note = picked["why"] or "This part of the docs covers it."
        if picked["quote"]:
            section = dict(section, text=picked["quote"])

        await message.reply(
            view=doc_card(self.bot.config.site, section, f"**You might be looking for this.** {note}", []),
            mention_author=False,
        )


async def setup(bot):
    await bot.add_cog(Look(bot))
