#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def artifact_entry(path: Path, base_url: str) -> dict:
    return {
        "file": path.name,
        "url": f"{base_url}/{path.name}",
        "format": "tar.gz",
        "size": path.stat().st_size,
        "sha256": sha256_file(path),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate release.json for Moud GitHub releases.")
    parser.add_argument("--version", required=True)
    parser.add_argument("--channel", required=True)
    parser.add_argument("--base-download-url", required=True)
    parser.add_argument("--client-archive", required=True)
    parser.add_argument("--server-archive", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    client_archive = Path(args.client_archive).resolve()
    server_archive = Path(args.server_archive).resolve()
    output = Path(args.output).resolve()

    manifest = {
        "schema_version": 1,
        "version": args.version,
        "channel": args.channel,
        "published_at": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "artifacts": {
            "client": {
                "full": artifact_entry(client_archive, args.base_download_url),
                "patches": [],
            },
            "server": {
                "full": artifact_entry(server_archive, args.base_download_url),
                "patches": [],
            },
        },
    }

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(manifest, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
