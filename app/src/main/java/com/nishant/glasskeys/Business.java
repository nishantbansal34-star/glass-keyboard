package com.nishant.glasskeys;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.nayuki.qrcodegen.QrCode;

/** Business tools: UPI payment requests + QR, shop-hours reply, and detecting details in copied text. */
public class Business {

    // ------------------------------------------------------------------ business profile (settings)

    public static String name(Prefs p) { return p.str("bizName", "").trim(); }
    public static String upi(Prefs p) { return p.str("bizUpi", "").trim(); }

    // ------------------------------------------------------------------ UPI

    public static String upiLink(String upiId, String payee, double amount, String note) {
        StringBuilder sb = new StringBuilder("upi://pay?pa=").append(Uri.encode(upiId, "@."));
        if (payee != null && !payee.isEmpty()) sb.append("&pn=").append(Uri.encode(payee));
        if (amount > 0) sb.append("&am=").append(String.format(Locale.US, "%.2f", amount));
        sb.append("&cu=INR");
        if (note != null && !note.isEmpty()) sb.append("&tn=").append(Uri.encode(note));
        return sb.toString();
    }

    /** The message typed into the chat for a payment request. */
    public static String paymentText(Prefs p, double amount) {
        String nm = name(p);
        StringBuilder sb = new StringBuilder();
        // WhatsApp formatting: *bold*, `code` (code also stops the number being turned into a phone link)
        sb.append("*Payment request").append(nm.isEmpty() ? "" : " · " + nm).append("*\n");
        if (amount > 0) sb.append("Amount: *₹").append(Calc.format(amount, true)).append("*\n");
        sb.append("UPI ID: `").append(upi(p)).append("`\n");
        sb.append("Scan the QR or pay to this UPI ID from GPay, PhonePe, Paytm or any UPI app.");
        return sb.toString();
    }

    static String rupees(double v) { return "₹" + Calc.format(Math.round(v * 100) / 100.0, true); }

