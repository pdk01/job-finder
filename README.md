# Job Finder Agent

A Java-based agent that collects fresh software-engineering jobs (1–4 years experience), enriches them with LLM outputs, finds recruiter contacts, and publishes a static site you can host on GitHub Pages.

## What It Does

1. **Fetch jobs** from:
   - Remotive (public JSON)
   - RemoteOK (public JSON)
   - Adzuna (official API, India by default)
2. **Filters** to software engineering roles and 1–4 years experience.
3. **Scores** jobs using a lightweight heuristic (keywords + experience).
4. **LLM (optional)** for:
   - Outreach message
   - Cover letters (plus variants)
5. **Recruiter contact discovery**:
   - Extracts emails/phones/names from job pages
   - Adds best‑guess emails (e.g., `careers@company.com`)
   - Optionally scans public pages (DuckDuckGo results)
6. **Publishes** a static HTML site in `site/`.

## Output

- `site/index.html` – job table
- `site/jobs.json` – structured data
- `site/cover_letters/` – cover letters + variants

## Requirements

- Java 21
- Gradle wrapper (included)
- Optional: Gemini API key (or OpenAI, if you switch provider)

## Quick Start (Local)

1. Create `.env` (not committed):
   ```env
   GEMINI_API_KEY=YOUR_KEY
   ADZUNA_APP_ID=YOUR_ADZUNA_ID
   ADZUNA_APP_KEY=YOUR_ADZUNA_KEY
   ```
2. Run:
   ```powershell
   ./gradlew runAgent
   ```
3. Open `site/index.html`.

## Configuration

Edit `config.json`:

- `lookbackHours`: recency window
- `experienceMinYears` / `experienceMaxYears`
- `maxResults`, `maxCoverLetters`, `coverLetterVariants`
- `maxEnrichments`, `maxContactLookups`
- `sources`: enable/disable job sources
- `adzuna`: country/query settings
- `llmProvider`: `gemini` or `openai`

### Minimal LLM usage (current default)
Only outreach + cover letters are enabled. Other AI enrichments are disabled for stability and quota control.

## GitHub Pages (Free Hosting)

This repo includes a workflow that:
- runs daily at **03:30 UTC**
- also supports manual triggers
- deploys `site/` to GitHub Pages via GitHub Actions

### Setup Steps

1. **Push repo** to GitHub.
2. **Add secrets**:
   - `GEMINI_API_KEY`
   - `ADZUNA_APP_ID`
   - `ADZUNA_APP_KEY`
3. **Enable Pages**:
   - `Settings → Pages → Source: GitHub Actions`
4. **Run workflow once**:
   - `Actions → Run Agent and Publish → Run workflow`

Your site will appear at:
```
https://<username>.github.io/job-finder/
```

## Notes

- API keys are not stored in code. Use `.env` locally or GitHub Secrets in CI.
- Many boards (LinkedIn/Naukri/Indeed) block scraping. This agent only uses compliant sources.

## Troubleshooting

- **No jobs**: widen `lookbackHours` or expand keyword filters.
- **LLM errors**: check API quota/limits. Increase `gemini.minDelayMs` if you see 429 errors.
- **Contacts empty**: job pages may not expose recruiter info.

## License

Use privately at your own risk. If you want a license added, tell me which one.