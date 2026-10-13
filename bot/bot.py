"""Telegram requests only. Never controls the phone or receives its photos."""
import json
import os
import time
import urllib.request


def api(token, method, **params):
    data = json.dumps(params).encode()
    request = urllib.request.Request(
        f"https://api.telegram.org/bot{token}/{method}",
        data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=40) as response:
        result = json.load(response)
    if not result.get("ok"):
        raise RuntimeError("Telegram rejected the request")
    return result["result"]


def handle(update, call, operator_id, owner_id, last_request):
    message = update.get("message", {})
    callback = update.get("callback_query")
    if callback:
        context = callback.get("message", {})
        allowed = (callback.get("from", {}).get("id") == operator_id
                   and context.get("chat", {}).get("id") == operator_id
                   and callback.get("data") == "request_photo")
        call("answerCallbackQuery", callback_query_id=callback["id"],
             text="درخواست بررسی شد." if allowed else "دسترسی ندارید.")
        if allowed:
            now = time.monotonic()
            if now - last_request[0] < 60:
                call("sendMessage", chat_id=operator_id,
                     text="بین دو درخواست حداقل یک دقیقه صبر کنید.")
                return
            call("sendMessage", chat_id=owner_id, text=(
                "درخواست عکس از فرد مجاز دریافت شد.\n"
                "هیچ عکسی خودکار گرفته نمی‌شود. اگر موافقید، برنامهٔ «عکس با رضایت» "
                "را خودتان باز کنید، عکس بگیرید، پیش‌نمایش را بررسی کنید و با انتخاب "
                "تلگرام و گیرنده ارسال کنید. می‌توانید این درخواست را نادیده بگیرید."))
            last_request[0] = now
            call("sendMessage", chat_id=operator_id,
                 text="درخواست فرستاده شد. گرفتن و ارسال عکس فقط با تصمیم صاحب گوشی است.")
        return
    sender = message.get("from", {}).get("id")
    chat = message.get("chat", {})
    if chat.get("type") != "private" or sender != chat.get("id"):
        return
    if message.get("text", "").split("@")[0] == "/start":
        if sender == operator_id:
            call("sendMessage", chat_id=operator_id,
                 text="این دکمه فقط درخواست عکس می‌فرستد؛ کنترل دوربین ندارد.",
                 reply_markup={"inline_keyboard": [[{
                     "text": "درخواست عکس با رضایت", "callback_data": "request_photo"}]]})
        elif sender == owner_id:
            call("sendMessage", chat_id=owner_id,
                 text="درخواست‌ها اینجا می‌آیند. پاسخ دادن اختیاری است؛ "
                      "عکس فقط با اقدام شما در برنامه گرفته و ارسال می‌شود.")


def main():
    token = os.environ["8647910126:AAFXHUAVgZPf8cV_A2zZtltiUQ6pNDSqCFM"]
    operator_id = int(os.environ["8815460142"])
    owner_id = int(os.environ["8815460142"])
    if operator_id <= 0 or owner_id <= 0:
        raise ValueError("Only private Telegram user IDs are allowed")
    call = lambda method, **params: api(token, method, **params)
    # Discard old requests on startup, so restarting cannot replay them.
    call("deleteWebhook", drop_pending_updates=True)
    offset = 0
    last_request = [float("-inf")]
    print("Consent request bot started. No camera access.")
    while True:
        try:
            updates = call("getUpdates", offset=offset, timeout=25,
                           allowed_updates=["message", "callback_query"])
            for update in updates:
                offset = update["update_id"] + 1
                try:
                    handle(update, call, operator_id, owner_id, last_request)
                except Exception:
                    # No sensitive identifiers or token-bearing URLs in logs.
                    print("Request failed; check Telegram access and configuration.")
        except Exception:
            print("Connection failed; retrying shortly.")
            time.sleep(5)


if __name__ == "__main__":
    main()
