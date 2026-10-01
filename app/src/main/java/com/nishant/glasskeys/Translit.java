package com.nishant.glasskeys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Hinglish → हिंदी. Turns roman spellings like "aap kaise ho" into Devanagari (आप कैसे हो), fully offline:
 * a list of common words first, then phonetic rules for everything else, then whatever you have picked before.
 */
public class Translit {

    // ------------------------------------------------------------------ common words (roman -> Hindi)
    private static final Map<String, String> COMMON = new HashMap<>();
    private static void w(String dev, String... romans) { for (String r : romans) if (!COMMON.containsKey(r)) COMMON.put(r, dev); }
    static {
        w("है", "hai", "he"); w("हैं", "hain", "hai'n", "hein"); w("मैं", "main", "mai", "mein'"); w("में", "mein", "me", "mei");
        w("क्या", "kya", "kyaa"); w("आप", "aap", "ap"); w("कैसे", "kaise", "kese", "kaisey"); w("कैसा", "kaisa", "kesa");
        w("कैसी", "kaisi", "kesi"); w("हो", "ho"); w("नहीं", "nahi", "nahin", "nai", "nhi", "nahee");
        w("की", "ki"); w("का", "ka"); w("के", "ke"); w("को", "ko"); w("से", "se"); w("पर", "par", "pe"); w("भी", "bhi");
        w("तुम", "tum"); w("हम", "hum", "ham"); w("ये", "ye", "yeh"); w("यह", "yah"); w("वो", "wo", "woh", "vo", "voh"); w("वह", "vah", "wah");
        w("कब", "kab"); w("कहाँ", "kahan", "kaha'n", "kaha"); w("क्यों", "kyun", "kyon", "kyu", "kyo"); w("अच्छा", "accha", "acha", "achha", "achchha");
        w("अच्छी", "acchi", "achi", "achhi"); w("अच्छे", "acche", "ache", "achhe"); w("ठीक", "theek", "thik", "thick'");
        w("धन्यवाद", "dhanyavad", "dhanyawad", "dhanyvad"); w("शुक्रिया", "shukriya", "sukriya"); w("नमस्ते", "namaste", "namastey");
        w("जी", "ji", "jee"); w("भाई", "bhai", "bhaii"); w("बहन", "behen", "bahan"); w("पैसा", "paisa", "pesa"); w("पैसे", "paise", "pese");
        w("रुपये", "rupaye", "rupay", "rupees", "rupye"); w("रुपया", "rupaya"); w("सामान", "saman", "samaan"); w("कितना", "kitna", "kitnaa");
        w("कितने", "kitne"); w("कितनी", "kitni"); w("दुकान", "dukan", "dukaan"); w("कल", "kal"); w("आज", "aaj", "aj"); w("अभी", "abhi", "abhee");
        w("जल्दी", "jaldi"); w("भेज", "bhej"); w("भेजो", "bhejo"); w("भेजिए", "bhejiye", "bhejiyega"); w("भेजा", "bheja"); w("भेजें", "bhejen", "bhejein");
        w("दीजिए", "dijiye", "deejiye", "dijiyega"); w("कीजिए", "kijiye", "keejiye"); w("होगा", "hoga"); w("होगी", "hogi"); w("होंगे", "honge");
        w("था", "tha"); w("थी", "thi"); w("थे", "the"); w("रहा", "raha", "rha"); w("रही", "rahi", "rhi"); w("रहे", "rahe", "rhe");
        w("गया", "gaya", "gya"); w("गई", "gayi", "gai"); w("गए", "gaye", "gye"); w("लिए", "liye", "liya'"); w("कुछ", "kuch", "kuchh", "kucch");
        w("सब", "sab"); w("सबको", "sabko"); w("बहुत", "bahut", "bohot", "bahot", "bhut", "bohat"); w("प्यार", "pyaar", "pyar");
        w("घर", "ghar"); w("लोग", "log"); w("बात", "baat", "bat'"); w("काम", "kaam", "kam'"); w("दिन", "din"); w("रात", "raat", "rat'");
        w("मेरा", "mera"); w("मेरी", "meri"); w("मेरे", "mere"); w("तेरा", "tera"); w("तेरी", "teri"); w("तेरे", "tere");
        w("आपका", "aapka", "apka"); w("आपकी", "aapki", "apki"); w("आपके", "aapke", "apke"); w("आपको", "aapko", "apko");
        w("उनका", "unka"); w("उनकी", "unki"); w("उनके", "unke"); w("उनको", "unko"); w("उसका", "uska"); w("उसकी", "uski"); w("उसके", "uske");
        w("हमारा", "hamara", "humara"); w("हमारी", "hamari", "humari"); w("हमारे", "hamare", "humare"); w("तुम्हारा", "tumhara"); w("तुम्हारी", "tumhari");
        w("कौन", "kaun", "kon"); w("हाँ", "haan", "han", "ha"); w("ना", "na"); w("और", "aur", "or'"); w("लेकिन", "lekin"); w("पर", "per");
        w("क्योंकि", "kyunki", "kyonki", "kyuki"); w("अगर", "agar"); w("तो", "to", "toh"); w("फिर", "phir", "fir"); w("सिर्फ", "sirf");
        w("ज़रूर", "zaroor", "jaroor", "zarur", "jarur"); w("पहले", "pehle", "pahle"); w("बाद", "baad", "bad'"); w("साथ", "saath", "sath");
        w("वाला", "wala", "waala"); w("वाली", "wali", "waali"); w("वाले", "wale", "waale"); w("करो", "karo"); w("करना", "karna");
        w("करते", "karte"); w("करता", "karta"); w("करती", "karti"); w("कर", "kar"); w("करें", "karen", "karein"); w("करेंगे", "karenge");
        w("दिया", "diya"); w("लिया", "liya"); w("दे", "de"); w("दो", "do"); w("लो", "lo"); w("देखो", "dekho"); w("देख", "dekh");
        w("चलो", "chalo"); w("चल", "chal"); w("जाओ", "jao", "jaao"); w("जा", "ja"); w("आओ", "aao", "ao"); w("आ", "aa");
        w("बताओ", "batao", "bataao"); w("बताइए", "bataiye", "bataiyega"); w("बता", "bata"); w("समझ", "samajh", "samaj"); w("पता", "pata");
        w("माफ़", "maaf", "maf"); w("खुश", "khush", "kush"); w("दोस्त", "dost"); w("शादी", "shaadi", "shadi"); w("जन्मदिन", "janamdin", "janmdin");
        w("मुबारक", "mubarak"); w("बधाई", "badhai", "badhaai"); w("परिवार", "parivar", "pariwar"); w("मम्मी", "mummy", "mumy"); w("पापा", "papa");
        w("बेटा", "beta"); w("बेटी", "beti"); w("एक", "ek"); w("तीन", "teen"); w("चार", "char", "chaar"); w("पांच", "paanch", "panch");
        w("छह", "chhah", "chhe"); w("सात", "saat"); w("आठ", "aath"); w("नौ", "nau"); w("दस", "das"); w("सौ", "sau"); w("हज़ार", "hazaar", "hazar", "hajar");
        w("लाख", "lakh"); w("करोड़", "crore", "karod", "karor"); w("कृपया", "kripya", "kripaya"); w("ध्यान", "dhyan", "dhyaan");
        w("ज़्यादा", "zyada", "jyada", "jada", "zada"); w("कम", "kam"); w("बुरा", "bura"); w("नया", "naya"); w("नई", "nayi", "nai'");
        w("पुराना", "purana"); w("जैसे", "jaise", "jese"); w("वैसे", "waise", "vaise", "wese"); w("ऐसे", "aise", "ese"); w("ऐसा", "aisa", "esa");
        w("कहा", "kaha'"); w("बोला", "bola"); w("सुनो", "suno"); w("मिलते", "milte"); w("मिलना", "milna"); w("मिल", "mil"); w("मिलेगा", "milega");
        w("टाइम", "time"); w("प्लीज़", "please", "plz", "pls"); w("सॉरी", "sorry"); w("ओके", "ok", "okay"); w("थैंक्स", "thanks", "thanku");
        w("थैंक यू", "thankyou"); w("डिलीवरी", "delivery"); w("पेमेंट", "payment"); w("रेट", "rate"); w("स्टॉक", "stock"); w("माल", "maal", "mal");
        w("बिल", "bill"); w("ऑर्डर", "order"); w("फ़ोन", "phone", "fone"); w("नंबर", "number", "no'"); w("पार्टी", "party"); w("गुब्बारे", "gubbare", "gubbaare");
        w("सजावट", "sajawat", "sajavat"); w("डेकोरेशन", "decoration"); w("जन्मदिन", "birthday'"); w("हिंदी", "hindi"); w("भारत", "bharat");
        w("सुबह", "subah", "subha"); w("शाम", "shaam", "sham"); w("दोपहर", "dopahar", "dopehar"); w("बजे", "baje"); w("घंटे", "ghante"); w("मिनट", "minute", "minat");
        w("सही", "sahi", "sahee"); w("गलत", "galat"); w("ज़रा", "zara", "jara"); w("थोड़ा", "thoda", "thora"); w("थोड़ी", "thodi", "thori");
        w("बड़ा", "bada", "bara"); w("बड़ी", "badi", "bari"); w("बड़े", "bade", "bare"); w("छोटा", "chhota", "chota"); w("छोटी", "chhoti", "choti");
        w("लगा", "laga"); w("लगता", "lagta"); w("लगेगा", "lagega"); w("चाहिए", "chahiye", "chaiye", "chahie"); w("चाहते", "chahte"); w("चाहता", "chahta");
        w("सकते", "sakte"); w("सकता", "sakta"); w("सकती", "sakti"); w("गए", "gaye'"); w("आए", "aaye", "aye"); w("आया", "aaya", "aya");
        w("आई", "aayi", "aai"); w("जाना", "jana", "jaana"); w("आना", "aana", "ana"); w("खाना", "khana", "khaana"); w("पानी", "paani", "pani");
        w("चाय", "chai", "chaay"); w("दूध", "doodh", "dudh"); w("बच्चे", "bacche", "bachhe", "bachche"); w("बच्चा", "baccha", "bachha");
        w("यहाँ", "yahan", "yaha", "yahaan"); w("वहाँ", "wahan", "waha", "vahan"); w("इधर", "idhar"); w("उधर", "udhar"); w("अंदर", "andar");
        w("बाहर", "bahar", "baahar"); w("ऊपर", "upar", "oopar"); w("नीचे", "neeche", "niche"); w("आगे", "aage", "age'"); w("पीछे", "peeche", "piche");
        w("सच", "sach"); w("मत", "mat"); w("बस", "bas"); w("यार", "yaar", "yar"); w("दीदी", "didi", "deedi"); w("भैया", "bhaiya", "bhaiyya");
        w("अंकल", "uncle"); w("आंटी", "aunty", "anty"); w("सर", "sir"); w("मैडम", "madam", "mam"); w("उन्हें", "unhe", "unhen"); w("इसे", "ise");
        w("उसे", "use'"); w("इस", "is'"); w("उस", "us'"); w("इसलिए", "isliye", "isiliye"); w("मतलब", "matlab"); w("शायद", "shayad");
        w("हमेशा", "hamesha", "humesha"); w("कभी", "kabhi"); w("सभी", "sabhi"); w("कोई", "koi"); w("किसी", "kisi"); w("किसको", "kisko");
        w("क्या हुआ", "kyahua"); w("हुआ", "hua", "huaa"); w("हुई", "hui", "huyi"); w("हुए", "hue", "huye"); w("होता", "hota"); w("होती", "hoti");
        w("होते", "hote"); w("रखो", "rakho"); w("रख", "rakh"); w("लेना", "lena"); w("देना", "dena"); w("लेकर", "lekar"); w("देकर", "dekar");
        w("मन", "man'"); w("दिल", "dil"); w("जान", "jaan"); w("ख़ुशी", "khushi", "kushi"); w("दुख", "dukh"); w("मज़ा", "maza", "maja");
        w("राजा", "raja"); w("रानी", "rani"); w("किताब", "kitab", "kitaab"); w("गुलाब", "gulab"); w("चावल", "chawal", "chaawal");
        w("दिवाली", "diwali", "deepawali"); w("राखी", "rakhi"); w("लड़का", "ladka", "larka"); w("लड़की", "ladki", "larki"); w("कपड़ा", "kapda", "kapra");
        w("स्कूल", "school", "skool"); w("कॉलेज", "college"); w("ऑफिस", "office"); w("शॉप", "shop"); w("दुकानदार", "dukandar", "dukaandar");
        w("राम", "ram", "raam"); w("श्याम", "shyam"); w("लाभ", "labh"); w("शुभ", "shubh"); w("मिठाई", "mithai"); w("सपना", "sapna");
        w("गर्मी", "garmi"); w("सर्दी", "sardi"); w("धरती", "dharti"); w("बिजली", "bijli"); w("रोटी", "roti"); w("सब्ज़ी", "sabzi", "sabji");
        w("दाल", "daal", "dal"); w("नींद", "neend"); w("होली", "holi"); w("गणेश", "ganesh"); w("कृष्ण", "krishna"); w("मोहब्बत", "mohabbat");
        w("खुशबू", "khushboo", "khushbu"); w("पकोड़े", "pakode", "pakore"); w("नींबू", "nimbu", "neembu"); w("अदरक", "adrak"); w("लहसुन", "lehsun", "lahsun");
        w("तत्काल", "tatkal"); w("समोसा", "samosa"); w("जलेबी", "jalebi"); w("टमाटर", "tamatar"); w("शांति", "shanti"); w("चांदनी", "chandni");
        w("लकड़ी", "lakdi", "lakri"); w("बाज़ार", "bazaar", "bazar"); w("दिल्ली", "dilli", "delhi"); w("अलीगढ़", "aligarh"); w("पत्ता", "patta");
        w("गिफ्ट", "gift"); w("केक", "cake"); w("ड्रेस", "dress"); w("कपड़े", "kapde", "kapre"); w("रंग", "rang"); w("लाल", "laal", "lal");
        w("नीला", "neela", "nila"); w("हरा", "hara"); w("पीला", "peela", "pila"); w("सफ़ेद", "safed"); w("काला", "kaala", "kala"); w("गुलाबी", "gulabi");
    }

