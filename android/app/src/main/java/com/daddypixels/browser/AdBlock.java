package com.daddypixels.browser;

import java.util.HashSet;
import java.util.Set;

/** Blocks well-known ad and tracking servers (pages load faster and use less data). */
final class AdBlock {
    private AdBlock() {}

    private static final Set<String> HOSTS = new HashSet<>();

    static {
        String[] list = {
                "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
                "googletagmanager.com", "googletagservices.com", "adservice.google.com", "pagead2.googlesyndication.com",
                "adnxs.com", "adsrvr.org", "advertising.com", "amazon-adsystem.com", "criteo.com", "criteo.net",
                "taboola.com", "outbrain.com", "scorecardresearch.com", "quantserve.com", "moatads.com",
                "rubiconproject.com", "pubmatic.com", "openx.net", "casalemedia.com", "smartadserver.com",
                "adform.net", "bidswitch.net", "yieldmo.com", "sharethrough.com", "media.net", "zedo.com",
                "hotjar.com", "mixpanel.com", "segment.io", "segment.com", "fullstory.com", "mouseflow.com",
                "crazyegg.com", "chartbeat.com", "chartbeat.net", "newrelic.com", "nr-data.net", "clarity.ms",
                "connect.facebook.net", "ads-twitter.com", "analytics.twitter.com", "ads.linkedin.com",
                "analytics.tiktok.com", "ads.tiktok.com", "bat.bing.com", "ads.yahoo.com", "adroll.com",
                "teads.tv", "spotxchange.com", "33across.com", "indexww.com", "lijit.com", "sovrn.com",
                "contextweb.com", "gumgum.com", "triplelift.com", "propellerads.com", "popads.net",
                "popcash.net", "exoclick.com", "adsterra.com", "mgid.com", "revcontent.com", "onesignal.com",
                "branch.io", "app-measurement.com", "appsflyer.com", "adjust.com", "kochava.com"
        };
        for (String h : list) HOSTS.add(h);
    }

    /** True if the host or any parent domain is on the list. */
    static boolean blocked(String host) {
        String h = host;
        while (true) {
            if (HOSTS.contains(h)) return true;
            int dot = h.indexOf('.');
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false;
            h = h.substring(dot + 1);
        }
    }
}
