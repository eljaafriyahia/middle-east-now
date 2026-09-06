# -*- coding: utf-8 -*-
"""تنظيف دوري للنصوص المخزنة في Supabase من الضوضاء (اقرأ أيضًا/روابط/وسوم...)."""
import sys
import urllib.parse

if sys.platform == "win32" and hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

import clean
import db


def main():
    rows = db._req("GET", "news", params="select=guid,detail&limit=1000")
    if not isinstance(rows, list):
        print("لا يمكن الوصول إلى السحابة.")
        return

    changed = []
    for r in rows:
        detail = r.get("detail") or ""
        if not detail:
            continue
        new = clean.clean_text(detail)
        if new != detail:
            changed.append((r["guid"], new))

    for guid, new in changed:
        q = urllib.parse.quote(guid, safe="")
        db._req("PATCH", "news", params="guid=eq." + q, body={"detail": new})

    print(f"فحص: {len(rows)}    نُظّف: {len(changed)}")


if __name__ == "__main__":
    main()