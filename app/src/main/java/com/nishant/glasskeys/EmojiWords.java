package com.nishant.glasskeys;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Words → emoji, for emoji search and for offering an emoji while you type (English + Hinglish). */
public class EmojiWords {
    private static final Map<String, String[]> MAP = new LinkedHashMap<>();

    private static void e(String words, String emojis) {
        String[] em = emojis.split(" ");
        for (String w : words.split(",")) {
            w = w.trim();
            String[] old = MAP.get(w);
            if (old == null) MAP.put(w, em);
            else {
                LinkedHashSet<String> s = new LinkedHashSet<>();
                for (String x : old) s.add(x);
                for (String x : em) s.add(x);
                MAP.put(w, s.toArray(new String[0]));
            }
        }
    }

    static {
        // feelings & faces
        e("happy,glad,smile,smiling,khush,khushi", "😊 😄 😁 🙂 😃");
        e("laugh,laughing,lol,haha,hahaha,funny,lmao,rofl,hasi", "😂 🤣 😆 😹");
        e("love,loving,pyaar,pyar,ishq,mohabbat,dil,heart", "❤️ 😍 🥰 😘 💕 💖");
        e("kiss,kisses", "😘 😚 💋");
        e("sad,unhappy,dukh,udaas,upset", "😢 😞 😔 🥺 💔");
        e("cry,crying,rona", "😭 😢 🥲");
        e("angry,mad,gussa,annoyed,irritated", "😠 😡 🤬 😤");
        e("wow,omg,shocked,surprised,amazing", "😮 😲 🤯 😱 🤩");
        e("cool,awesome,mast,badhiya,zabardast", "😎 🔥 🤩 👌 💯");
        e("think,thinking,hmm,sochna,doubt", "🤔 🧐 💭");
        e("sleep,sleepy,sleeping,tired,neend,goodnight,night", "😴 🥱 💤 🌙 😪");
        e("sick,ill,fever,bimar,bukhar", "🤒 🤧 😷 🤕");
        e("shy,blush,sharam", "😊 ☺️ 🙈");
        e("cute,sweet,adorable", "🥰 🥹 😊 🍬");
        e("wink,naughty", "😉 😜 😏");
        e("cool,chill,relax", "😎 😌 🧘");
        e("scared,afraid,dar,fear", "😨 😱 😰");
        e("confused,what", "😕 🤨 🤷");
        e("sorry,maaf,oops,apologies", "🙏 😔 😅 🙇");
        e("nervous,awkward", "😅 😬");
        e("crazy,pagal,silly", "🤪 😜 🙃");
        e("sick,vomit,disgusting,yuck", "🤢 🤮");
        e("party,celebrate,celebration,celebrating,jashn", "🎉 🥳 🎊 🍾 🪩");
        e("yes,haan,ok,okay,okk,theek,thik,sure,done,agreed,accha,acha", "👍 👌 ✅ 🙂");
        e("no,nahi,nope,nahin", "🙅 ❌ 👎 🚫");
        e("thanks,thank,thankyou,thanku,dhanyavad,shukriya,grateful", "🙏 😊 💐 🤝");
        e("please,plz,pls,kripya", "🙏 🥺");
        e("namaste,namaskar,pranam,greetings", "🙏 😊");
        e("hello,hi,hey,hii,bye,goodbye,tata,wave", "👋 😊 🙋");
        e("welcome,swagat", "🤗 🙏 💐");
        e("congrats,congratulations,badhai,mubarak,mubarakbaad,shabash,bravo,well", "🎉 👏 🥳 🎊 💐");
        e("clap,applause", "👏 🙌");
        e("strong,strength,power,gym,workout,fitness", "💪 🏋️ 🔥");
        e("pray,prayer,bless,blessing,blessings,god,bhagwan,ashirwad", "🙏 😇 ✨ 🕉️");
        e("hug,hugs", "🤗 🫂");
        e("good,great,nice,best,perfect,excellent,superb,badiya", "👍 👌 💯 ⭐ 🔥");
        e("bad,worst,bura", "👎 😞");
        e("fire,lit,hot,garam", "🔥 🥵");
        e("cold,thanda,winter,sardi", "🥶 ❄️ 🧣");
        e("hundred,100,perfect", "💯");
        e("star,stars,rating", "⭐ 🌟 ✨");
        e("sparkle,magic,shine,new,naya", "✨ 🌟 💫");
        e("money,paisa,paise,rupees,rupaye,rupay,cash,payment,pay,paid,rich,amount,price,rate", "💰 💵 🪙 💸 ₹");
        e("bank,upi", "🏦 💳 📲");
        e("card", "💳");
        e("gift,gifts,present,tohfa", "🎁 🎀");
        e("birthday,bday,janamdin,janmdin", "🎂 🎉 🎈 🥳 🎁");
        e("cake", "🎂 🍰 🧁");
        e("balloon,balloons,gubbara,gubbare,gubbaare", "🎈 🎉");
        e("wedding,shaadi,shadi,marriage,vivah,bride,dulhan,groom,dulha", "💍 👰 🤵 💒 🎊");
        e("ring,engagement,sagai", "💍 💎");
        e("anniversary", "💑 💐 🎉");
        e("diwali,deepawali,diya,deepak", "🪔 ✨ 🎆");
        e("holi,colour,colours,color,colors,rang", "🎨 🌈 💦");
        e("rakhi,rakshabandhan", "🧵 👫 🎁");
        e("christmas,xmas,santa", "🎄 🎅 🎁");
        e("newyear,new year", "🎆 🎉 🥂");
        e("eid", "🌙 ✨ 🤲");
        e("flower,flowers,phool,rose,gulab,bouquet", "🌹 💐 🌸 🌷 🌺");
        e("music,song,gaana,dance,naach", "🎵 🎶 💃 🕺");
        e("decoration,decor,sajawat,sajavat", "🎊 🎈 ✨ 🎀");
        e("costume,fancy,dress,kapde,kapda,clothes", "👗 👔 🎭 🦸");
        e("mask", "🎭 😷");
        // food
        e("food,khana,khaana,lunch,dinner,hungry,bhookh,bhuk", "🍽️ 😋 🍛 🍲");
        e("breakfast,nashta", "🍳 🥞 ☕");
        e("tea,chai,chaay", "☕ 🫖");
        e("coffee", "☕");
        e("pizza", "🍕"); e("burger", "🍔"); e("fries", "🍟"); e("icecream,ice cream", "🍦 🍨"); e("chocolate", "🍫");
        e("sweet,sweets,mithai,laddu,ladoo", "🍬 🍭 🍮");
        e("fruit,fruits,phal,apple,seb", "🍎 🍏 🍓 🍉"); e("mango,aam", "🥭"); e("banana,kela", "🍌");
        e("water,paani,pani", "💧 🥤 🚰"); e("drink,drinks,juice", "🥤 🧃 🍹"); e("beer,cheers", "🍻 🥂"); e("milk,doodh", "🥛");
        e("rice,chawal", "🍚"); e("bread,roti", "🍞 🫓"); e("egg,anda", "🥚 🍳");
        // things & business
        e("shop,dukan,dukaan,store,business,dhanda,vyapar", "🏪 🛍️ 🏬");
        e("shopping,buy,kharid,kharidari", "🛍️ 🛒");
        e("order,orders,booking", "📦 🧾 ✅");
        e("delivery,deliver,parcel,courier,shipping,dispatch,dispatched", "📦 🚚 🛵");
        e("box,packet,packing,package", "📦 🎁");
        e("bill,invoice,receipt,gst", "🧾 📄");
        e("offer,sale,discount,deal", "🏷️ 💥 🔥");
        e("phone,call,mobile,number", "📞 📱 ☎️");
        e("message,msg,chat,whatsapp,text", "💬 📩 📲");
        e("email,mail", "📧 ✉️");
        e("photo,pic,picture,selfie,camera", "📸 🤳 🖼️");
        e("video", "🎥 📹");
        e("location,address,map,pata", "📍 🗺️");
        e("home,ghar,house", "🏠 🏡");
        e("office,work,kaam,job,naukri", "💼 🏢 👨‍💻");
        e("meeting", "🤝 📅 💼");
        e("deal,handshake,partner,partnership", "🤝");
        e("time,samay,clock,late,der", "⏰ ⌛ 🕒");
        e("wait,ruko,intezar", "⏳ ✋");
        e("fast,quick,jaldi,hurry,urgent", "⚡ 🏃 ⏩");
        e("car,gaadi,gadi,drive", "🚗 🚙"); e("bike,scooter", "🏍️ 🛵"); e("bus", "🚌"); e("train,rail", "🚆 🚉"); e("flight,plane,travel,trip,safar", "✈️ 🧳 🌍");
        e("idea,tip", "💡"); e("book,books,kitab,study,padhai,exam", "📚 📖 ✏️ 📝");
        e("school,college,class", "🏫 🎒 📚"); e("win,winner,jeet,trophy,champion", "🏆 🥇 🎉");
        e("cricket", "🏏"); e("football,soccer", "⚽"); e("game,games,khel", "🎮 🎲");
        e("india,bharat,hindustan", "🇮🇳"); e("flag,jhanda", "🇮🇳 🏳️");
        e("sun,sunny,dhoop,morning,subah,goodmorning", "☀️ 🌞 🌅");
        e("rain,baarish,barish,monsoon", "🌧️ ☔ 🌦️"); e("moon,chand", "🌙 🌕");
        e("dog,kutta,puppy", "🐶 🐕"); e("cat,billi", "🐱 🐈"); e("baby,bachha,baccha,beta,beti,kid,kids,bachche", "👶 🍼 🧸");
        e("mom,mummy,maa,mother", "👩 ❤️"); e("dad,papa,father", "👨 ❤️"); e("family,parivar", "👨‍👩‍👧 🏡");
        e("friend,friends,dost,yaar,bro,bhai", "🤝 🫂 😎"); e("girl,ladki", "👧"); e("boy,ladka", "👦");
        e("check,tick,correct,sahi,right", "✅ ✔️"); e("wrong,galat,cross", "❌ ✖️"); e("warning,careful,dhyan", "⚠️");
        e("question,sawal", "❓ 🤔"); e("up,upar", "⬆️ 👆"); e("down,neeche", "⬇️ 👇"); e("point,this,here,yahan", "👉 👈 👇");
        e("eyes,look,see,dekho", "👀"); e("secret,quiet,chup", "🤫"); e("lock,password,safe", "🔒 🔐"); e("key,chabi", "🔑");
        e("rocket,launch,growth", "🚀 📈"); e("chart,sales,profit,growth", "📈 📊 💹"); e("loss", "📉");
        e("calendar,date,tareekh,schedule", "📅 🗓️"); e("tomorrow,kal,today,aaj", "📅");
        e("medicine,dawai,doctor,hospital", "💊 🩺 🏥");
        e("good night,gn", "🌙 😴 💤"); e("gm", "☀️ 🌞");
    }

