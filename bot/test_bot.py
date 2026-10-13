import unittest
from bot import handle


class ConsentTests(unittest.TestCase):
    def setUp(self):
        self.calls = []
        self.call = lambda method, **kw: self.calls.append((method, kw))
        self.last = [float("-inf")]

    def callback(self, sender=10, chat=10):
        return {"callback_query": {"id": "example", "from": {"id": sender},
                "message": {"chat": {"id": chat}}, "data": "request_photo"}}

    def test_authorized_request_is_text_only(self):
        handle(self.callback(), self.call, 10, 20, self.last)
        self.assertEqual([m for m, p in self.calls],
                         ["answerCallbackQuery", "sendMessage", "sendMessage"])
        self.assertEqual(self.calls[1][1]["chat_id"], 20)

    def test_unauthorized_user_cannot_request(self):
        handle(self.callback(sender=99), self.call, 10, 20, self.last)
        self.assertEqual(len(self.calls), 1)

    def test_group_callback_cannot_request(self):
        handle(self.callback(chat=-123), self.call, 10, 20, self.last)
        self.assertEqual(len(self.calls), 1)

    def test_repeated_request_is_rate_limited(self):
        handle(self.callback(), self.call, 10, 20, self.last)
        handle(self.callback(), self.call, 10, 20, self.last)
        owner_messages = [p for m, p in self.calls
                          if m == "sendMessage" and p["chat_id"] == 20]
        self.assertEqual(len(owner_messages), 1)

    def test_unknown_start_gets_no_reply(self):
        handle({"message": {"text": "/start", "from": {"id": 99},
                            "chat": {"id": 99, "type": "private"}}},
               self.call, 10, 20, self.last)
        self.assertEqual(self.calls, [])


if __name__ == "__main__":
    unittest.main()
