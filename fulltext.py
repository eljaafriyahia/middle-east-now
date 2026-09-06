import html as html_lib
import json
import os
import random
import re
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed

import db

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(ROOT, "data")
NEWS_JSON = os.path.join(DATA_DIR, "news.json")

if sys.platform == "win32" and hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36"
AR = re.compile(r"[\u0600-\u06FF]")


def fetch(url, timeout=25):
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Accept-Language": "ar,en;q=0.8",
        "Accept": "text/html,application/xhtml+xml",
    })
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read().decode("utf-8", "replace")


def extract_paragraphs(html_text):
    raw = re.findall(r"<p[^>]*>(.*?)</p>", html_text, re.S)
    out = []
    for t in raw:
        t = html_lib.unescape(re.sub(r"<[^>]+>", "", t))
        t = re.sub(r"\s+", " ", t).strip()
        n_ar = len(AR.findall(t))
        if 80 <= len(t) <= 900 and n_ar >= 30:
            out.append(t)
    seen = set()
    dedup = []
    for p in out:
        if p not in seen:
            seen.add(p)
            dedup.append(p)
    return dedup[:40]


def article_text(link):
    body = "\n\n".join(extract_paragraphs(fetch(link)))
    return body[:7000]


def row_of(art):
    return {
        "guid": art["guid"],
        "title": art.get("title", ""),
        "summary": art.get("summary", ""),
        "link": art.get("link", ""),
        "image": art.get("image") or "",
        "category": art.get("category", ""),
        "source_id": art.get("source_id", ""),
        "source_name": art.get("source_name", ""),
        "source_cat": art.get("source_cat", ""),
        "published": art.get("published") or 0,
        "detail": art.get("detail") or "",
    }


def main():
    argv = sys.argv[1:]
    limit = int(argv[0]) if argv and argv[0].isdigit() else None

    if not os.path.exists(NEWS_JSON):
        print("لا توجد بيانات؛ شغّل المحرك أولاً.")
        return

    with open(NEWS_JSON, "r", encoding="utf-8") as fh:
        news = json.load(fh)

    guids = [a["guid"] for a in news["articles"]]
    cloud = db.existing(guids, "guid,detail")
    pending = []
    reused = 0
    for art in news["articles"]:
        gid = art["guid"]
        if gid in cloud and cloud[gid].get("detail"):
            art["detail"] = cloud[gid]["detail"]
            reused += 1
            continue
        if art.get("detail"):
            continue
        pending.append(art)

    if limit:
        pending = pending[:limit]

    if not pending:
        print(f"كل المقالات نصها الكامل جاهز (استُعيدت {reused} من Supabase).")
        return

    def job(art):
        try:
            return art, article_text(art["link"])
        except Exception:
            return art, ""

    done = 0
    failed = 0
    buffer = []
    with ThreadPoolExecutor(max_workers=8) as ex:
        futs = {ex.submit(job, a): a for a in pending}
        for fut in as_completed(futs):
            art, text = fut.result()
            art["detail"] = text
            if text:
                done += 1
            else:
                failed += 1
            buffer.append(art)
            if len(buffer) >= 20:
                db.upsert([row_of(a) for a in buffer])
                buffer = []
            time.sleep(random.uniform(0, 0.3))
    if buffer:
        db.upsert([row_of(a) for a in buffer])

    all_rows = [row_of(a) for a in news["articles"]]
    for i in range(0, len(all_rows), 50):
        db.upsert(all_rows[i:i + 50])

    with open(NEWS_JSON, "w", encoding="utf-8") as fh:
        json.dump(news, fh, ensure_ascii=False, indent=1)

    ready = len([a for a in news["articles"] if a.get("detail")])
    print(f"جُلب النص الكامل الآن: {done}   (تعذّر/أُغلق: {failed})   مُستعاد من السحابة: {reused}")
    print(f"إجمالي بنص كامل جاهز للصياغة: {ready}/{len(news['articles'])}")


if __name__ == "__main__":
    main()