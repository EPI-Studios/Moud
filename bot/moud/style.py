import discord
from discord import ui

ACCENTS = {
    "help": 0x6EA4FF,
    "show": 0xB692FF,
    "bugs": 0xFF6A5C,
    "plugins": 0xF2C14E,
    "news": 0x5FD07A,
}

TONES = {
    "purple": 0xB692FF,
    "blue": 0x6EA4FF,
    "green": 0x5FD07A,
    "yellow": 0xF2C14E,
    "red": 0xFF6A5C,
}

PLAIN = 0xC6C6C6
QUIET = 0x6A6A6A


def _stamp(iso: str) -> str:
    when = discord.utils.parse_time(iso)
    return f"<t:{int(when.timestamp())}:R>" if when else ""


class Card(ui.LayoutView):
    def __init__(self, accent: int):
        super().__init__(timeout=None)
        self.box = ui.Container(accent_colour=accent)
        self.add_item(self.box)

    def text(self, content: str) -> "Card":
        self.box.add_item(ui.TextDisplay(content))
        return self

    def beside(self, thumbnail: str, *lines: str) -> "Card":
        self.box.add_item(
            ui.Section(*[ui.TextDisplay(line) for line in lines], accessory=ui.Thumbnail(thumbnail))
        )
        return self

    def picture(self, url: str) -> "Card":
        self.box.add_item(ui.MediaGallery(discord.MediaGalleryItem(url)))
        return self

    def rule(self) -> "Card":
        self.box.add_item(ui.Separator())
        return self

    def links(self, *labels: tuple[str, str]) -> "Card":
        self.box.add_item(
            ui.ActionRow(
                *[
                    ui.Button(style=discord.ButtonStyle.link, label=label, url=url)
                    for label, url in labels
                ]
            )
        )
        return self


def topic_card(topic: dict) -> Card:
    category = topic["category"]
    author = topic["author"]
    card = Card(ACCENTS.get(category["id"], PLAIN))
    card.beside(
        author["face"],
        f"**{category['name']}** · by [{author['name']}]({author['url']}) {_stamp(topic['createdAt'])}",
        f"## [{topic['title']}]({topic['url']})",
    )
    if topic.get("blurb"):
        card.text(f"-# {topic['blurb']}")
    card.picture(topic["card"])
    card.links(("Read and reply", topic["url"]))
    return card


def profile_card(profile: dict) -> Card:
    badges = profile.get("badges", [])
    accent = TONES.get(badges[0]["tone"], PLAIN) if badges else PLAIN
    lines = [
        f"## [{profile['name']}]({profile['url']})",
        f"-# @{profile['handle']} · joined <t:{int(discord.utils.parse_time(profile['joinedAt']).timestamp())}:D>",
    ]
    card = Card(accent).beside(profile["face"], *lines)
    card.rule()
    card.text(
        f"**{profile['topicCount']}** topics · **{profile['postCount']}** posts"
        + (f" · **{profile['role']}**" if profile["role"] != "member" else "")
    )
    if badges:
        card.text(" ".join(f"`{badge['name']}`" for badge in badges))
    card.links(("Open the profile", profile["url"]))
    return card


def doc_card(site: str, hit: dict, note: str | None, others: list[dict]) -> Card:
    where = f"{site}{hit['url']}"
    card = Card(PLAIN)
    card.text(f"## [{hit['heading'] or hit['page']}]({where})\n-# {hit['page']} in the Moud docs")
    if note:
        card.text(note)
    card.text(f">>> {hit['text'][:700]}")
    if others:
        card.rule()
        card.text(
            "-# Also: "
            + " · ".join(f"[{other['heading'] or other['page']}]({site}{other['url']})" for other in others)
        )
    card.links(("Read the page", where))
    return card


def note_card(text: str, accent: int = QUIET) -> Card:
    return Card(accent).text(text)
