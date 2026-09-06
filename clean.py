# -*- coding: utf-8 -*-
"""تنقية محتوى الخبر من الضوضاء: صناديق "اقرأ أيضًا"، قوائم الروابط، الوسوم..."""
import html as html_lib
import re

AR = re.compile(r"[\u0600-\u06FF]")
LINKY = re.compile(r"<a[^>]*>.*?</a>", re.S)
PURE_URL = re.compile(r"^\s*(?:https?|www\.)\S+\s*$", re.I)

NOISE = [
    r"اقرأ\s*(أيضًا|أيضا|المزيد|مزيد|أكثر|ذلك|الخبر)",
    r"أيضًا:\s*شاهد",
    r"(مقالات|مواضيع|أخبار|عناوين)\s*(ذات|ذو|ذات)\s*صلة",
    r"مقالات\s*صلة",
    r"ذات\s*صلة",
    r"الأكثر\s*(قراءة|مشاركة|متابعة|تداولاً|تداولا)",
    r"الأخبار\s*الأكثر\s*قراءة",
    r"أكثر\s*الموضوعات\s*قراءة",
    r"شاهد\s*(أيضًا|أيضا|المزيد|الفيديو|الخبر)",
    r"شاهد\s*فيديو\s*(متعلق|مرتبط)",
    r"فيديو\s*(متعلق|مرتبط|مصاحب)",
    r"مواضيع\s*مشابهة",
    r"روابط\s*مصاحبة|روابط\s*ذات\s*صلة",
    r"نشرة\s*(إخبارية|بريدية)",
    r"اشترك\s*(في|معنا|بقناتنا)",
    r"شارك(نا)?\s*(الخبر|هذا|المقال|الخبر|المنشور)",
    r"أنشر\s*(الخبر|المقال)",
    r"حفظ\s*الخبر",
    r"أضف\s*تعليقا?",
    r"وسوم|كلمات\s*مفتاحية|الوسوم",
    r"جميع\s*الحقوق\s*محفوظة|الملكية\s*الفكرية|حقوق\s*النشر",
    r"\u00a9\s*\d{4}|\(\s*c\)\s*\d{4}",
    r"للمزيد\s*من\s*الأخبار|للمزيد\s*تفضل|لمزيد\s*من\s*المقالات",
    r"لمشاهدة\s*المزيد|لمتابعة\s*المزيد",
    r"طالع\s*(أيضًا|أيضا)|وانظر\s*أيضًا|وفي\s*سياق\s*متصل",
    r"بالتعاون\s*مع",
    r"هل\s*تريد\s*معرفة\s*المزيد",
    r"مقالات\s*مقترحة\s*لك",
    r"اقتراحات\s*لك",
    r"تابع(ونا)?\s*(على|وحساباتنا)",
    r"قنوات(نا)?\s*التواصل|حساباتنا\s*الرسمية",
    r"انضموا?\s*إلى",
    r"للأخبار\s*العاجلة",
    r"آخر\s*التحديثات\s*على\s*قنوات",
    r"top\s*news|trending|related\s*(articles|news|stories)",
    r"read\s*(more|next)|also\s*read",
    r"more\s*from|you\s*may\s*(also|like)|best\s*of",
    r"recommended\s*(for\s*you)?",
    r"share\s*(this|the|on)|tweet|whatsapp|telegram\s*channel",
    r"follow\s*us\s*on|copyright|all\s*rights\s*reserved",
    r"subscribe\s*(to|now)|newsletter",
    r"\btags:|keywords:|source:\s*http",
    r"loading\s*\.\.\.",
]

NOISE_RE = re.compile(r"|".join(NOISE), re.I)

STRONG_END = [
    r"اقرأ\s*(أيضًا|أيضا|المزيد|مزيد)",
    r"(مقالات|مواضيع|أخبار)\s*(ذات|ذو)\s*صلة",
    r"روابط\s*مصاحبة",
    r"الأكثر\s*قراءة",
    r"\btags:",
    r"كلمات\s*مفتاحية",
    r"RT\s+STORIES",
    r"أخبار\s*ذات\s*صلة",
    r"المزيد\s*من\s*الأخبار\s*ثم",
]
STRONG_END_RE = re.compile(r"|".join(STRONG_END), re.I)

LABEL = [
    r"RT\s+STORIES",
]
LABEL_RE = re.compile(r"|".join(LABEL), re.I)


def is_noise(text):
    return bool(NOISE_RE.search(text))


def _plain(frag):
    t = html_lib.unescape(re.sub(r"<br\s*/?>\s*", " ", frag, flags=re.I))
    t = html_lib.unescape(re.sub(r"<[^>]+>", "", t))
    return re.sub(r"\s+", " ", t).strip()


def link_density(frag):
    links = "".join(LINKY.findall(frag))
    n = len(html_lib.unescape(re.sub(r"<[^>]+>", "", links)))
    return n / max(len(frag), 1)


def extract_clean_paragraphs(html_text, max_paras=40):
    raw = re.findall(r"<p[^>]*>(.*?)</p>", html_text, re.S)
    out = []
    in_article = False
    skip_next = False
    for t in raw:
        if link_density(t) > 0.45 and len(re.sub(r"<[^>]+>", "", t)) < 200:
            continue
        p = _plain(t)
        n_ar = len(AR.findall(p))
        if not p:
            continue
        if PURE_URL.match(p):
            continue
        if LABEL_RE.search(p):
            if in_article:
                break
            skip_next = True
            continue
        if skip_next:
            skip_next = False
            continue
        if len(p) < 7:
            continue
        if is_noise(p):
            if STRONG_END_RE.search(p):
                if in_article:
                    break
            continue
        if not in_article:
            if len(p) >= 90 and n_ar >= 20:
                in_article = True
                out.append(p)
            continue
        if STRONG_END_RE.search(p):
            break
        if len(p) < 45 and not re.search(r"[.!؟?，。!\u2026]$", p):
            continue
        if len(p) < 25 or len(p) > 900:
            continue
        if n_ar >= 20:
            out.append(p)
    return out[:max_paras]


def clean_text(text):
    """تنظيف نص مخزون سابقًا (بدون وسوم): يزيل الأسطر الضوضائية والروابط والفراغات."""
    if not text:
        return text
    chunks = re.split(r"\n\s*\n", text)
    kept = []
    for c in chunks:
        c = re.sub(r"\s+", " ", c).strip()
        if not c:
            continue
        if len(c) < 6:
            continue
        if PURE_URL.match(c):
            continue
        if is_noise(c):
            continue
        kept.append(c)
    joined = "\n\n".join(kept)
    if len(joined) < 60:
        return text
    return joined[:7000]