    /** Emoji for a whole typed word (for the suggestion bar), best first; empty if none. */
    private static final java.util.Set<String> QUIET = new java.util.HashSet<>(java.util.Arrays.asList(
            "this", "here", "up", "down", "what", "new", "time", "card", "key", "well", "point", "check", "right", "date",
            "kal", "aaj", "today", "tomorrow", "pata", "number", "text", "see", "look", "class", "bad", "no", "ok", "okay",
            "yes", "sure", "done", "work", "kaam", "pay", "price", "rate", "amount", "order", "bill", "call", "message", "mail",
            "home", "ghar", "wait", "late", "der", "fast", "best", "good", "great", "nice", "hi", "hey", "bhai", "dost", "yaar",
            "accha", "acha", "theek", "thik", "haan", "nahi", "beta", "maa", "papa", "mom", "dad", "friend", "friends", "baby",
            "box", "deal", "drive", "safe", "power", "this", "eyes", "shop", "store", "business", "job", "office", "phone", "mobile"));

    public static String[] forWord(String word) {
        if (word == null || word.length() < 2 || QUIET.contains(word.toLowerCase())) return new String[0];
        String[] r = MAP.get(word.toLowerCase());
        return r == null ? new String[0] : r;
    }

    /** Emoji search: words starting with the query first, then words containing it. */
    public static List<String> search(String query, int max) {
        List<String> out = new ArrayList<>();
        String q = query.trim().toLowerCase();
        if (q.isEmpty()) return out;
        LinkedHashSet<String> set = new LinkedHashSet<>();
        String[] exact = MAP.get(q);
        if (exact != null) for (String s : exact) set.add(s);
        for (Map.Entry<String, String[]> e : MAP.entrySet())
            if (e.getKey().startsWith(q)) for (String s : e.getValue()) set.add(s);
        if (q.length() >= 3) for (Map.Entry<String, String[]> e : MAP.entrySet())
            if (e.getKey().contains(q)) for (String s : e.getValue()) set.add(s);
        for (String s : set) { out.add(s); if (out.size() >= max) break; }
        return out;
    }
}
