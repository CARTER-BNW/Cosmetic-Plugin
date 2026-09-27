#!/usr/bin/env python3
"""Minimal Minecraft RCON client for driving the dev server from scripts.

Usage:  python tools/rcon.py "cosmeticshop reload" "skycoins balance"
Env:    RCON_HOST (default 127.0.0.1), RCON_PORT (default 25576), RCON_PASS (default cosmeticdev)
Matches run/server.properties for the run-paper dev server.
"""
import os
import socket
import struct
import sys

HOST = os.environ.get("RCON_HOST", "127.0.0.1")
PORT = int(os.environ.get("RCON_PORT", "25576"))
PASS = os.environ.get("RCON_PASS", "cosmeticdev")


def _send(sock: socket.socket, req_id: int, kind: int, payload: str) -> None:
    body = struct.pack("<ii", req_id, kind) + payload.encode("utf-8") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(body)) + body)


def _recv(sock: socket.socket) -> tuple[int, int, str]:
    raw = b""
    while len(raw) < 4:
        chunk = sock.recv(4 - len(raw))
        if not chunk:
            raise ConnectionError("connection closed")
        raw += chunk
    (length,) = struct.unpack("<i", raw)
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise ConnectionError("connection closed")
        data += chunk
    req_id, kind = struct.unpack("<ii", data[:8])
    return req_id, kind, data[8:-2].decode("utf-8", "replace")


def run(commands: list[str]) -> int:
    with socket.create_connection((HOST, PORT), timeout=10) as sock:
        _send(sock, 1, 3, PASS)
        req_id, _, _ = _recv(sock)
        if req_id == -1:
            print("RCON auth failed", file=sys.stderr)
            return 2
        for i, cmd in enumerate(commands, start=10):
            _send(sock, i, 2, cmd)
            _, _, out = _recv(sock)
            print(f"> {cmd}")
            if out.strip():
                print(out.rstrip())
    return 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    sys.exit(run(sys.argv[1:]))
