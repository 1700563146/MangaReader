#!/usr/bin/env python3
"""MangaReader 开发用假源（仅本地测试，无任何真实漫画内容）。

用法：
    python tools/devserver/serve.py --port 8765

Android 模拟器通过 http://10.0.2.2:8765 访问；配合 app/src/debug 里的
DevInstallActivity 安装 dev_rule.json（只在 debug 构建存在）。

页面结构刻意与 docs/examples/demo-source.json 的选择器保持一致。
"""
from __future__ import annotations

import argparse
import http.server
import io
import socketserver
import urllib.parse

from PIL import Image, ImageDraw


def search_html() -> str:
    items = "".join(
        f"""
        <li>
          <a class="cover title" href="/comic/{cid}/">测试漫画 {cid}
            <img data-src="/img/{cid}/0.png">
          </a>
          <span class="author">作者 {cid}</span>
          <span class="status">连载</span>
          <span class="update">更新至第3话</span>
        </li>"""
        for cid in (1, 2, 3)
    )
    return f"""<html><body>
      <ul class="result">{items}</ul>
      <a class="next" href="/search?q=test&page=2">下一页</a>
    </body></html>"""


def detail_html(cid: int) -> str:
    return f"""<html><body>
      <div class="comic-info">
        <h1>测试漫画 {cid}</h1>
        <img class="cover" src="/img/{cid}/0.png">
        <a class="author">作者 {cid}</a>
        <div class="intro"><p>这是一部仅用于开发测试的占位漫画，没有任何真实内容。</p></div>
        <span class="status">连载</span>
        <span class="update">更新至第3话</span>
      </div>
      <ul class="chapter-list">
        <li><a href="/chapter/{cid}/3/">第3话</a></li>
        <li><a href="/chapter/{cid}/2/">第2话</a></li>
        <li><a href="/chapter/{cid}/1/">第1话</a></li>
      </ul>
    </body></html>"""


def chapter_html(cid: int, no: int) -> str:
    imgs = "".join(f'<img data-src="/img/{cid}/{no}{p}.png">' for p in range(1, 4))
    return f'<html><body><div class="reader">{imgs}</div></body></html>'


def category_html() -> str:
    items = "".join(
        f'<li><a class="title" href="/comic/{cid}/">测试漫画 {cid}</a>'
        f'<img data-src="/img/{cid}/0.png"></li>'
        for cid in (1, 2, 3)
    )
    return f'<html><body><ul class="list">{items}</ul></body></html>'


def image_bytes(cid: int, label: int) -> bytes:
    img = Image.new("RGB", (900, 1350), (11, 15, 14))
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, 900, 16], fill=(94, 234, 212))
    draw.rectangle([0, 1334, 900, 1350], fill=(94, 234, 212))
    draw.text((330, 620), f"{cid} / {label}", fill=(255, 255, 255))
    draw.text((250, 680), "DEV PLACEHOLDER", fill=(148, 163, 184))
    out = io.BytesIO()
    img.save(out, format="PNG")
    return out.getvalue()


class Handler(http.server.BaseHTTPRequestHandler):
    server_version = "MangaReaderDevServer/1.0"

    def log_message(self, fmt: str, *args) -> None:  # noqa: A003
        print("[devserver]", self.address_string(), fmt % args, flush=True)

    def do_GET(self) -> None:  # noqa: N802
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        query = urllib.parse.parse_qs(parsed.query)

        if path.startswith("/img/"):
            parts = path.strip("/").split("/")
            try:
                cid = int(parts[1])
                label = int("".join(ch for ch in parts[2] if ch.isdigit()) or "0")
            except (IndexError, ValueError):
                self.send_error(400)
                return
            self.send_png(image_bytes(cid, label))
            return

        if path == "/search":
            page = query.get("page", ["1"])[0]
            self.send_html(search_html() if page == "1" else "<html><body><ul class='result'></ul></body></html>")
            return

        if path.startswith("/comic/"):
            try:
                cid = int(path.strip("/").split("/")[1])
            except (IndexError, ValueError):
                self.send_error(400)
                return
            self.send_html(detail_html(cid))
            return

        if path.startswith("/chapter/"):
            parts = path.strip("/").split("/")
            try:
                cid, no = int(parts[1]), int(parts[2])
            except (IndexError, ValueError):
                self.send_error(400)
                return
            self.send_html(chapter_html(cid, no))
            return

        if path.startswith("/category/") or path.startswith("/list/hot") or path.startswith("/list/new"):
            self.send_html(category_html())
            return

        self.send_error(404)

    def send_html(self, html: str) -> None:
        body = html.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_png(self, data: bytes) -> None:
        self.send_response(200)
        self.send_header("Content-Type", "image/png")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


class Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    with Server(("0.0.0.0", args.port), Handler) as httpd:
        print(f"devserver listening on http://0.0.0.0:{args.port}", flush=True)
        httpd.serve_forever()


if __name__ == "__main__":
    main()
