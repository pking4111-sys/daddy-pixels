package com.daddypixels.browser;

import android.net.Uri;
import android.text.TextUtils;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Simple scam / phishing checks, same idea as the desktop Daddy Pixels shield. */
final class Shield {
    private Shield() {}

    /** Brands that scammers love to copy, and the real domains each one uses. */
    private static final String[][] BRANDS = {
            {"paypal", "paypal.com", "paypal.me"},
            {"apple", "apple.com", "icloud.com"},
            {"icloud", "icloud.com", "apple.com"},
            {"google", "google.com", "google.co.uk", "googleusercontent.com", "googleapis.com", "gstatic.com", "googlevideo.com"},
            {"gmail", "gmail.com", "google.com"},
            {"microsoft", "microsoft.com", "microsoftonline.com", "live.com", "office.com", "msn.com"},
            {"outlook", "outlook.com", "live.com", "office.com", "microsoft.com"},
            {"amazon", "amazon.com", "amazon.co.uk", "amazon.de", "amazonaws.com", "media-amazon.com"},
            {"netflix", "netflix.com", "nflxext.com", "nflxvideo.net"},
            {"facebook", "facebook.com", "fb.com", "fbcdn.net"},
            {"instagram", "instagram.com", "cdninstagram.com"},
            {"whatsapp", "whatsapp.com", "whatsapp.net"},
            {"steam", "steampowered.com", "steamcommunity.com", "steamstatic.com"},
            {"steamcommunity", "steamcommunity.com"},
            {"roblox", "roblox.com", "rbxcdn.com"},
            {"discord", "discord.com", "discord.gg", "discordapp.com", "discordapp.net"},
            {"epicgames", "epicgames.com", "unrealengine.com"},
            {"playstation", "playstation.com", "sony.com"},
            {"xbox", "xbox.com", "microsoft.com"},
            {"nintendo", "nintendo.com", "nintendo.co.uk", "nintendo.net"},
            {"coinbase", "coinbase.com"},
            {"binance", "binance.com"},
            {"metamask", "metamask.io"},
            {"hmrc", "gov.uk"},
            {"royalmail", "royalmail.com"},
            {"dpd", "dpd.co.uk", "dpd.com"},
            {"evri", "evri.com"},
            {"barclays", "barclays.co.uk", "barclays.com"},
            {"lloyds", "lloydsbank.com", "lloydsbank.co.uk"},
            {"natwest", "natwest.com"},
            {"santander", "santander.co.uk", "santander.com"},
            {"halifax", "halifax.co.uk"},
            {"monzo", "monzo.com"},
    };

    private static final String[] SCAM_WORDS = {"login", "signin", "sign-in", "verify", "verification", "secure",
            "account", "update", "wallet", "unlock", "support", "billing", "password", "recover", "free-robux",
            "freerobux", "giftcard", "gift-card", "claim", "prize", "nitro"};

    private static final Set<String> allowed = new HashSet<>();

    static void allow(String host) {
        if (host != null) allowed.add(host.toLowerCase(Locale.ROOT));
    }

    /** Returns a reason if the address looks dangerous, otherwise null. */
    static String check(Uri u) {
        String host = u.getHost();
        if (TextUtils.isEmpty(host)) return null;
        host = host.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (allowed.contains(host)) return null;
        String full = u.toString().toLowerCase(Locale.ROOT);

        for (int i = 0; i < host.length(); i++) {
            if (host.charAt(i) > 127) return "This address uses look-alike letters from other alphabets. Scam sites use this trick to copy real websites.";
        }
        for (String part : host.split("\\.")) {
            if (part.startsWith("xn--")) return "This address uses look-alike letters from other alphabets (punycode). Scam sites use this trick to copy real websites.";
        }
        if (u.getUserInfo() != null && u.getUserInfo().length() > 0)
            return "This link hides its real destination using an @ sign. The site you'd actually visit is " + host + ".";

        boolean ip = host.matches("^\\d{1,3}(\\.\\d{1,3}){3}$") || host.startsWith("[");
        if (ip && containsAny(full, SCAM_WORDS))
            return "This is a bare number address (no real website name) asking you to log in or verify something.";

        String squashed = host.replace("-", "").replace("0", "o").replace("1", "l").replace("rn", "m").replace("vv", "w");
        for (String[] brand : BRANDS) {
            String name = brand[0];
            String word = "(^|[.\\-])" + name;           // brand at the start of a word, so "livestream" isn't "steam"
            boolean inHost = java.util.regex.Pattern.compile(word).matcher(host).find();
            boolean inSquashed = java.util.regex.Pattern.compile(word).matcher(squashed).find();
            if (!inHost && !inSquashed) continue;
            boolean real = false;
            for (int i = 1; i < brand.length; i++) {
                String d = brand[i];
                if (host.equals(d) || host.endsWith("." + d)) { real = true; break; }
            }
            if (real) continue;
            // the brand's name inside someone else's domain
            if (inHost && containsAny(full, SCAM_WORDS))
                return "This page uses the name \"" + name + "\" but it isn't the real " + name + " website. It looks like a fake login page.";
            if (!inHost)
                return "This address is spelled to look like \"" + name + "\" but it isn't the real " + name + " website.";
        }
        return null;
    }

    private static boolean containsAny(String s, String[] words) {
        for (String w : words) if (s.contains(w)) return true;
        return false;
    }

    static String warningPage(String url, String why, boolean terminal) {
        String esc = TextUtils.htmlEncode(url);
        String go = "daddypixels://continue?to=" + Uri.encode(url);
        String fonts = "@font-face{font-family:Silkscreen;src:url(file:///android_asset/fonts/Silkscreen-Bold.ttf)}"
                + "@font-face{font-family:VT323;src:url(file:///android_asset/fonts/VT323-Regular.ttf)}";
        String css = terminal
                ? "body{background:#07140b;color:#3cff7a;font-family:VT323,monospace;font-size:22px;padding:24px;text-shadow:0 0 6px #3cff7a99}"
                + "h1{font-size:34px}a{display:block;margin:16px 0;color:#07140b;background:#3cff7a;padding:10px;text-decoration:none;text-align:center}"
                + "a.c{background:none;color:#1f9a48;border:2px solid #1f9a48}.u{word-break:break-all;color:#1f9a48}"
                : "body{background:#ffd3ea;color:#2f5fe0;font-family:Silkscreen,monospace;padding:20px}"
                + ".w{background:#fff;border:3px solid #2f5fe0;box-shadow:6px 6px 0 #2f5fe066;padding:18px}"
                + "h1{color:#ff3b6b;font-size:24px}p{font-family:sans-serif;font-size:16px;line-height:1.4}"
                + "a{display:block;margin:14px 0;background:#ff6fb5;color:#fff;border:3px solid #2f5fe0;padding:12px;text-align:center;text-decoration:none;font-weight:bold}"
                + "a.c{background:#fff;color:#2f5fe0;font-size:12px}.u{word-break:break-all;font-family:monospace;font-size:13px}";
        String title = terminal ? "> WARNING: HOSTILE SIGNAL DETECTED" : "( ! ) STOP! POSSIBLE SCAM";
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>" + fonts + css + "</style></head><body><div class='w'><h1>" + title + "</h1>"
                + "<p>" + TextUtils.htmlEncode(why) + "</p><p class='u'>" + esc + "</p>"
                + "<p>Don't type passwords, codes or card details here.</p>"
                + "<a href='daddypixels://back'>GET ME OUT OF HERE</a>"
                + "<a class='c' href='" + TextUtils.htmlEncode(go) + "'>I trust this site, continue anyway</a></div></body></html>";
    }
}
