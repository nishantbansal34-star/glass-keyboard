# Builds the next-word model (ngrams.txt).
#   python3 build.py OUT WORDS.txt src1 src2 ...   where src = kind:path:weight
# kinds: dd (DailyDialog), tweets (NLTK twitter json), nps (NLTK nps_chat dir), webtext (file), lines (one sentence per line),
#        gb2 / gb3 (Google Books ngram csv "ngram,freq")
import json, re, os, sys, collections, glob
OUT, WORDS = sys.argv[1], sys.argv[2]
vocab = {}
for line in open(WORDS, encoding='utf-8'):
    t = line.rstrip('\n').split('\t')
    if len(t) == 2: vocab[t[0]] = int(t[1])
EXTRA = set("i'm don't it's that's can't i'll you're didn't i've won't isn't doesn't let's what's there's i'd he's she's we're they're haven't wasn't aren't couldn't wouldn't shouldn't".split())
uni = collections.Counter(); bi = collections.Counter(); tri = collections.Counter()
tok = re.compile(r"[a-z]+(?:'[a-z]+)?|[.!?]")
def norm(s):
    s = s.replace('’', "'").replace('‘', "'").lower()
    s = re.sub(r"\bi m\b", "i'm", s)
    s = re.sub(r"(\w) n't\b", r"\1n't", s)
    s = re.sub(r"(\w) '(s|re|ve|ll|d|m)\b", r"\1'\2", s)
    return s
def feed(text, w):
    chunk = []
    def flush(chunk, end):
        if not chunk: return
        seq = ['^'] + chunk + ([end] if end in ('?', '!') else [])
        for i in range(1, len(seq)):
            bi[(seq[i-1], seq[i])] += w
            if i >= 2: tri[(seq[i-2], seq[i-1], seq[i])] += w
        for x in chunk: uni[x] += w
    for t in tok.findall(norm(text)):
        if t in '.!?': flush(chunk, t); chunk = []
        elif t in vocab or t in EXTRA: chunk.append(t)
        else: flush(chunk, None); chunk = []
    flush(chunk, None)
gb_bi = collections.Counter(); gb_tri = collections.Counter(); gb_w = {}
for src in sys.argv[3:]:
    kind, path, w = src.split(':'); w = float(w)
    if kind == 'dd':
        for f in glob.glob(path + '/*/dialogues.txt'):
            for line in open(f, encoding='utf-8', errors='ignore'):
                for u in line.split('__eou__'): feed(u, w)
    elif kind == 'tweets':
        for f in ['positive_tweets.json', 'negative_tweets.json']:
            for line in open(path + '/' + f, encoding='utf-8'):
                feed(re.sub(r'https?://\S+|@\w+|#\w+|RT\b', '', json.loads(line)['text']), w)
    elif kind == 'nps':
        for f in glob.glob(path + '/*.xml'):
            for m in re.finditer(r'<Post[^>]*>([^<]*)', open(f, encoding='utf-8', errors='ignore').read()): feed(m.group(1), w)
    elif kind == 'webtext':
        for line in open(path, encoding='utf-8', errors='ignore'):
            if ':' in line[:40]: line = line.split(':', 1)[1]
            feed(line, w)
    elif kind == 'lines':
        for line in open(path, encoding='utf-8'): feed(line, w)
    elif kind in ('gb2', 'gb3'):
        for line in list(open(path, encoding='utf-8'))[1:]:
            ng, c = line.rsplit(',', 1); parts = ng.lower().split()
            if not all(p in vocab or p in EXTRA for p in parts): continue
            (gb_bi if kind == 'gb2' else gb_tri)[tuple(parts)] += int(c)
            gb_w[kind] = w
# blend Google Books counts at a fixed share of the conversational mass
for counter, gb, k in ((bi, gb_bi, 'gb2'), (tri, gb_tri, 'gb3')):
    if gb:
        share = gb_w[k] * max(1, sum(counter.values())) / sum(gb.values())
        for key, c in gb.items(): counter[key] += max(1, round(c * share))
print('tokens', sum(uni.values()), 'bigrams', len(bi), 'trigrams', len(tri))
def top(counter, keylen, K, minc):
    g = collections.defaultdict(list)
    for k, c in counter.items():
        if c >= minc: g[k[:keylen]].append((c, k[keylen]))
    out = {}
    for p, l in g.items():
        l.sort(reverse=True); tot = sum(c for c, _ in l)
        if tot < minc * 2: continue
        out[p] = (tot, l[:K])
    return out
B = top(bi, 1, 10, 2); T = top(tri, 2, 6, 3)
with open(OUT, 'w', encoding='utf-8') as f:
    for p, (tot, l) in sorted(B.items()): f.write('B\t%s\t%d\t%s\n' % (p[0], tot, ' '.join('%s:%d' % (w, round(c)) for c, w in l)))
    for p, (tot, l) in sorted(T.items()): f.write('T\t%s %s\t%d\t%s\n' % (p[0], p[1], tot, ' '.join('%s:%d' % (w, round(c)) for c, w in l)))
print('B', len(B), 'T', len(T), 'bytes', os.path.getsize(OUT))
for p in ['^', 'thank', 'please', 'bhai', 'order', 'kal', 'kitne', 'bhej', 'payment', 'you']:
    if (p,) in B: print(' ', p, '->', [w for c, w in B[(p,)][1][:8]])
for p in [('how', 'are'), ('are', 'you'), ('thank', 'you'), ('kya', 'haal'), ('let', 'me'), ('is', 'this')]:
    if p in T: print(' ', p, '->', [w for c, w in T[p][1][:6]])