    // ------------------------------------------------------------------ phonetic rules
    private static final String[][] CONS = {
            {"ksh", "क्ष"}, {"chh", "छ"}, {"cch", "च्छ"}, {"gy", "ज्ञ!"}, // "gy" handled specially below (marker)
            {"kh", "ख"}, {"gh", "घ"}, {"ch", "च"}, {"jh", "झ"}, {"th", "थ"}, {"dh", "ध"}, {"ph", "फ"}, {"bh", "भ"},
            {"sh", "श"}, {"k", "क"}, {"g", "ग"}, {"c", "क"}, {"j", "ज"}, {"t", "त"}, {"d", "द"}, {"n", "न"},
            {"p", "प"}, {"b", "ब"}, {"m", "म"}, {"y", "य"}, {"r", "र"}, {"l", "ल"}, {"v", "व"}, {"w", "व"},
            {"s", "स"}, {"h", "ह"}, {"f", "फ़"}, {"z", "ज़"}, {"q", "क़"}, {"x", "क्स"},
    };
    private static final String[][] VOW = {   // roman, independent, matra
            {"aa", "आ", "ा"}, {"ai", "ऐ", "ै"}, {"au", "औ", "ौ"}, {"ee", "ई", "ी"}, {"ii", "ई", "ी"}, {"oo", "ऊ", "ू"},
            {"uu", "ऊ", "ू"}, {"ei", "ए", "े"}, {"ou", "औ", "ौ"}, {"a", "अ", ""}, {"i", "इ", "ि"}, {"u", "उ", "ु"},
            {"e", "ए", "े"}, {"o", "ओ", "ो"},
    };
    private static final Map<String, String> RETRO = new HashMap<>();
    static { RETRO.put("त", "ट"); RETRO.put("थ", "ठ"); RETRO.put("द", "ड"); RETRO.put("ध", "ढ"); }

