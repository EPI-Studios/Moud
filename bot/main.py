import asyncio
import logging

from dotenv import load_dotenv

from moud.client import Moud
from moud.config import Config


async def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
        datefmt="%H:%M:%S",
    )
    load_dotenv()
    bot = Moud(Config.read())
    async with bot:
        await bot.start(bot.config.token)


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
