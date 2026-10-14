# Generates an original, commercially-safe conversational corpus (written for Glass Keys).
# Output: one sentence per line. Templates are expanded with slot fillers.
import itertools, random, sys
random.seed(7)
S = []
def add(*xs):
    for x in xs: S.append(x)
def T(tpl, **slots):
    keys = list(slots)
    for combo in itertools.product(*[slots[k] for k in keys]):
        s = tpl
        for k, v in zip(keys, combo): s = s.replace('{' + k + '}', v)
        S.append(s)

people = ["mom", "dad", "bhai", "didi", "my wife", "my brother", "my sister", "my friend", "the customer", "the supplier", "him", "her", "them", "you"]
times = ["today", "tomorrow", "tonight", "this evening", "in the morning", "after lunch", "next week", "on Monday", "on Sunday", "at 5", "at 7 pm", "later", "soon", "in ten minutes", "in an hour"]
places = ["home", "the shop", "the office", "the market", "the bank", "the station", "the mall", "the hospital", "the godown", "Delhi", "Agra", "the warehouse"]
items = ["balloons", "the order", "the parcel", "the bill", "the photos", "the catalogue", "the price list", "the samples", "the documents", "the payment", "the money", "the stock", "the goods", "the details", "the address", "the invoice"]
verbs_send = ["send", "share", "forward", "check", "confirm", "pack", "deliver", "bring", "collect", "pick up"]
feel = ["good", "fine", "great", "tired", "busy", "happy", "better", "okay", "sick", "excited"]
foods = ["lunch", "dinner", "breakfast", "tea", "coffee", "chai", "food"]

# --- greetings & small talk
add("Hi.", "Hello.", "Hey.", "Hi, how are you?", "Hello, how are you doing?", "Hey, what's up?", "Good morning.", "Good morning, have a nice day.",
    "Good afternoon.", "Good evening.", "Good night.", "Good night, sweet dreams.", "Good night, take care.", "How are you?", "How are you doing?",
    "How was your day?", "How is everyone at home?", "How is your health now?", "What are you doing?", "What's going on?", "Where are you?",
    "Are you free now?", "Are you there?", "Can you talk now?", "Long time no see.", "Nice to meet you.", "Nice to hear from you.",
    "I am fine, thank you.", "I'm fine, thanks.", "I'm good, how about you?", "All good here.", "Everything is fine.", "Not bad.", "Same here.",
    "Take care.", "Take care of yourself.", "See you soon.", "See you later.", "See you tomorrow.", "See you then.", "Talk to you later.", "Bye.", "Bye, take care.")
T("I am {f} today.", f=feel); T("I'm feeling {f} now.", f=feel); T("Are you feeling {f} now?", f=["better", "okay", "fine"])
T("Good {t}.", t=["morning", "afternoon", "evening", "night", "luck", "job", "news", "idea"])
# --- thanks / sorry / wishes
add("Thank you.", "Thank you so much.", "Thank you very much.", "Thanks a lot.", "Thanks for your help.", "Thank you for your help.", "Thank you for everything.",
    "Thank you for the update.", "Thank you for your support.", "Thanks for letting me know.", "Thank you for coming.", "Thank you for your time.",
    "No problem.", "No worries.", "You're welcome.", "My pleasure.", "Anytime.", "Sorry.", "Sorry for the late reply.", "Sorry, I missed your call.",
    "Sorry for the delay.", "Sorry for the inconvenience.", "I am sorry.", "I'm really sorry.", "Sorry, I can't make it today.", "Sorry, I was busy.",
    "Happy birthday!", "Happy birthday, have a wonderful year ahead!", "Many many happy returns of the day!", "Happy anniversary!", "Happy new year!",
    "Happy Diwali!", "Happy Holi!", "Eid Mubarak!", "Merry Christmas!", "Congratulations!", "Congratulations on your success!", "Congrats!",
    "Best wishes.", "All the best.", "All the best for your exam.", "Good luck.", "Get well soon.", "Wish you a speedy recovery.", "Stay safe.",
    "Happy Diwali to you and your family!", "Wishing you and your family a very happy Diwali!", "Happy birthday, have a great day!")
