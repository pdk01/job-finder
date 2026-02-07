package com.radar.agent.util;

import com.radar.agent.model.Job;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContactFinder {
    private static final Pattern EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE = Pattern.compile("(\\+?\\d{1,3}[-.\\s]?)?(\\(?\\d{2,4}\\)?[-.\\s]?)?\\d{3,4}[-.\\s]?\\d{4}");
    private static final Pattern NAME_HINT = Pattern.compile("(recruiter|talent acquisition|hr|people operations)[:\\s-]*([A-Z][a-z]+(?:\\s+[A-Z][a-z]+){0,2})", Pattern.CASE_INSENSITIVE);

    private static final Set<String> JOB_BOARD_HOSTS = Set.of(
        "remoteok.com",
        "remotive.com",
        "boards.greenhouse.io",
        "jobs.lever.co"
    );

    public ContactResult find(Job job, boolean enablePublicSearch) {
        ContactResult result = new ContactResult();
        if (job.url == null || job.url.isBlank()) {
            return result;
        }

        String html = safeGet(job.url);
        if (html != null) {
            parseHtml(html, result);
            parseMailto(html, result);
        }

        String domain = extractDomain(job.url);
        if (domain != null && !domain.isBlank()) {
            result.guessedEmails.addAll(guessEmails(domain));
        }

        if (enablePublicSearch) {
            List<String> pages = searchPublic(job, 3);
            for (String page : pages) {
                String body = safeGet(page);
                if (body != null) {
                    parseHtml(body, result);
                    parseMailto(body, result);
                }
            }
        }

        return result;
    }

    private void parseHtml(String html, ContactResult result) {
        Document doc = Jsoup.parse(html);
        String text = doc.text();
        Matcher emailMatcher = EMAIL.matcher(text);
        while (emailMatcher.find()) {
            result.emails.add(emailMatcher.group());
        }

        Matcher phoneMatcher = PHONE.matcher(text);
        while (phoneMatcher.find()) {
            String phone = phoneMatcher.group().trim();
            if (phone.length() >= 8) {
                result.phones.add(phone);
            }
        }

        Matcher nameMatcher = NAME_HINT.matcher(text);
        while (nameMatcher.find()) {
            String name = nameMatcher.group(2);
            if (name != null && !name.isBlank()) {
                result.names.add(name.trim());
            }
        }
    }

    private void parseMailto(String html, ContactResult result) {
        Document doc = Jsoup.parse(html);
        for (Element link : doc.select("a[href^=mailto:]")) {
            String href = link.attr("href");
            if (href == null) {
                continue;
            }
            String email = href.replace("mailto:", "");
            int idx = email.indexOf('?');
            if (idx >= 0) {
                email = email.substring(0, idx);
            }
            if (!email.isBlank()) {
                result.emails.add(email);
            }
        }
    }

    private List<String> searchPublic(Job job, int maxResults) {
        List<String> results = new ArrayList<>();
        String query = job.company + " " + job.title + " recruiter email";
        String url = "https://duckduckgo.com/html/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        String html = safeGet(url);
        if (html == null) {
            return results;
        }
        Document doc = Jsoup.parse(html);
        for (Element link : doc.select("a.result__a")) {
            String href = link.attr("href");
            if (href == null || href.isBlank()) {
                continue;
            }
            results.add(href);
            if (results.size() >= maxResults) {
                break;
            }
        }
        return results;
    }

    private String safeGet(String url) {
        try {
            return Http.get(url);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                return null;
            }
            String lower = host.toLowerCase(Locale.ROOT);
            for (String board : JOB_BOARD_HOSTS) {
                if (lower.endsWith(board)) {
                    return null;
                }
            }
            return lower.startsWith("www.") ? lower.substring(4) : lower;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> guessEmails(String domain) {
        List<String> guesses = new ArrayList<>();
        for (String prefix : List.of("careers", "jobs", "talent", "recruiting", "hr")) {
            guesses.add(prefix + "@" + domain);
        }
        return guesses;
    }

    public static class ContactResult {
        public Set<String> emails = new HashSet<>();
        public Set<String> phones = new HashSet<>();
        public Set<String> names = new HashSet<>();
        public Set<String> guessedEmails = new HashSet<>();
    }
}