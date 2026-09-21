import json
import os
from dataclasses import dataclass


def _json(name: str, fallback):
    raw = os.environ.get(name, "").strip()
    if not raw:
        return fallback
    try:
        return json.loads(raw)
    except json.JSONDecodeError as problem:
        raise SystemExit(f"{name} is not valid json: {problem}") from problem


@dataclass(frozen=True)
class Config:
    token: str
    guild: int | None
    site: str
    secret: str
    deepseek_key: str
    deepseek_model: str
    announce: dict[str, int]
    help_channels: set[int]
    role_names: dict[str, str]
    state_file: str

    @classmethod
    def read(cls) -> "Config":
        token = os.environ.get("DISCORD_TOKEN", "").strip()
        if not token:
            raise SystemExit("DISCORD_TOKEN is missing")

        secret = os.environ.get("BOT_SECRET", "").strip()
        if not secret:
            raise SystemExit("BOT_SECRET is missing, and nothing works without it")

        guild = os.environ.get("GUILD_ID", "").strip()
        site = os.environ.get("SITE_URL", "https://moud.epistudios.fr").strip().rstrip("/")

        announce = {
            category: int(channel)
            for category, channel in _json("ANNOUNCE_CHANNELS", {}).items()
            if int(channel) > 0
        }

        return cls(
            token=token,
            guild=int(guild) if guild else None,
            site=site if site.startswith("http") else f"https://{site}",
            secret=secret,
            deepseek_key=os.environ.get("DEEPSEEK_API_KEY", "").strip(),
            deepseek_model=os.environ.get("DEEPSEEK_MODEL", "deepseek-chat").strip(),
            announce=announce,
            help_channels={int(channel) for channel in _json("HELP_CHANNELS", [])},
            role_names=_json("ROLE_NAMES", {}),
            state_file=os.environ.get("STATE_FILE", "state.json").strip(),
        )