T("Thank you for {x}.", x=["your order", "the gift", "your message", "the wishes", "the payment", "your feedback", "shopping with us", "your patience", "calling", "the invite"])
T("Happy birthday {p}!", p=["bhai", "didi", "dear", "buddy", "sir", "my friend"])
# --- plans & time
T("I will call you {t}.", t=times); T("I'll call you {t}.", t=times); T("Call me {t}.", t=times); T("Let's meet {t}.", t=times)
T("I will be there {t}.", t=times); T("I'll reach {p} {t}.", p=places, t=["soon", "in ten minutes", "by 6", "tomorrow", "in an hour"])
T("I am at {p}.", p=places); T("I'm going to {p}.", p=places); T("I am on my way to {p}.", p=places); T("Are you at {p}?", p=places)
T("Can we meet {t}?", t=times); T("Can you come {t}?", t=times); T("What time are you coming {t}?", t=["today", "tomorrow", "tonight"])
T("Please call me when you {x}.", x=["are free", "reach", "get time", "see this", "wake up"])
add("On my way.", "I'm on my way.", "I am coming.", "Coming.", "Reached.", "I have reached.", "Reached home.", "Just reached.", "I'm almost there.",
    "Wait for me.", "Please wait.", "Please wait for five minutes.", "I'll be late.", "I will be late today.", "Running late, sorry.", "Start now.",
    "What time?", "At what time?", "Where should we meet?", "When will you come?", "When are you coming?", "When will you be free?",
    "Let me know when you are free.", "Let me know.", "Let me know if you need anything.", "Let me know if you need anything else.",
    "Let me check.", "Let me check and get back to you.", "Let me see.", "Let me think.", "I'll let you know.", "I will let you know.",
    "I'll get back to you.", "I will get back to you soon.", "I'll call you back.", "I will call you back.", "Call me back.", "Please call me back.",
    "I'm busy right now.", "I am in a meeting.", "I'm driving, will call you later.", "Can't talk now, will call you later.", "Busy now, call later.")
# --- requests / actions
T("Please {v} {i}.", v=verbs_send, i=items); T("Please {v} {i} {t}.", v=["send", "share", "deliver"], i=["the order", "the parcel", "the bill", "the photos", "the details"], t=["today", "tomorrow", "by evening", "as soon as possible"])
T("I will {v} {i} {t}.", v=["send", "share", "check", "deliver"], i=["the order", "the bill", "the details", "the photos", "the payment"], t=["today", "tomorrow", "in some time", "by evening"])
T("Can you {v} {i}?", v=verbs_send, i=items); T("Did you {v} {i}?", v=["send", "check", "receive", "get", "see"], i=items)
T("Have you {v} {i}?", v=["sent", "checked", "received", "seen", "paid"], i=items)
T("I have {v} {i}.", v=["sent", "shared", "checked", "received", "paid", "forwarded"], i=items)
T("Please send me {x}.", x=["the details", "your address", "the location", "a photo", "the price", "the bill", "the catalogue", "the list", "your number"])
T("Send me {x}.", x=["the details", "the location", "the photos", "your address", "the list"])
add("Please send it.", "Please send it today.", "Please send it as soon as possible.", "As soon as possible please.", "Please do it today.",
    "Please check.", "Please check once.", "Please check and confirm.", "Please confirm.", "Please reply.", "Please reply soon.", "Please call me.",
    "Can you help me?", "Can you do me a favour?", "Can you please check?", "Could you please send it again?", "Do you have time?",
    "Is it possible?", "Is it done?", "Is everything okay?", "Are you sure?", "Really?", "Seriously?", "What happened?", "Why?", "How?", "What?",
    "What do you think?", "What do you mean?", "What should I do?", "Do you want anything?", "Do you need anything?", "Is it okay?",
    "Okay.", "Ok.", "Okay, done.", "Okay, no problem.", "Okay, sounds good.", "Sounds good.", "Sure.", "Sure, no problem.", "Of course.",
    "Yes.", "Yes, please.", "Yes, sure.", "No.", "No, thanks.", "Not now.", "Maybe.", "Maybe later.", "I think so.", "I don't think so.",
    "I don't know.", "I know.", "I understand.", "I see.", "Got it.", "Noted.", "Done.", "Great.", "Great, thanks.", "Perfect.", "Nice.",
    "That's great.", "That's nice.", "That's good news.", "That's okay.", "It's okay.", "It's fine.", "Don't worry.", "Don't worry about it.",
    "I miss you.", "Miss you.", "Love you.", "I love you.", "Proud of you.", "I am proud of you.", "Have fun.", "Enjoy.", "Enjoy your day.",
    "Have a nice day.", "Have a great day.", "Have a good night.", "Have a safe journey.", "Happy journey.", "Drive safe.", "Reach safely.")