    private static class U { boolean cons; String roman, dev, matra; }

    private static List<U> units(String w) {
        List<U> out = new ArrayList<>();
        int i = 0;
        outer:
        while (i < w.length()) {
            for (String[] v : VOW) if (w.startsWith(v[0], i)) {
                U u = new U(); u.cons = false; u.roman = v[0]; u.dev = v[1]; u.matra = v[2];
                out.add(u); i += v[0].length(); continue outer;
            }
            for (String[] c : CONS) if (w.startsWith(c[0], i)) {
                if (c[0].equals("gy")) continue;           // keep g + y (as in "gyaan" -> ग्यान is fine)
                U u = new U(); u.cons = true; u.roman = c[0]; u.dev = c[1];
                out.add(u); i += c[0].length(); continue outer;
            }
            return null;   // not a roman word
        }
        return out;
    }

    static final int HARD = 1, RETRO_D = 2, SHORT_I = 4, SPLIT_AI = 8, LONG_FIRST_A = 16, LONG_LAST_A = 32;

    /** Should two consonants in a row join as a half-letter (क्य, प्र, स्त, त्त) or stay separate (सपना)? */
    private static boolean joins(U c1, U c2, boolean clusterAtEnd, boolean wordStart) {
        if (wordStart || clusterAtEnd) return true;                       // "prem", "dost"
        if (c1.roman.equals(c2.roman)) return true;                       // "patta", "dilli"
        String r2 = c2.roman;
        if (r2.equals("y") || r2.equals("r") || r2.equals("v") || r2.equals("w")) return true;   // "kya", "mitra"
        if ((c1.roman.equals("s") || c1.roman.equals("sh")) && "t th k kh p ph m n l".contains(r2)) return true;  // "namaste"
        return false;
    }

