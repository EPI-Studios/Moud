import logging

import discord
from discord import app_commands
from discord.ext import commands, tasks

from ..site import SiteError
from ..style import TONES, note_card, profile_card

log = logging.getLogger("moud.accounts")


def wanted_badges(role: str, badges: list[str]) -> set[str]:
    held = set(badges)
    if role == "staff":
        held.add("staff")
    return held


class Accounts(commands.Cog):
    def __init__(self, bot):
        self.bot = bot
        self.refresh_roles.start()

    def cog_unload(self):
        self.refresh_roles.cancel()

    async def role_for(self, guild: discord.Guild, badge: str) -> discord.Role | None:
        name = self.bot.config.role_names.get(badge)
        if not name:
            return None

        found = discord.utils.get(guild.roles, name=name)
        if found:
            return found

        if not guild.me.guild_permissions.manage_roles:
            return None
        try:
            return await guild.create_role(name=name, colour=discord.Colour(TONES.get("blue", 0x6EA4FF)))
        except discord.HTTPException as problem:
            log.warning("could not make the role %s: %s", name, problem)
            return None

    async def dress(self, member: discord.Member, badges: set[str]) -> None:
        for badge in self.bot.config.role_names:
            role = await self.role_for(member.guild, badge)
            if not role or role >= member.guild.me.top_role:
                continue
            try:
                if badge in badges and role not in member.roles:
                    await member.add_roles(role, reason="moud forum badge")
                elif badge not in badges and role in member.roles:
                    await member.remove_roles(role, reason="moud forum badge")
            except discord.HTTPException as problem:
                log.warning("could not move the role %s on %s: %s", role.name, member, problem)

    @app_commands.command(description="Tie this Discord account to your Moud forum account")
    @app_commands.describe(code="The six characters Moud gave you in the editor")
    async def link(self, interaction: discord.Interaction, code: str):
        await interaction.response.defer(ephemeral=True)
        try:
            answer = await self.bot.site.link(code, interaction.user.id, str(interaction.user))
        except SiteError as problem:
            await interaction.followup.send(view=note_card(f"**{problem}**"), ephemeral=True)
            return

        if not answer.get("ok"):
            await interaction.followup.send(
                view=note_card(
                    f"**{answer.get('error', 'that did not work')}**\n"
                    "-# Open Moud, click your name in the project hub, then Get a code."
                ),
                ephemeral=True,
            )
            return

        profile = answer["profile"]
        if isinstance(interaction.user, discord.Member):
            await self.dress(interaction.user, wanted_badges(profile["role"], [badge["id"] for badge in profile["badges"]]))

        await interaction.followup.send(view=profile_card(profile), ephemeral=True)

    @app_commands.command(description="Forget the link between this Discord account and the forum")
    async def unlink(self, interaction: discord.Interaction):
        await interaction.response.defer(ephemeral=True)
        try:
            answer = await self.bot.site.unlink(interaction.user.id)
        except SiteError as problem:
            await interaction.followup.send(view=note_card(f"**{problem}**"), ephemeral=True)
            return

        if not answer.get("ok"):
            await interaction.followup.send(view=note_card(f"**{answer.get('error')}**"), ephemeral=True)
            return

        if isinstance(interaction.user, discord.Member):
            await self.dress(interaction.user, set())

        await interaction.followup.send(
            view=note_card(f"Done. **{answer['handle']}** is no longer tied to this Discord account."),
            ephemeral=True,
        )

    @app_commands.command(description="Show someone's Moud forum profile")
    @app_commands.describe(member="Someone on this server", name="A Minecraft name or a forum handle")
    async def whois(
        self,
        interaction: discord.Interaction,
        member: discord.Member | None = None,
        name: str | None = None,
    ):
        await interaction.response.defer()
        asked = {"discordId": str((member or interaction.user).id)} if not name else {"name": name}

        try:
            answer = await self.bot.site.profile(**asked)
            if not answer.get("ok") and name:
                answer = await self.bot.site.profile(handle=name.lower())
        except SiteError as problem:
            await interaction.followup.send(view=note_card(f"**{problem}**"))
            return

        if not answer.get("ok"):
            who = name or (member or interaction.user).display_name
            await interaction.followup.send(
                view=note_card(f"**{who}** has no linked Moud account.\n-# `/link` ties one to a Discord account.")
            )
            return

        await interaction.followup.send(view=profile_card(answer["profile"]))

    @tasks.loop(minutes=15)
    async def refresh_roles(self):
        if not self.bot.config.role_names:
            return
        try:
            members = await self.bot.site.members()
        except SiteError as problem:
            log.warning("no member list: %s", problem)
            return

        for guild in self.bot.guilds:
            for row in members:
                member = guild.get_member(int(row["discordId"]))
                if member:
                    await self.dress(member, wanted_badges(row["role"], row["badges"]))

    @refresh_roles.before_loop
    async def wait(self):
        await self.bot.wait_until_ready()


async def setup(bot):
    await bot.add_cog(Accounts(bot))
