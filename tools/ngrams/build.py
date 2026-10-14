import json, re, os, sys, collections, glob, math
D=sys.argv[1]; OUT=sys.argv[2]; WORDS=sys.argv[3]
vocab={}
for line in open(WORDS,encoding='utf-8'):
    t=line.rstrip('\n').split('\t')
    if len(t)==2: vocab[t[0]]=int(t[1])
sents=[]   # (weight, sentence)
# DailyDialog
for f in glob.glob(D+'/laed/data/daily_dialog/*/dialogues.txt'):
    for line in open(f,encoding='utf-8',errors='ignore'):
        for u in line.split('__eou__'):
            u=u.strip()
            if u: sents.append((1,u))
# tweets (strip mentions/urls/hashtags)
for f in ['positive_tweets.json','negative_tweets.json']:
    for line in open(D+'/twitter_samples/'+f,encoding='utf-8'):
        t=json.loads(line)['text']
        t=re.sub(r'https?://\S+|@\w+|#\w+|RT\b','',t)
        sents.append((1,t))
# chat
for f in glob.glob(D+'/nps_chat/*.xml'):
    for m in re.finditer(r'<Post[^>]*>([^<]*)',open(f,encoding='utf-8',errors='ignore').read()):
        sents.append((1,m.group(1)))
for f in ['overheard.txt','singles.txt']:
    for line in open(D+'/webtext/'+f,encoding='utf-8',errors='ignore'):
        if ':' in line[:40] and f=='overheard.txt': line=line.split(':',1)[1]
        sents.append((1,line))
for line in open(D+'/business.txt',encoding='utf-8'):
    sents.append((25,line))
uni=collections.Counter(); bi=collections.Counter(); tri=collections.Counter()
tok=re.compile(r"[a-z]+(?:'[a-z]+)?|[.!?]")
# normalise curly quotes and common chat shorthand
def norm(s):
    s=s.replace('’',"'").replace('‘',"'").lower()
    s=re.sub(r"\bi m\b","i'm",s)
    s=re.sub(r"(\w) n't\b",r"\1n't",s)   # do n't
    s=re.sub(r"(\w) '(s|re|ve|ll|d|m)\b",r"\1'\2",s)
    return s
for w,s in sents:
    toks=tok.findall(norm(s))
    chunk=[]
    def flush(chunk):
        if not chunk: return
        seq=['^']+chunk
        for i in range(1,len(seq)):
            a=seq[i-1]; b=seq[i]
            bi[(a,b)]+=w
            if i>=2: tri[(seq[i-2],a,b)]+=w
        for x in chunk: uni[x]+=w
    for t in toks:
        if t in '.!?':
            flush(chunk); chunk=[]
        else:
            # only words the keyboard knows (keeps names/typos out)
            if t in vocab or t in ("i'm","don't","it's","that's","can't","i'll","you're","didn't","i've","won't","isn't","doesn't","let's","what's","there's","i'd","he's","she's","we're","they're","haven't","wasn't","aren't","couldn't","wouldn't","shouldn't"):
                chunk.append(t)
            else:
                flush(chunk); chunk=[]   # break context at unknown tokens
    flush(chunk)
print('sentences',len(sents),'tokens',sum(uni.values()),'bigrams',len(bi),'trigrams',len(tri))
# per-prev top lists
def top(counter,keylen,K,minc):
    g=collections.defaultdict(list)
    for k,c in counter.items():
        if c>=minc: g[k[:keylen]].append((c,k[keylen]))
    out={}
    for p,l in g.items():
        l.sort(reverse=True)
        tot=sum(c for c,_ in l)
        if tot<minc*2: continue
        out[p]=(tot,l[:K])
    return out
B=top(bi,1,10,2); T=top(tri,2,6,3)
with open(OUT,'w',encoding='utf-8') as f:
    for p,(tot,l) in sorted(B.items()):
        f.write('B\t%s\t%d\t%s\n'%(p[0],tot,' '.join('%s:%d'%(w,c) for c,w in l)))
    for p,(tot,l) in sorted(T.items()):
        f.write('T\t%s %s\t%d\t%s\n'%(p[0],p[1],tot,' '.join('%s:%d'%(w,c) for c,w in l)))
print('B',len(B),'T',len(T),'bytes',os.path.getsize(OUT))
for p in ['^','thank','i','how','please','bhai','order','see','let','what','good','kal','happy','will','you']:
    if (p,) in B: print(p,'->',[w for c,w in B[(p,)][1][:8]])
for p in [('thank','you'),('i','will'),('how','are'),('please','send'),('let','me'),('^','i')]:
    if p in T: print(p,'->',[w for c,w in T[p][1][:6]])
