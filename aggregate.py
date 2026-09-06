import json
import os
import re
import sys
import time
import urllib.request

import feedparser

import sources

if sys.platform == "win32" and hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(ROOT, "data")
NEWS_JSON = os.path.join(DATA_DIR, "news.json")
MAX_AGE_SECONDS = 7 * 24 * 60 * 60

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MiddleEastNow/1.0"

CATEGORIES = {
    "سياسة": ["رئيس", "وزير", "حكومة", "انتخاب", "برلمان", "دبلوماسي", "سياسي", "قمة", "قرار", "مجلس", "منظمة", "حزب"],
    "اقتصاد": ["اقتصاد", "بترول", "نفط", "دولار", "عملة", "بورصة", "استثمار", "تضخم", "صناعة", "تجارة", "ميزانية"],
    "رياضة": ["كرة", "كأس", "مباراة", "الدوري", "لاعب", "مدرب", "أولمبياد", "بطولة", "رياضي"],
    "تكنولوجيا": ["تكنولوجيا", "تقنية", "ذكاء اصطناعي", "إنترنت", "تطبيق", "هاتف", "سيبراني", "رقمي", "فضاء"],
    "صحة": ["صحة", "طبي", "علاج", "مرض", "لقاح", "غذائي", "السرطان"],
    "أمن": ["أمن", "جيش", "عسكري", "قصف", "غارات", "هجوم", "انفجار", "توتر", "صراع", "قتال"],
}


def _now():
    return int(time.time())


def strip_html(text):
    if not text:
        return ""
    text = re.sub(r"<[^>]+>", " ", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def get_article_time(entry):
    for attr in ("published_parsed", "updated_parsed"):
        parsed = getattr(entry, attr, None)
        if parsed:
            return time.mktime(parsed)
    return None


def guess_image(entry):
    candidates = []
    for attr in ("media_thumbnail", "media_content"):
        for item in getattr(entry, attr, []) or []:
            url = item.get("url")
            if url:
                candidates.append(url)
    for enc in getattr(entry, "enclosures", []) or []:
        if "image" in (enc.get("type") or ""):
            candidates.append(enc.get("href") or enc.get("url"))
    for link in getattr(entry, "links", []) or []:
        if (link.get("type") or "").startswith("image"):
            candidates.append(link.get("href"))
    for candidate in candidates:
        if candidate and not candidate.endswith(".gif"):
            return candidate
    return None


def normalize_key(title):
    t = re.sub(r"[^\u0600-\u06FF0-9A-Za-z ]", "", title or "")
    t = re.sub(r"(عاجل|مباشر|فيديو|صور|الآن|بالفيديو|بالصور)", "", t)
    t = re.sub(r"\s+", " ", t).strip()
    return t[:60]


def categorize(title):
    for cat, words in CATEGORIES.items():
        if any(w in title for w in words):
            return cat
    return "أخبار"


def title_similarity(a, b):
    if not a or not b:
        return 0.0
    pa = set(a.split())
    pb = set(b.split())
    if not pa or not pb:
        return 0.0
    return (2 * len(pa & pb)) / (len(pa) + len(pb))


def fetch_entries(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=20) as resp:
        raw = resp.read()
    return feedparser.parse(raw).entries


def article_from(entry, source):
    title = strip_html(getattr(entry, "title", ""))
    link = getattr(entry, "link", "") or ""
    if not title or not link:
        return None
    published = get_article_time(entry)
    if published and (_now() - published) > MAX_AGE_SECONDS:
        return None
    return {
        "guid": getattr(entry, "id", "") or link,
        "title": title,
        "link": link,
        "summary": strip_html(getattr(entry, "summary", ""))[:300],
        "image": guess_image(entry),
        "published": int(published) if published else None,
        "source_id": source["id"],
        "source_name": source["name"],
        "source_en": source["name_en"],
        "source_cat": source["category"],
    }


def dedupe(articles):
    unique = []
    keys = []
    for art in articles:
        key = normalize_key(art["title"])
        if any(title_similarity(key, existing) >= 0.72 for existing in keys):
            continue
        keys.append(key)
        unique.append(art)
    return unique


def main():
    os.makedirs(DATA_DIR, exist_ok=True)
    all_articles = []
    for source in sources.SOURCES:
        grabbed = []
        for attempt in range(4):
            try:
                entries = fetch_entries(source["url"])
                grabbed = [a for a in (article_from(e, source) for e in entries) if a]
                if grabbed or attempt == 3:
                    break
                time.sleep(4 + attempt * 4)
            except Exception as exc:
                if attempt == 3:
                    grabbed = []
                    print(f"  FAIL {source['id']:>14} -> {str(exc)[:120]}")
                    break
                time.sleep(4 + attempt * 4)
        if not grabbed:
            print(f"  ZERO {source['id']:>12} -> لا أخبار حاليًا (قد يكون ضغط مؤقت)")
            continue
        all_articles.extend(grabbed)
        print(f"  OK  {source['id']:>14} -> {len(grabbed)} مقال")
        time.sleep(1.5)

    deduped = dedupe(all_articles)
    for art in deduped:
        art["category"] = categorize(art["title"])
    deduped.sort(key=lambda a: a["published"] or 0, reverse=True)

    payload = {
        "site": "الشرق الأوسط الآن",
        "site_en": "Middle East Now",
        "generated": _now(),
        "count": len(deduped),
        "sources": [
            {"id": s["id"], "name": s["name"], "name_en": s["name_en"], "category": s["category"]}
            for s in sources.SOURCES
        ],
        "articles": deduped,
    }
    with open(NEWS_JSON, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=1)

    print(f"الإجمالي قبل إزالة التكرار: {len(all_articles)}")
    print(f"بعد إزالة التكرار: {len(deduped)}")
    print(f"محفوظ في: {NEWS_JSON}")


if __name__ == "__main__":
    main()