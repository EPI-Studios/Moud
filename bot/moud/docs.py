import math
import re
import time

STOP = {
    "a", "an", "and", "are", "as", "at", "be", "but", "by", "can", "do", "does", "for", "from",
    "how", "i", "in", "is", "it", "its", "me", "my", "not", "of", "on", "or", "that", "the",
    "there", "this", "to", "what", "when", "where", "why", "with", "you", "your",
    "comment", "est", "je", "la", "le", "les", "pour", "que", "quoi", "un", "une",
}

WORD = re.compile(r"[a-z0-9_]+")


def words(text: str) -> list[str]:
    return [word for word in WORD.findall(text.lower()) if word not in STOP and len(word) > 1]


class Library:
    def __init__(self, site, ttl: float = 3600.0):
        self.site = site
        self.ttl = ttl
        self.sections: list[dict] = []
        self.weight: dict[str, float] = {}
        self.read_at = 0.0

    async def ready(self) -> list[dict]:
        if self.sections and time.monotonic() - self.read_at < self.ttl:
            return self.sections

        self.sections = await self.site.sections()
        self.read_at = time.monotonic()

        seen: dict[str, int] = {}
        for section in self.sections:
            whole = f"{section.get('page', '')} {section.get('heading', '')} {section.get('text', '')}"
            for word in set(words(whole)):
                seen[word] = seen.get(word, 0) + 1

        total = len(self.sections)
        self.weight = {
            word: math.log((total + 1) / (count + 1)) + 0.4 for word, count in seen.items()
        }
        return self.sections

    def rarity(self, word: str) -> float:
        return self.weight.get(word, 3.0)

    async def look(self, query: str, limit: int = 5) -> list[tuple[float, dict]]:
        sections = await self.ready()
        asked = words(query)
        if not asked:
            return []

        phrase = query.strip().lower()
        mass = sum(self.rarity(word) for word in asked)
        scored = []

        for section in sections:
            heading = (section.get("heading") or "").lower()
            page = (section.get("page") or "").lower()
            text = (section.get("text") or "").lower()

            score = 0.0
            if len(phrase) > 6:
                if phrase in heading:
                    score += 14
                elif phrase in text:
                    score += 6

            met = 0.0
            for word in asked:
                found = text.count(word)
                here = 0.0
                if word in heading:
                    here += 2.6
                if word in page:
                    here += 1.6
                if found:
                    here += 0.8 * min(found, 4) ** 0.5
                if here:
                    met += self.rarity(word)
                score += here * self.rarity(word)

            if not met:
                continue

            score *= 0.35 + 0.65 * (met / mass)
            score /= 1 + len(text) / 2400
            scored.append((score, section))

        scored.sort(key=lambda pair: pair[0], reverse=True)
        return scored[:limit]

    async def search(self, query: str, limit: int = 5) -> list[dict]:
        return [section for _, section in await self.look(query, limit)]

    async def klass(self, name: str) -> list[dict]:
        sections = await self.ready()
        wanted = name.strip().lower()
        reference = [
            section
            for section in sections
            if section.get("url", "").startswith("/docs/reference#") and section.get("heading")
        ]

        exact = [section for section in reference if section["heading"].lower() == wanted]
        if exact:
            return exact
        return [section for section in reference if wanted in section["heading"].lower()][:5]

    async def classes(self) -> list[str]:
        sections = await self.ready()
        return [
            section["heading"]
            for section in sections
            if section.get("url", "").startswith("/docs/reference#") and section.get("heading")
        ]

    async def pages(self) -> list[tuple[str, str]]:
        sections = await self.ready()
        seen: dict[str, str] = {}
        for section in sections:
            page = section.get("page") or ""
            url = (section.get("url") or "").split("#")[0]
            if page and page not in seen:
                seen[page] = url
        return sorted(seen.items())
