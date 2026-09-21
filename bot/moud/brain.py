import json

import aiohttp

ENDPOINT = "https://api.deepseek.com/chat/completions"

ORDERS = """You answer questions about Moud, a game engine that runs inside Minecraft and is scripted in Luau.
You are given the question and numbered sections of the Moud documentation.
Pick the one section that answers the question, or none of them.
Never use knowledge from outside the sections. Never invent an API, a method or a field.
Answer with json only: {"index": <number of the section, or null>, "why": "<one short sentence, plain English, saying what that section gives them>", "quote": "<at most three lines copied word for word from that section, or empty>"}
If no section really answers it, index is null."""


class Brain:
    def __init__(self, key: str, model: str, session: aiohttp.ClientSession):
        self.key = key
        self.model = model
        self.session = session

    @property
    def awake(self) -> bool:
        return bool(self.key)

    async def keywords(self, question: str) -> list[str]:
        if not self.awake:
            return []
        return await _ask_terms(self, question)

    async def choose(self, question: str, candidates: list[dict]) -> dict | None:
        if not self.awake or not candidates:
            return None

        written = "\n\n".join(
            f"[{index}] page: {section.get('page')} · heading: {section.get('heading') or '(top)'}\n"
            f"{(section.get('text') or '')[:900]}"
            for index, section in enumerate(candidates)
        )

        payload = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": ORDERS},
                {"role": "user", "content": f"Question:\n{question}\n\nSections:\n{written}"},
            ],
            "response_format": {"type": "json_object"},
            "temperature": 0.1,
            "max_tokens": 400,
        }

        try:
            async with self.session.post(
                ENDPOINT,
                json=payload,
                headers={"authorization": f"Bearer {self.key}"},
                timeout=aiohttp.ClientTimeout(total=25),
            ) as answer:
                if answer.status != 200:
                    return None
                read = await answer.json(content_type=None)
        except (aiohttp.ClientError, TimeoutError):
            return None

        try:
            said = json.loads(read["choices"][0]["message"]["content"])
        except (KeyError, IndexError, json.JSONDecodeError):
            return None

        index = said.get("index")
        if not isinstance(index, int) or not 0 <= index < len(candidates):
            return None

        return {
            "section": candidates[index],
            "why": str(said.get("why") or "").strip()[:280],
            "quote": str(said.get("quote") or "").strip()[:600],
        }


TERMS = """You turn a question about the Moud game engine into search words for its English documentation.
The question may be in any language. Answer with json only: {"terms": ["...", "..."]}
Give at most six single English words a manual would use, no punctuation, no sentences."""


async def _ask_terms(brain: "Brain", question: str) -> list[str]:
    payload = {
        "model": brain.model,
        "messages": [
            {"role": "system", "content": TERMS},
            {"role": "user", "content": question[:500]},
        ],
        "response_format": {"type": "json_object"},
        "temperature": 0.0,
        "max_tokens": 120,
    }

    try:
        async with brain.session.post(
            ENDPOINT,
            json=payload,
            headers={"authorization": f"Bearer {brain.key}"},
            timeout=aiohttp.ClientTimeout(total=20),
        ) as answer:
            if answer.status != 200:
                return []
            read = await answer.json(content_type=None)
        said = json.loads(read["choices"][0]["message"]["content"])
    except (aiohttp.ClientError, TimeoutError, KeyError, IndexError, json.JSONDecodeError):
        return []

    terms = said.get("terms")
    if not isinstance(terms, list):
        return []
    return [str(term)[:32] for term in terms[:6] if str(term).strip()]