    /** One phonetic reading of a roman word, shaped by the flags above. */
    static String rules(String w, int f) {
        List<U> us = units(w);
        if (us == null || us.isEmpty()) return null;
        boolean hard = (f & HARD) != 0, retro = (f & RETRO_D) != 0;
        int n = us.size();
        // which short "a" (after a consonant, not word-final) to lengthen, if any
        int firstA = -1, lastA = -1;
        for (int k = 1; k < n - 1; k++) {
            U u = us.get(k);
            if (!u.cons && u.roman.equals("a") && us.get(k - 1).cons) { if (firstA < 0) firstA = k; lastA = k; }
        }
        int longA = (f & LONG_FIRST_A) != 0 ? firstA : (f & LONG_LAST_A) != 0 ? lastA : -1;
        if (((f & (LONG_FIRST_A | LONG_LAST_A)) != 0) && longA < 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < n; k++) {
            U u = us.get(k);
            U prev = k > 0 ? us.get(k - 1) : null, next = k + 1 < n ? us.get(k + 1) : null;
            boolean last = k == n - 1;
            if (u.cons) {
                // final h after a long vowel is silent in Hinglish spelling: "toh", "woh", "yeh"
                if (last && u.roman.equals("h") && prev != null && !prev.cons && !prev.roman.equals("a")) continue;
                // n/m before a consonant -> anusvara: "hindi" -> हिंदी, "andar" -> अंदर
                if ((u.roman.equals("n") || (u.roman.equals("m") && next != null && (next.roman.startsWith("p") || next.roman.startsWith("b"))))
                        && prev != null && !prev.cons && next != null && next.cons
                        && !next.roman.equals("y") && !next.roman.equals("h") && !next.roman.equals("v")
                        && !next.roman.equals("w") && !next.roman.equals("r") && !next.roman.equals("n") && !next.roman.equals("m")) {
                    sb.append("ं");
                    continue;
                }
                // nasal ending: "hain" -> हैं, "mein" -> में, "kyon" -> क्यों
                if (last && u.roman.equals("n") && prev != null && !prev.cons && k >= 2
                        && (prev.roman.equals("ai") || prev.roman.equals("ei") || prev.roman.equals("e") || prev.roman.equals("o")
                        || prev.roman.equals("au") || prev.roman.equals("oo") || prev.roman.equals("ee"))) {
                    sb.append("ं");
                    continue;
                }
                String d = u.dev;
                if (retro) {
                    if (d.equals("द") || d.equals("ध")) {
                        boolean medial = prev != null && !(prev.cons && prev.roman.equals("n")) && !(prev.cons && next != null && next.cons);
                        d = d.equals("द") ? (medial ? "ड़" : "ड") : (medial ? "ढ़" : "ढ");
                    } else if (RETRO.containsKey(d)) d = RETRO.get(d);
                }
                sb.append(d);
                if (next != null && next.cons) {
                    boolean atEnd = k + 2 >= n || (us.get(k + 2).cons && k + 3 >= n);
                    if (hard || joins(u, next, atEnd, prev == null)) sb.append("्");
                }
            } else {
                boolean afterCons = prev != null && prev.cons;
                if (afterCons) {
                    if (u.roman.equals("a")) {
                        if ((last && n > 1) || k == longA) sb.append("ा");   // Hinglish: final a is long — "kya", "tha", "mera"
                    } else if (last && (f & SPLIT_AI) != 0 && u.roman.equals("ai")) {
                        sb.append("ाई");
                    } else if (last && u.roman.equals("i")) {
                        sb.append((f & SHORT_I) != 0 ? "ि" : "ी");
                    } else sb.append(u.matra);
                } else {
                    sb.append(u.dev);
                }
            }
        }
        return sb.toString();
    }