T("Did you have {f}?", f=foods); T("Have you had {f}?", f=foods); T("Let's have {f} together.", f=foods); T("I am having {f}.", f=foods)
T("I need {x}.", x=["help", "some time", "money", "a break", "your help", "the details", "it today", "more stock"])
T("I want to {x}.", x=["talk to you", "meet you", "order", "buy this", "know the price", "check the stock", "return this", "go home"])
T("Do you want to {x}?", x=["come", "meet", "talk", "order", "go out", "watch a movie", "join"])
T("I think {x}.", x=["so", "it's fine", "it will work", "you are right", "we should go", "it's better", "it's a good idea"])
T("Tell {p} to call me.", p=people[:10]); T("Ask {p} to call me.", p=people[:10]); T("Did {p} call?", p=people[:10])
# --- shop / business (English)
T("What is the price of {x}?", x=["this", "this item", "these balloons", "the set", "100 pieces", "one dozen", "one packet", "the costume"])
T("What is the {x}?", x=["price", "rate", "wholesale rate", "best price", "delivery charge", "total", "final amount", "minimum order"])
T("How much for {x}?", x=["this", "100 pieces", "one packet", "the full set", "two dozen", "50 pieces"])
T("Is {x} available?", x=["this", "it", "this colour", "this size", "the red one", "cash on delivery", "home delivery"])
T("Your order {x}.", x=["has been dispatched", "is confirmed", "has been delivered", "will be delivered tomorrow", "is ready", "is packed", "is on the way", "will reach in 3 to 4 days"])
T("Payment {x}.", x=["received", "done", "received, thank you", "is pending", "has been sent", "is due"])
T("Please make the payment {x}.", x=["today", "on this UPI ID", "on this number", "by evening", "before dispatch"])
T("We {x}.", x=["are open from 10 am to 9 pm", "are closed on Tuesday", "do home delivery", "also do bulk orders", "will dispatch today", "have new stock", "will deliver tomorrow"])
add("Is this available in stock?", "Yes, it is available.", "Sorry, it is out of stock.", "It will be available next week.", "New stock has arrived.",
    "Please share the catalogue.", "I will send you the catalogue.", "Please check the catalogue.", "Please share your address.", "Please send your full address with pincode.",
    "Please send the payment screenshot.", "I have sent the payment.", "I have made the payment.", "Payment done, please check.", "Please send the bill.",
    "Please send the GST bill.", "Please share your GST number.", "The bill has been sent.", "When will you deliver?", "When will it be delivered?",
    "When will I get my order?", "Where is my order?", "Order confirmed.", "Please confirm the order.", "I want to place an order.", "I want to order this.",
    "Please cancel the order.", "The order has been cancelled.", "I want to return this.", "Please replace it.", "We will replace it.", "Refund has been processed.",
    "Have you received the parcel?", "Yes, I have received it.", "Not yet received.", "Please check the tracking link.", "Cash on delivery is available.",
    "Delivery is free above 999.", "Minimum order is 50 pieces.", "Please visit our shop.", "Please visit our website.", "Thank you for shopping with us.",
    "Please give us a review on Google.", "Your feedback means a lot to us.", "Which colour do you want?", "What size do you need?", "How many pieces do you need?",
    "Can you give a discount?", "Please give the best price.", "This is the best price.", "Final price.", "Price is fixed.", "Rates have increased.",
    "Please send the photos of all the items.", "Please share the location of the shop.", "I have shared the location.", "Birthday decoration items are available.",
    "Balloons, party decorations and fancy dress are available.", "We have all kinds of party supplies.", "Wedding items are also available.")
