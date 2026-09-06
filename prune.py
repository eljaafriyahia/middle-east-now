import sys

import db

if sys.platform == "win32" and hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

if __name__ == "__main__":
    deleted = db.delete_older_than(3)
    if db.enabled():
        print(f"حُذفت {deleted} خبر أقدم من 3 أيام (التحديث الجديد يتكوّن الآن).")
    else:
        print("(قاعدة Supabase غير مضبوطة - لا يوجد حذف للانتهاء. الحذف التلقائي يعمل بعد ضبطها).")