    public static boolean isRoman(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (c < 'a' || c > 'z') return false;
        }
        return true;
    }

    /**
     * Hindi spellings for a roman word, best first. picks = what you chose before (roman\u0001hindi -> count).
     * May contain the roman word itself if you usually keep it in English.
     */
    public static List<String> candidates(String roman, Map<String, Integer> picks) {
        List<String> out = new ArrayList<>();
        if (!isRoman(roman)) return out;
        String w = roman.toLowerCase();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (picks != null) {
            List<Map.Entry<String, Integer>> mine = new ArrayList<>();
            for (Map.Entry<String, Integer> e : picks.entrySet())
                if (e.getKey().startsWith(w + "\u0001")) mine.add(e);
            mine.sort((a, b) -> b.getValue() - a.getValue());
            for (Map.Entry<String, Integer> e : mine) set.add(e.getKey().substring(w.length() + 1));
        }
        String c = COMMON.get(w);
        if (c != null) set.add(c);
        boolean td = w.indexOf('t') >= 0 || w.indexOf('d') >= 0;
        int[] tries = {0, td ? RETRO_D : -1, w.endsWith("ai") ? SPLIT_AI : -1, LONG_FIRST_A, LONG_LAST_A, HARD,
                td ? RETRO_D | LONG_FIRST_A : -1, w.endsWith("i") ? SHORT_I : -1};
        for (int t : tries) {
            if (t < 0 || set.size() >= 4) continue;
            String r = rules(w, t);
            if (r != null) set.add(r);
        }
        for (String s : set) { out.add(s); if (out.size() >= 4) break; }
        return out;
    }

    public static String best(String roman, Map<String, Integer> picks) {
        List<String> c = candidates(roman, picks);
        return c.isEmpty() ? null : c.get(0);
    }

    // ------------------------------------------------------------------ Devanagari keyboard helpers
    public static boolean isConsonant(int cp) { return (cp >= 0x0915 && cp <= 0x0939) || (cp >= 0x0958 && cp <= 0x095F) || cp == 0x093C; }

    public static boolean isDevMark(int cp) { return (cp >= 0x093E && cp <= 0x094D) || cp == 0x0901 || cp == 0x0902 || cp == 0x0903 || cp == 0x093C; }

    /** A vowel sign typed with no consonant before it becomes the full vowel: ा -> आ, े -> ए. */
    public static String independentFor(String matra) {
        switch (matra) {
            case "ा": return "आ"; case "ि": return "इ"; case "ी": return "ई"; case "ु": return "उ"; case "ू": return "ऊ";
            case "े": return "ए"; case "ै": return "ऐ"; case "ो": return "ओ"; case "ौ": return "औ"; case "ृ": return "ऋ";
            case "ॉ": return "ऑ"; case "ॅ": return "ऍ";
        }
        return null;
    }
}
