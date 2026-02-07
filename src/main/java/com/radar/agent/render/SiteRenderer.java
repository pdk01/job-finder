package com.radar.agent.render;

import com.radar.agent.model.Job;
import com.radar.agent.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SiteRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public void writeSite(Path outputDir, List<Job> jobs) {
        try {
            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve("jobs.json"), Json.write(jobs), StandardCharsets.UTF_8);
            Files.writeString(outputDir.resolve("index.html"), buildHtml(jobs), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write site output", e);
        }
    }

    private String buildHtml(List<Job> jobs) {
        StringBuilder rows = new StringBuilder();
        for (Job job : jobs) {
            String contacts = join(job.contactEmails);
            String phones = join(job.contactPhones);
            String names = join(job.contactNames);
            String guesses = join(job.contactGuesses);

            rows.append("<tr>")
                .append(td(escape(job.company)))
                .append(td(escape(job.title)))
                .append(td(link(job.url)))
                .append(td(job.postedAt != null ? DATE.format(job.postedAt) : ""))
                .append(td(job.score >= 0 ? String.format("%.1f", job.score) : ""))
                .append(td(details("Outreach", escape(job.outreachMessage))))
                .append(td(details("Contacts", escape(contacts))))
                .append(td(details("Phones", escape(phones))))
                .append(td(details("Names", escape(names))))
                .append(td(details("Guesses", escape(guesses))))
                .append(td(coverLinks(job)))
                .append("</tr>\n");
        }

        return """
<!DOCTYPE html>
<html lang=\"en\">
<head>
  <meta charset=\"UTF-8\" />
  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
  <title>Job Radar</title>
  <style>
    :root {
      --bg: #0b1220;
      --card: #111a2e;
      --accent: #3dd5a7;
      --text: #e9eef7;
      --muted: #9fb0c7;
    }
    body { margin: 0; font-family: \"Segoe UI\", system-ui, sans-serif; background: linear-gradient(160deg, #0b1220, #121b30); color: var(--text); }
    header { padding: 32px 48px; }
    h1 { margin: 0; font-size: 28px; }
    p { color: var(--muted); }
    .table-wrap { padding: 0 48px 48px; overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; background: var(--card); border-radius: 12px; overflow: hidden; }
    th, td { padding: 12px 16px; border-bottom: 1px solid #1f2b45; text-align: left; vertical-align: top; }
    th { background: #121c33; font-size: 13px; letter-spacing: 0.04em; text-transform: uppercase; color: var(--muted); }
    tr:hover { background: #16213a; }
    a { color: var(--accent); text-decoration: none; }
    a:hover { text-decoration: underline; }
    details { color: var(--text); }
    summary { cursor: pointer; color: var(--accent); }
    .empty { padding: 48px; text-align: center; color: var(--muted); }
  </style>
</head>
<body>
  <header>
    <h1>Job Radar (Last 24 Hours)</h1>
    <p>Company, opening, link. Contact fields include explicit matches and best-guess emails.</p>
  </header>
  <div class=\"table-wrap\">
""" + (jobs.isEmpty() ? "    <div class=\"empty\">No jobs found in the last 24 hours.</div>\n" :
"    <table>\n      <thead>\n        <tr>\n          <th>Company</th>\n          <th>Opening</th>\n          <th>Link</th>\n          <th>Posted</th>\n          <th>Score</th>\n          <th>Outreach</th>\n          <th>Contacts</th>\n          <th>Phones</th>\n          <th>Names</th>\n          <th>Guesses</th>\n          <th>Cover Letters</th>\n        </tr>\n      </thead>\n      <tbody>\n" + rows + "      </tbody>\n    </table>\n") +
"  </div>\n</body>\n</html>";
    }

    private static String td(String value) {
        return "<td>" + value + "</td>";
    }

    private static String link(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return "<a href=\"" + escape(url) + "\" target=\"_blank\" rel=\"noopener\">" + escape(url) + "</a>";
    }

    private static String coverLinks(Job job) {
        StringBuilder sb = new StringBuilder();
        if (job.coverLetterPath != null && !job.coverLetterPath.isBlank()) {
            sb.append(link(job.coverLetterPath));
        }
        if (job.coverLetterVariants != null && !job.coverLetterVariants.isEmpty()) {
            for (String path : job.coverLetterVariants) {
                if (sb.length() > 0) {
                    sb.append("<br/>");
                }
                sb.append(link(path));
            }
        }
        return sb.toString();
    }

    private static String details(String label, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return "<details><summary>" + label + "</summary><div>" + value.replace("\n", "<br/>") + "</div></details>";
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(", ", values);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}