# --- Hinglish
add("Bhai kya haal hai?", "Haan bhai, sab theek hai.", "Sab badhiya.", "Aap kaise ho?", "Aap kaise hain?", "Main theek hoon.", "Kya chal raha hai?",
    "Kya kar rahe ho?", "Kahan ho?", "Kab aaoge?", "Kab tak aaoge?", "Kitne baje aaoge?", "Main aa raha hoon.", "Main nikal gaya hoon.", "Pahunch gaya.",
    "Ghar pahunch gaya.", "Thodi der mein aata hoon.", "Bas 5 minute.", "Abhi aata hoon.", "Abhi busy hoon, baad mein call karta hoon.", "Baad mein baat karte hain.",
    "Kal milte hain.", "Kal baat karte hain.", "Theek hai.", "Theek hai bhai.", "Ok bhai.", "Ok ji.", "Haan ji.", "Ji haan.", "Nahi yaar.", "Koi baat nahi.",
    "Chinta mat karo.", "Ho jayega.", "Kar dunga.", "Kar diya.", "Bhej diya.", "Bhej dunga.", "Bhej do.", "Bhej dena.", "Jaldi bhejo.", "Jaldi karo.",
    "Kitne ka hai?", "Kitne ka diya?", "Kya rate hai?", "Rate kya hai?", "Best rate lagao.", "Thoda kam karo.", "Isse kam nahi hoga.", "Final rate batao.",
    "Maal aa gaya hai.", "Maal ready hai.", "Maal kab aayega?", "Order bhej diya hai.", "Order kab tak aayega?", "Kal tak aa jayega.", "Aaj shaam tak bhej denge.",
    "Payment kar diya hai.", "Payment kar do.", "Paise bhej diye hain.", "Paise kab doge?", "Bill bhej do.", "Bill bhej diya hai.", "Photo bhejo.", "Photo bhej do.",
    "Location bhej do.", "Address bhej do.", "Number bhej do.", "Dukaan pe aa jao.", "Dukaan band hai.", "Dukaan khuli hai.", "Main dukaan pe hoon.",
    "Shop pe aa jao.", "Ghar pe hoon.", "Khana kha liya?", "Khana khaya?", "Chai peeni hai?", "Chalo milte hain.", "Chalo theek hai.", "Bahut badhiya.",
    "Bahut accha.", "Bahut bahut dhanyavaad.", "Dhanyavaad.", "Shukriya.", "Shukriya bhai.", "Sorry yaar.", "Maaf karna.", "Kya hua?", "Sab theek?",
    "Tension mat lo.", "Pakka?", "Pakka.", "Sach mein?", "Accha theek hai.", "Haan theek hai.", "Mujhe nahi pata.", "Pata nahi.", "Dekhta hoon.",
    "Check karke batata hoon.", "Batao.", "Mujhe batao.", "Mujhe call karo.", "Call karna.", "Call kar lena.", "Message kar dena.", "Yaad se bhej dena.")
T("{p} ko bol do.", p=["bhai", "didi", "mummy", "papa", "unko", "usko"]); T("Kal {x} bhej dena.", x=["order", "maal", "bill", "photo", "paise", "parcel"])
T("{x} bhej do.", x=["order", "maal", "bill", "photo", "paise", "parcel", "list", "rate list", "catalogue", "sample"])
T("{x} kab tak aayega?", x=["order", "maal", "parcel", "payment"]); T("Aaj {x} ho jayega.", x=["kaam", "order", "payment", "delivery"])
random.shuffle(S)
open(sys.argv[1], 'w', encoding='utf-8').write('\n'.join(S) + '\n')
print(len(S), 'sentences')
