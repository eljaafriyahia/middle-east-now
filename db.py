import json
import os
import sys
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CFG = os.path.join(ROOT, "data", "supabase_config.json")

if sys.platform == "win32" and hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

_config = None
_warned = False


def config():
    global _config
    if _config is None:
        try:
            with open(CFG, "r", encoding="utf-8") as fh:
                _config = json.load(fh)
        except Exception:
            _config = {}
        url = os.environ.get("SUPABASE_URL", "").strip() or str(_config.get("url", "")).strip()
        key = os.environ.get("SUPABASE_KEY", "").strip() or str(_config.get("key", "")).strip()
        valid = (
            url.startswith("https://")
            and "YOURPROJECT" not in url
            and len(key) > 30
            and (key.startswith("eyJ") or key.startswith("sb_secret") or key.startswith("sb_publishable"))
        )
        if not valid:
            _config = {}
        else:
            _config = {"url": url, "key": key, "table": "news"}
    return _config


def enabled():
    c = config()
    if not c:
        return False
    return True


def _warn_once():
    global _warned
    if not _warned:
        print("Supabase غير مضبوط بعد (data/supabase_config.json) - سيُدار الحفظ مُؤقتًا بدون سحابة.")
        print("1) أنشئ مشروع مجانًا على https://supabase.com")
        print("2) افتح SQL Editor والصق محتوى ملف  data/supabase.sql  وشغّله")
        print("3) انسخ Project URL + service_role key في data/supabase_config.json")
        _warned = True


def _req(method, path, params=None, body=None, prefer=None):
    c = config()
    if not c:
        _warn_once()
        return None
    url = c["url"].rstrip("/") + "/rest/v1/" + path
    if params:
        url += "?" + params
    headers = {
        "apikey": c["key"],
        "Authorization": "Bearer " + c["key"],
        "Content-Type": "application/json",
    }
    if prefer:
        headers["Prefer"] = prefer
    data = None
    if body is not None:
        data = json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode()
            return json.loads(raw) if raw and raw.strip() else {}
    except Exception as exc:
        print(f"Supabase ERROR ({method}) -> {str(exc)[:140]}")
        return None


def existing(guids, cols="guid,detail,body"):
    """يرجع {guid: {(col): value}} للمواقع الموجودة."""
    out = {}
    if not enabled():
        _warn_once()
        return out
    for i in range(0, len(guids), 90):
        chunk = guids[i:i + 90]
        quoted = ",".join(urllib.parse.quote(g, safe="") for g in chunk)
        res = _req("GET", config()["table"], params="select=" + cols + "&guid=in.(" + quoted + ")")
        if res:
            for row in res:
                out[row.get("guid")] = row
    return out


def upsert(rows):
    """inserte/تحديث قائمة صفوف (يُدمج حسب guid)."""
    if not rows:
        return True
    if not enabled():
        _warn_once()
        return False
    res = _req("POST", config()["table"], body=rows, prefer="resolution=merge-duplicates,return=minimal")
    return res is not None


def delete_older_than(days):
    """يحذف الأخبار الأقدم من days أيام. يرجع العدد المحذوف."""
    if not enabled():
        _warn_once()
        return 0
    import datetime
    cutoff = (datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(days=days)).strftime("%Y-%m-%dT%H:%M:%SZ")
    res = _req("DELETE", config()["table"], body={}, params="created_at=lt." + cutoff)
    if isinstance(res, dict):
        return res.get("count", 0)
    return 0