    /** Your price list as a WhatsApp-ready message. */
    public static String priceListText(Prefs p, java.util.List<Prefs.Product> items) {
        String nm = name(p);
        StringBuilder sb = new StringBuilder();
        sb.append("*Price list").append(nm.isEmpty() ? "" : " · " + nm).append("*\n");
        for (Prefs.Product it : items) {
            sb.append("• ").append(it.name).append(" — ").append(rupees(it.price));
            if (!it.unit.isEmpty()) sb.append(" / ").append(it.unit);
            sb.append("\n");
        }
        String date = new java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ENGLISH).format(new java.util.Date());
        sb.append("_Rates as of ").append(date).append("_");
        return sb.toString();
    }

    /** Order summary with quantities and total. cart: product index -> quantity. */
    public static String orderText(Prefs p, java.util.List<Prefs.Product> items, java.util.Map<Integer, Integer> cart) {
        String nm = name(p);
        StringBuilder sb = new StringBuilder();
        sb.append("*Order summary").append(nm.isEmpty() ? "" : " · " + nm).append("*\n");
        double total = 0;
        int n = 1;
        for (java.util.Map.Entry<Integer, Integer> e : cart.entrySet()) {
            if (e.getKey() >= items.size() || e.getValue() <= 0) continue;
            Prefs.Product it = items.get(e.getKey());
            double line = it.price * e.getValue();
            total += line;
            sb.append(n++).append(". ").append(it.name).append(" × ").append(e.getValue());
            if (!it.unit.isEmpty()) sb.append(" ").append(it.unit);
            sb.append(" — ").append(rupees(line)).append("\n");
        }
        sb.append("*Total: ").append(rupees(total)).append("*");
        return sb.toString();
    }

    public static double orderTotal(java.util.List<Prefs.Product> items, java.util.Map<Integer, Integer> cart) {
        double t = 0;
        for (java.util.Map.Entry<Integer, Integer> e : cart.entrySet())
            if (e.getKey() < items.size()) t += items.get(e.getKey()).price * e.getValue();
        return Math.round(t * 100) / 100.0;
    }

    /** A clean white payment card: business name, amount, QR, UPI ID. */
    public static Bitmap qrCard(Prefs p, double amount, float density) {
        String link = upiLink(upi(p), name(p), amount, "");
        QrCode qr = QrCode.encodeText(link, QrCode.Ecc.MEDIUM);
        int W = 900, H = 1180, pad = 90;
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.WHITE);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setTextAlign(Paint.Align.CENTER);
        t.setColor(0xFF111318);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setTextSize(52);
        String nm = name(p).isEmpty() ? "Scan to pay" : name(p);
        c.drawText(nm, W / 2f, 110, t);
        t.setTypeface(Typeface.DEFAULT);
        t.setTextSize(amount > 0 ? 64 : 36);
        t.setColor(amount > 0 ? 0xFF111318 : 0xFF5B6070);
        c.drawText(amount > 0 ? "₹" + Calc.format(amount, true) : "Scan with any UPI app", W / 2f, 195, t);
        int size = qr.size, border = 2;
        float cell = (W - 2f * pad) / (size + 2 * border);
        float top = 240;
        Paint q = new Paint();
        q.setColor(0xFF111318);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                if (qr.getModule(x, y))
                    c.drawRect(pad + (x + border) * cell, top + (y + border) * cell,
                            pad + (x + border + 1) * cell + 0.5f, top + (y + border + 1) * cell + 0.5f, q);
        float qBottom = top + (size + 2 * border) * cell;
        t.setTextSize(36);
        t.setColor(0xFF111318);
        c.drawText("UPI: " + upi(p), W / 2f, qBottom + 60, t);
        t.setTextSize(28);
        t.setColor(0xFF8A8F9C);
        c.drawText("GPay · PhonePe · Paytm · BHIM", W / 2f, qBottom + 110, t);
        return b;
    }

    // ------------------------------------------------------------------ shop hours

    private static final String[] DAY = {"", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final String[] DAY_FULL = {"", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    /** Minutes since midnight from "10:00", "9:30", "20:00" (returns -1 if unreadable). */
    static int mins(String hhmm) {
        try {
            String[] a = hhmm.trim().split("[:.]");
            int h = Integer.parseInt(a[0].trim()), m = a.length > 1 ? Integer.parseInt(a[1].trim()) : 0;
            return h * 60 + m;
        } catch (Exception e) { return -1; }
    }

    static String pretty(int mins) {
        int h = mins / 60, m = mins % 60;
        String ap = h >= 12 ? "PM" : "AM";
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return m == 0 ? h12 + " " + ap : String.format(Locale.US, "%d:%02d %s", h12, m, ap);
    }

    static boolean closedOn(Prefs p, int dow) { return p.str("bizClosed", "1").contains(String.valueOf(dow)); }

    /** "Mon–Sat" style summary of open days. */
    static String openDays(Prefs p) {
        List<Integer> open = new ArrayList<>();
        int[] order = {2, 3, 4, 5, 6, 7, 1};
        for (int d : order) if (!closedOn(p, d)) open.add(d);
        if (open.size() == 7) return "Every day";
        if (open.isEmpty()) return "Closed";
        // contiguous run in Mon..Sun order?
        int first = -1, last = -1;
        boolean contiguous = true;
        for (int i = 0; i < order.length; i++) {
            if (!closedOn(p, order[i])) { if (first < 0) first = i; else if (last != i - 1) contiguous = false; last = i; }
        }
        if (contiguous && open.size() > 2) return DAY[order[first]] + "–" + DAY[order[last]];
        StringBuilder sb = new StringBuilder();
        for (int d : open) { if (sb.length() > 0) sb.append(", "); sb.append(DAY[d]); }
        return sb.toString();
    }

    public static String hoursText(Prefs p, Calendar now) {
        int open = mins(p.str("bizOpen", "10:00")), close = mins(p.str("bizClose", "20:00"));
        if (open < 0) open = 600;
        if (close < 0) close = 1200;
        String nm = name(p);
        StringBuilder sb = new StringBuilder();
        sb.append(nm.isEmpty() ? "Our timings" : nm + " timings").append(": ")
                .append(openDays(p)).append(", ").append(pretty(open)).append(" – ").append(pretty(close));
        StringBuilder closedList = new StringBuilder();
        for (int d = 1; d <= 7; d++) if (closedOn(p, d)) { if (closedList.length() > 0) closedList.append(", "); closedList.append(DAY_FULL[d]); }
        if (closedList.length() > 0 && closedList.toString().split(",").length < 7) sb.append(" (closed ").append(closedList).append(")");
        sb.append("\n");

        int dow = now.get(Calendar.DAY_OF_WEEK);
        int nowM = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (!closedOn(p, dow) && nowM >= open && nowM < close) {
            sb.append("We're open now (until ").append(pretty(close)).append(")");
        } else {
            // find the next opening
            String when = null;
            if (!closedOn(p, dow) && nowM < open) when = "today at " + pretty(open);
            else for (int i = 1; i <= 7; i++) {
                int d = ((dow - 1 + i) % 7) + 1;
                if (!closedOn(p, d)) { when = (i == 1 ? "tomorrow" : DAY_FULL[d]) + " at " + pretty(open); break; }
            }
            sb.append("We're closed right now").append(when != null ? " and open " + when : "");
        }
        String addr = p.str("bizAddress", "").trim(), maps = p.str("bizMaps", "").trim(), ph = p.str("bizPhone", "").trim();
        if (!addr.isEmpty()) sb.append("\nAddress: ").append(addr);
        if (!maps.isEmpty()) sb.append("\nLocation: ").append(maps);
        if (!ph.isEmpty()) sb.append("\nCall / WhatsApp: ").append(ph);
        return sb.toString();
    }

    // ------------------------------------------------------------------ orders

    /** "Balloons × 2, Banner × 1" from the cart. */
    public static String cartSummary(java.util.List<Prefs.Product> items, java.util.Map<Integer, Integer> cart) {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<Integer, Integer> e : cart.entrySet()) {
            if (e.getKey() >= items.size()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(items.get(e.getKey()).name).append(" × ").append(e.getValue());
        }
        return sb.toString();
    }

    static boolean sameDay(long a, long b) {
        Calendar x = Calendar.getInstance(), y = Calendar.getInstance();
        x.setTimeInMillis(a); y.setTimeInMillis(b);
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR);
    }

    public static String when(long t) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(sameDay(t, System.currentTimeMillis()) ? "'Today' h:mm a" : "d MMM, h:mm a", java.util.Locale.ENGLISH);
        return f.format(new java.util.Date(t));
    }

    /** Today's orders at a glance, ready to send to yourself or a partner. */
    public static String daySummary(java.util.List<Prefs.Order> orders) {
        long now = System.currentTimeMillis();
        int n = 0, unpaid = 0, pending = 0; double sum = 0, due = 0;
        StringBuilder lines = new StringBuilder();
        for (Prefs.Order o : orders) {
            if (!sameDay(o.time, now)) continue;
            n++; sum += o.total;
            if (!o.paid) { unpaid++; due += o.total; }
            if (!o.delivered) pending++;
            lines.append("\n• ").append(o.note != null && !o.note.isEmpty() ? o.note + ": " : "").append(o.items)
                 .append(" — ").append(rupees(o.total)).append(o.paid ? " ✓paid" : " (unpaid)").append(o.delivered ? " ✓delivered" : "");
        }
        if (n == 0) return "No orders saved today.";
        return "*Orders today: " + n + "*  ·  Total " + rupees(sum) + "  ·  Unpaid " + rupees(due) + " (" + unpaid + ")  ·  To deliver: " + pending + lines;
    }

    // ------------------------------------------------------------------ replies to a copied customer message

    public static final class Reply {
        public final String label, text; public final int icon;
        Reply(String label, String text, int icon) { this.label = label; this.text = text; this.icon = icon; }
    }

    private static boolean any(String t, String... keys) {
        for (String k : keys) {
            if (k.startsWith("^")) { if (t.startsWith(k.substring(1))) return true; }
            else if (java.util.regex.Pattern.compile("(^|[^a-z])" + java.util.regex.Pattern.quote(k) + "([^a-z]|$)").matcher(t).find()) return true;
        }
        return false;
    }

    /**
     * Reads a message you copied from a customer and suggests replies: price list, delivery time,
     * availability, payment details, shop location, opening hours, greeting or thanks. Best first.
     */
    public static List<Reply> replyIdeas(Prefs p, String msg) {
        List<Reply> out = new ArrayList<>();
        if (msg == null) return out;
        String t = msg.toLowerCase(java.util.Locale.ROOT).trim();
        if (t.length() > 400) t = t.substring(0, 400);
        String upi = upi(p), name = name(p);
        if (any(t, "price", "prices", "rate", "rates", "cost", "how much", "kitne", "kitna", "kitni", "mrp", "price list", "catalogue", "catalog", "rate list", "kya rate", "best price", "wholesale")) {
            List<Prefs.Product> items = p.products();
            if (!items.isEmpty()) out.add(new Reply("Send price list", priceListText(p, items), GlassPainter.IC_RUPEE));
            else out.add(new Reply("Ask which item", "Sure! Please tell me which item and how many pieces you need, and I'll share the best price.", GlassPainter.IC_RUPEE));
        }
        if (any(t, "deliver", "delivery", "delivered", "dispatch", "dispatched", "shipped", "tracking", "courier", "parcel", "kab tak", "kab aayega", "kab milega", "kab aaega", "when will", "where is my order", "order status", "reach")) {
            String d = p.str("bizDelivery", "").trim();
            if (d.isEmpty()) d = "Your order will be dispatched today and should reach you in 3–4 days. I'll share the tracking details as soon as it's shipped.";
            out.add(new Reply("Delivery time", d, GlassPainter.IC_CLOCK));
        }
        if (any(t, "available", "availability", "in stock", "stock", "milega", "mil jayega", "hai kya", "do you have", "have you got")) {
            out.add(new Reply("Yes, available", "Yes, it's available. How many pieces do you need?", GlassPainter.IC_SHOP));
        }
        if (any(t, "pay", "payment", "upi", "gpay", "google pay", "phonepe", "paytm", "account number", "bank details", "qr", "scanner")) {
            String txt = !upi.isEmpty()
                    ? "You can pay on UPI: " + upi + (name.isEmpty() ? "" : " (" + name + ")") + "\nPlease share the screenshot once it's done. Thank you!"
                    : "I'll share the payment details right away.";
            out.add(new Reply("Payment details", txt, GlassPainter.IC_QR));
        }
        if (any(t, "address", "location", "where is your shop", "where is the shop", "shop kaha", "shop kahan", "dukaan kaha", "dukaan kahan", "dukan kaha", "directions", "map")) {
            String addr = p.str("bizAddress", "").trim(), maps = p.str("bizMaps", "").trim(), ph = p.str("bizPhone", "").trim();
            StringBuilder sb = new StringBuilder();
            if (!addr.isEmpty()) sb.append(name.isEmpty() ? "Our shop: " : name + ": ").append(addr);
            if (!maps.isEmpty()) sb.append(sb.length() > 0 ? "\n" : "").append("Location: ").append(maps);
            if (!ph.isEmpty()) sb.append(sb.length() > 0 ? "\n" : "").append("Call / WhatsApp: ").append(ph);
            if (sb.length() > 0) out.add(new Reply("Shop location", sb.toString(), GlassPainter.IC_MAP));
        }
        if (any(t, "open", "opening", "timing", "timings", "closed", "close", "kab khulega", "khula", "khuli", "band", "holiday", "sunday")) {
            out.add(new Reply("Shop hours", hoursText(p, Calendar.getInstance()), GlassPainter.IC_CLOCK));
        }
        if (out.isEmpty() && any(t, "^hi", "^hello", "^hey", "^namaste", "^good morning", "^good evening", "^hii", "^helo")) {
            out.add(new Reply("Greet", "Namaste! Thank you for contacting us" + (name.isEmpty() ? "" : " at " + name) + ". How can I help you today?", GlassPainter.IC_SPARKLE));
        }
        if (out.isEmpty() && any(t, "thank", "thanks", "thank you", "shukriya", "dhanyavaad", "dhanyawad", "thx")) {
            out.add(new Reply("You're welcome", "You're welcome! Do visit again 😊", GlassPainter.IC_SPARKLE));
        }
        return out;
    }

    // ------------------------------------------------------------------ detecting details in copied text

    public static final int PHONE = 1, GSTIN = 2, PINCODE = 3, UPI = 4, AMOUNT = 5, EMAIL = 6;

    public static class Found {
        public final int type;
        public final String value;   // normalised value
        public String note;          // extra info (e.g. GSTIN state / validity)
        Found(int t, String v) { type = t; value = v; }
        public String title() {
            switch (type) {
                case PHONE: return "Phone  " + value.substring(0, 5) + " " + value.substring(5);
                case GSTIN: return "GSTIN  " + value;
                case PINCODE: return "Pincode  " + value;
                case UPI: return "UPI ID  " + value;
                case AMOUNT: return "Amount  ₹" + Calc.format(Double.parseDouble(value), true);
                default: return "Email  " + value;
            }
        }
        public String shortLabel() {
            switch (type) {
                case PHONE: return value.substring(0, 5) + " " + value.substring(5);
                case AMOUNT: return "₹" + Calc.format(Double.parseDouble(value), true);
                default: return value;
            }
        }
    }

    private static final Pattern P_PHONE = Pattern.compile("(?<![\\d])(?:\\+?91[\\s-]?|0)?([6-9]\\d{4})[\\s-]?(\\d{5})(?![\\d])");
    private static final Pattern P_GST = Pattern.compile("\\b(\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z])\\b");
    private static final Pattern P_PIN = Pattern.compile("(?<![\\d])([1-9]\\d{2}\\s?\\d{3})(?![\\d])");
    private static final Pattern P_UPI = Pattern.compile("\\b([A-Za-z0-9._-]{2,}@[A-Za-z]{2,})\\b(?![.@\\w])");
    private static final Pattern P_EMAIL = Pattern.compile("\\b([A-Za-z0-9._%+-]+@[A-Za-z0-9-]+\\.[A-Za-z0-9.-]+)\\b");
    private static final Pattern P_AMT = Pattern.compile("(?i)(?:₹|rs\\.?|inr)\\s?([\\d,]+(?:\\.\\d{1,2})?)|([\\d,]+(?:\\.\\d{1,2})?)\\s?(?:/-|rs\\b|rupees)");

    public static List<Found> detect(String text) {
        List<Found> out = new ArrayList<>();
        if (text == null || text.length() > 5000) return out;
        List<int[]> used = new ArrayList<>();
        Matcher m = P_GST.matcher(text.toUpperCase(Locale.US));
        while (m.find()) {
            Found f = new Found(GSTIN, m.group(1));
            f.note = gstinInfo(f.value);
            add(out, f); used.add(new int[]{m.start(), m.end()});
        }
        m = P_EMAIL.matcher(text);
        while (m.find()) { add(out, new Found(EMAIL, m.group(1))); used.add(new int[]{m.start(), m.end()}); }
        m = P_UPI.matcher(text);
        while (m.find()) if (!overlaps(used, m.start(), m.end())) { add(out, new Found(UPI, m.group(1))); used.add(new int[]{m.start(), m.end()}); }
        m = P_PHONE.matcher(text);
        while (m.find()) if (!overlaps(used, m.start(), m.end())) { add(out, new Found(PHONE, m.group(1) + m.group(2))); used.add(new int[]{m.start(), m.end()}); }
        m = P_AMT.matcher(text);
        while (m.find()) {
            String g = m.group(1) != null ? m.group(1) : m.group(2);
            if (g == null || overlaps(used, m.start(), m.end())) continue;
            try {
                double v = Double.parseDouble(g.replace(",", ""));
                if (v > 0) { add(out, new Found(AMOUNT, Calc.format(v, false))); used.add(new int[]{m.start(), m.end()}); }
            } catch (Exception ignored) { }
        }
        m = P_PIN.matcher(text);
        while (m.find()) if (!overlaps(used, m.start(), m.end())) add(out, new Found(PINCODE, m.group(1).replace(" ", "")));
        return out;
    }

    private static void add(List<Found> l, Found f) {
        for (Found x : l) if (x.type == f.type && x.value.equals(f.value)) return;
        if (l.size() < 12) l.add(f);
    }

    private static boolean overlaps(List<int[]> used, int s, int e) {
        for (int[] u : used) if (s < u[1] && e > u[0]) return true;
        return false;
    }

    // ------------------------------------------------------------------ GSTIN check (offline)

    private static final Map<String, String> STATES = new HashMap<>();
    static {
        String[] s = {"01", "Jammu & Kashmir", "02", "Himachal Pradesh", "03", "Punjab", "04", "Chandigarh", "05", "Uttarakhand",
                "06", "Haryana", "07", "Delhi", "08", "Rajasthan", "09", "Uttar Pradesh", "10", "Bihar", "11", "Sikkim",
                "12", "Arunachal Pradesh", "13", "Nagaland", "14", "Manipur", "15", "Mizoram", "16", "Tripura", "17", "Meghalaya",
                "18", "Assam", "19", "West Bengal", "20", "Jharkhand", "21", "Odisha", "22", "Chhattisgarh", "23", "Madhya Pradesh",
                "24", "Gujarat", "26", "Dadra & Nagar Haveli and Daman & Diu", "27", "Maharashtra", "29", "Karnataka", "30", "Goa",
                "31", "Lakshadweep", "32", "Kerala", "33", "Tamil Nadu", "34", "Puducherry", "35", "Andaman & Nicobar",
                "36", "Telangana", "37", "Andhra Pradesh", "38", "Ladakh", "97", "Other Territory"};
        for (int i = 0; i < s.length; i += 2) STATES.put(s[i], s[i + 1]);
    }

    public static boolean gstinValid(String g) {
        if (g == null || g.length() != 15) return false;
        String cs = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int v = cs.indexOf(g.charAt(i));
            if (v < 0) return false;
            int prod = v * ((i % 2) + 1);
            sum += prod / 36 + prod % 36;
        }
        int check = (36 - (sum % 36)) % 36;
        return cs.charAt(check) == g.charAt(14);
    }

    public static String gstinInfo(String g) {
        String st = STATES.get(g.substring(0, 2));
        boolean ok = gstinValid(g);
        return (ok ? "Checksum valid" : "Checksum does NOT match — possible typo")
                + (st != null ? "  ·  " + st : "") + "  ·  PAN " + g.substring(2, 12);
    }
}
