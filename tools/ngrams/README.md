# Built-in context model (app/src/main/assets/ngrams.txt)

`build.py <corpus-dir> <out> <words.txt>` builds next-word tables from:
- DailyDialog (everyday dialogues) — licence CC BY-NC-SA 4.0 (non-commercial; replace before selling the app)
- NLTK twitter_samples (positive/negative tweets), NLTK nps_chat, NLTK webtext (overheard, singles)
- `business.txt`: shop / WhatsApp / Hinglish phrases (weighted ×25)

Format: `B<TAB>prev<TAB>total<TAB>w:c w:c …` and `T<TAB>prev2 prev<TAB>total<TAB>w:c …` ("^" = sentence start).
