package io.pyahu.quality;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** quality.html: one self-contained page, readable offline from a CI artifact, light and dark. */
final class HtmlReport {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    static String render(Report r) {
        StringBuilder h = new StringBuilder(32_000);
        h.append("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>Quality report · ").append(esc(r.project())).append("</title>\n")
                .append("<style>").append(CSS).append("</style>\n</head>\n<body>\n<div class=\"page\">\n");

        // Header
        h.append("<header class=\"top\">")
                .append("<div class=\"brand\">").append(MARK)
                .append("<div><p class=\"eyebrow\">Pyahu quality report</p>")
                .append("<h1>").append(esc(r.project())).append("</h1></div></div>")
                .append("<div class=\"meta\"><span class=\"where\">");
        if (r.runUrl() != null) {
            h.append("<a href=\"").append(esc(r.runUrl())).append("\">").append(esc(r.where())).append(ARROW).append("</a>");
        } else {
            h.append(esc(r.where()));
        }
        h.append("</span><time datetime=\"").append(r.generatedAt()).append("\">")
                .append(TIME.format(r.generatedAt())).append("</time></div></header>\n");

        hero(h, r);

        // Gates
        h.append("<section aria-labelledby=\"gates\"><div class=\"section-head\"><h2 id=\"gates\">Gates</h2>")
                .append("<p>In the order a full build runs them. Each one links to the guide step that explains it.</p></div>\n")
                .append("<ol class=\"timeline\">\n");
        for (Report.Gate g : r.gates()) {
            gate(h, g);
        }
        h.append("</ol></section>\n");

        // Environment
        h.append("<section aria-labelledby=\"env\"><div class=\"section-head\"><h2 id=\"env\">Environment</h2>")
                .append("<p>Passes here and fails in CI, or the other way round? Compare this table with the report of the ")
                .append("other run: Java, time zone, locale and Docker.</p></div>\n<dl class=\"env\">");
        for (Map.Entry<String, String> e : r.environment().entrySet()) {
            h.append("<div><dt>").append(esc(e.getKey())).append("</dt><dd>").append(esc(e.getValue())).append("</dd></div>");
        }
        h.append("</dl></section>\n");

        h.append("<footer><p>Written by the build on every run: <code>build/reports/pyahu/quality.html</code>, with the same ")
                .append("data for tools and AI agents in <a href=\"quality.json\">quality.json</a>.</p>")
                .append("<p><a href=\"https://initializer.pyahu.io/quality/\">Pyahu Initializer: the quality checks</a></p></footer>\n")
                .append("</div>\n</body>\n</html>\n");
        return h.toString();
    }

    private static void hero(StringBuilder h, Report r) {
        String status = r.status();
        h.append("<section class=\"hero ").append(status).append("\">");
        h.append("<div class=\"hero-main\"><div class=\"hero-status\">").append(icon(status, 28))
                .append("<span>").append(switch (status) {
                    case Report.PASSED -> "Passed";
                    case Report.FAILED -> "Failed";
                    default -> "Incomplete";
                }).append("</span></div>");
        if (r.failedFirst() != null) {
            Report.Gate g = r.failedFirst();
            Gates.Definition d = g.definition();
            long failedCount = r.gates().stream().filter(x -> Report.FAILED.equals(x.status())).count();
            h.append("<h2>The build failed at <a href=\"#gate-").append(d.id()).append("\">")
                    .append(esc(d.title().toLowerCase(Locale.ROOT))).append("</a>");
            if (failedCount > 1) {
                h.append(" and ").append(Report.plural((int) failedCount - 1, "more gate"));
            }
            h.append("</h2>");
            String reason = Report.reason(g);
            if (!reason.isBlank()) {
                h.append("<pre class=\"why\">").append(esc(reason)).append("</pre>");
            }
            h.append("<div class=\"actions\">")
                    .append("<div class=\"action\"><span>Reproduce</span><code>").append(esc(d.command())).append("</code></div>");
            if (d.fix() != null) {
                h.append("<div class=\"action\"><span>Fix</span><p>").append(code(d.fix())).append("</p></div>");
            }
            h.append("<a class=\"guide-button\" href=\"").append(esc(d.guideUrl())).append("\">Why it matters and how to fix it: ")
                    .append(esc(d.guide())).append(ARROW).append("</a></div>");
        } else if (Report.PASSED.equals(status)) {
            h.append("<h2>All ").append(r.gates().size()).append(" gates passed</h2>")
                    .append("<p class=\"lead\">Formatting, compilation, tests, architecture, complexity, coverage, mutation ")
                    .append("and packaging. The same checks run in CI.</p>");
        } else {
            h.append("<h2>").append(r.passedCount()).append(" of ").append(r.gates().size()).append(" gates have passed</h2>")
                    .append("<p class=\"lead\">The others have not run yet. <code>./gradlew build</code> runs them all.</p>");
        }
        h.append("</div><div class=\"hero-side\">").append(ring(r)).append(strip(r)).append("</div></section>\n");
    }

    private static String ring(Report r) {
        int total = r.gates().size();
        long passed = r.passedCount();
        double c = 2 * Math.PI * 44;
        double filled = total == 0 ? 0 : c * passed / total;
        return "<svg class=\"ring\" viewBox=\"0 0 104 104\" role=\"img\" aria-label=\"" + passed + " of " + total
                + " gates passed\"><circle cx=\"52\" cy=\"52\" r=\"44\" class=\"ring-track\"/>"
                + "<circle cx=\"52\" cy=\"52\" r=\"44\" class=\"ring-fill\" stroke-dasharray=\""
                + String.format(Locale.ROOT, "%.2f %.2f", filled, c) + "\" transform=\"rotate(-90 52 52)\"/>"
                + "<text x=\"52\" y=\"50\" class=\"ring-num\">" + passed + "/" + total + "</text>"
                + "<text x=\"52\" y=\"68\" class=\"ring-label\">passed</text></svg>";
    }

    private static String strip(Report r) {
        StringBuilder s = new StringBuilder("<ol class=\"strip\" aria-hidden=\"true\">");
        for (Report.Gate g : r.gates()) {
            s.append("<li class=\"").append(g.status()).append("\" title=\"").append(esc(g.definition().title()))
                    .append(": ").append(esc(g.headline())).append("\"></li>");
        }
        return s.append("</ol>").toString();
    }

    private static void gate(StringBuilder h, Report.Gate g) {
        Gates.Definition d = g.definition();
        h.append("<li class=\"gate ").append(g.status()).append("\" id=\"gate-").append(d.id()).append("\">")
                .append("<span class=\"node\">").append(icon(g.status(), 16)).append("</span>")
                .append("<article class=\"card\"><header><div><h3>").append(esc(d.title())).append("</h3>")
                .append("<p class=\"purpose\">").append(esc(d.purpose())).append("</p></div>")
                .append("<div class=\"result\"><strong>").append(esc(g.headline())).append("</strong>");
        if (g.ranAt() > 0) {
            Instant at = Instant.ofEpochMilli(g.ranAt());
            h.append("<span class=\"when").append(g.fromThisRun() ? "" : " earlier").append("\">")
                    .append(g.fromThisRun() ? "this run, " + CLOCK.format(at) : "earlier run, " + TIME.format(at))
                    .append("</span>");
        }
        h.append("</div></header>");

        for (Report.Metric m : g.metrics()) {
            double pct = Math.max(0, Math.min(1, m.value())) * 100;
            h.append("<div class=\"metric").append(m.belowMinimum() ? " low" : "").append("\">")
                    .append("<div class=\"metric-head\"><span>").append(esc(m.label())).append("</span><span><b>")
                    .append(Report.percent(m.value())).append("</b> ").append(esc(m.text()));
            if (m.minimum() != null) {
                h.append(" · minimum ").append(Report.percent(m.minimum()));
            }
            h.append("</span></div><div class=\"bar\"><i style=\"width:")
                    .append(String.format(Locale.ROOT, "%.1f", pct)).append("%\"></i>");
            if (m.minimum() != null) {
                h.append("<em style=\"left:").append(String.format(Locale.ROOT, "%.1f", m.minimum() * 100))
                        .append("%\" title=\"Minimum ").append(Report.percent(m.minimum())).append("\"></em>");
            }
            h.append("</div></div>");
        }

        if (Report.FAILED.equals(g.status()) && g.failure() != null && g.findings().isEmpty()) {
            h.append("<pre class=\"why small\">").append(esc(Report.trim(g.failure(), 10, 1600))).append("</pre>");
        }

        if (!g.findings().isEmpty()) {
            h.append("<details class=\"findings\"").append(Report.FAILED.equals(g.status()) ? " open" : "").append(">")
                    .append("<summary>").append(Report.plural(g.findingsTotal(), "finding")).append("</summary><ul>");
            int shown = 0;
            for (Report.Finding f : g.findings()) {
                if (shown++ == VISIBLE) {
                    h.append("</ul><details class=\"rest\"><summary>Show ").append(g.findings().size() - VISIBLE)
                            .append(" more</summary><ul>");
                }
                finding(h, f);
            }
            if (g.findings().size() > VISIBLE) {
                h.append("</ul></details><ul>");
            }
            h.append("</ul>");
            if (g.findingsTotal() > g.findings().size()) {
                h.append("<p class=\"more\">").append(g.findingsTotal() - g.findings().size())
                        .append(" more in the tool report.</p>");
            }
            h.append("</details>");
        }

        h.append("<footer class=\"links\"><a href=\"").append(esc(d.guideUrl())).append("\">").append(esc(d.guide()))
                .append(ARROW).append("</a><span class=\"chip\">").append(esc(d.checklist())).append("</span>");
        if (d.toolReport() != null && !Report.NOT_RUN.equals(g.status())) {
            h.append("<a href=\"").append(esc(d.toolReport())).append("\">Tool report").append(ARROW).append("</a>");
        }
        h.append("<code class=\"cmd\">").append(esc(d.command())).append("</code></footer></article></li>\n");
    }

    /** Findings shown before "Show more": enough to start fixing, short enough to scan the page. */
    private static final int VISIBLE = 5;

    private static void finding(StringBuilder h, Report.Finding f) {
        h.append("<li><div class=\"f-head\"><span class=\"rule\">");
        if (f.url() != null && !f.url().isBlank()) {
            h.append("<a href=\"").append(esc(f.url())).append("\">").append(esc(f.rule())).append("</a>");
        } else {
            h.append(esc(f.rule()));
        }
        h.append("</span><code class=\"loc\">").append(esc(f.location())).append("</code></div>")
                .append("<p class=\"msg\">").append(esc(f.message())).append("</p></li>");
    }

    private static String icon(String status, int size) {
        String path = switch (status) {
            case Report.PASSED -> "<path d=\"M5 12.5l4.2 4.2L19 7\"/>";
            case Report.FAILED -> "<path d=\"M7 7l10 10M17 7L7 17\"/>";
            default -> "<path d=\"M7 12h10\"/>";
        };
        return "<svg class=\"icon\" width=\"" + size + "\" height=\"" + size
                + "\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.6\" "
                + "stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\">" + path + "</svg>";
    }

    /** Wraps ./gradlew commands in a fix text as code. */
    private static String code(String text) {
        String e = esc(text);
        int i = e.indexOf("./gradlew");
        if (i < 0) {
            return e;
        }
        int end = e.indexOf(' ', e.indexOf(' ', i) + 1);
        end = end < 0 ? e.length() : end;
        return e.substring(0, i) + "<code>" + e.substring(i, end) + "</code>" + e.substring(end);
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static final String ARROW = "<svg class=\"arrow\" width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" "
            + "stroke=\"currentColor\" stroke-width=\"2.4\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\">"
            + "<path d=\"M7 17L17 7M9 7h8v8\"/></svg>";

    private static final String MARK = "<svg class=\"mark\" width=\"36\" height=\"36\" viewBox=\"0 0 120 120\" fill=\"none\" "
            + "role=\"img\" aria-label=\"Pyahu\"><defs><linearGradient id=\"pb\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">"
            + "<stop stop-color=\"#042DE8\"/><stop offset=\"1\" stop-color=\"#064FFF\"/></linearGradient>"
            + "<linearGradient id=\"pt\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\"><stop stop-color=\"#0874FF\"/>"
            + "<stop offset=\"1\" stop-color=\"#0060FF\"/></linearGradient></defs><g transform=\"translate(20.695 8) scale(0.122066)\">"
            + "<path d=\"M110 0H385C536 0 644 126 644 272V394C644 489 606 551 522 600L218 734V754C218 816 164 852 91 852C31 852 0 815 0 "
            + "758V575C0 514 25 475 78 444L218 362V532C233 519 251 508 282 493L422 421C454 404 470 380 470 345V278L380 222C358 203 334 "
            + "190 305 190H108C47 190 4 157 4 103C4 45 46 0 110 0Z\" fill=\"url(#pb)\"/><path d=\"M110 0H385C536 0 644 126 644 272V432C634 "
            + "367 599 332 549 300L380 222C358 203 334 190 305 190H108C47 190 4 157 4 103C4 45 46 0 110 0Z\" fill=\"url(#pt)\"/>"
            + "<path d=\"M218 362V754C218 816 164 852 91 852C31 852 0 815 0 758V575C0 514 25 475 78 444Z\" fill=\"#0736E8\"/>"
            + "<circle cx=\"334\" cy=\"326\" r=\"91\" fill=\"#0067FF\"/></g></svg>";

    private static final String CSS = """
            :root{--bg:#f5f6fb;--surface:#fff;--surface-2:#f1f3f9;--border:#e2e6f0;--text:#0f172a;--muted:#586174;
            --accent:#4051b5;--accent-soft:#e0e7ff;--pass:#0e8a5f;--pass-soft:#e2f5ec;--fail:#c42f3a;--fail-soft:#fde9ea;
            --idle:#8b94a6;--idle-soft:#eceff5;--brand:#064fff;--shadow:0 1px 2px rgba(15,23,42,.05),0 8px 24px rgba(15,23,42,.06);
            --sans:"Space Grotesk","Inter",ui-sans-serif,system-ui,-apple-system,"Segoe UI",sans-serif;
            --mono:"IBM Plex Mono",ui-monospace,"SF Mono","JetBrains Mono",Menlo,Consolas,monospace;color-scheme:light dark}
            @media (prefers-color-scheme:dark){:root{--bg:#070b16;--surface:#0e1424;--surface-2:#141b2e;--border:#222b41;
            --text:#eef2f8;--muted:#98a3b8;--accent:#a5b4fc;--accent-soft:#1e2452;--pass:#3ccf91;--pass-soft:rgba(60,207,145,.12);
            --fail:#f47c7c;--fail-soft:rgba(244,124,124,.12);--idle:#6b768c;--idle-soft:rgba(148,163,184,.1);
            --shadow:0 1px 2px rgba(0,0,0,.4),0 10px 30px rgba(0,0,0,.35)}}
            *{box-sizing:border-box}html{-webkit-text-size-adjust:100%}
            body{margin:0;background:var(--bg);color:var(--text);font:15px/1.55 var(--sans);-webkit-font-smoothing:antialiased}
            a{color:var(--accent);text-decoration:none}a:hover{text-decoration:underline}
            code,pre{font-family:var(--mono);font-size:.85em}
            .page{max-width:1040px;margin:0 auto;padding:28px 16px 64px}
            .top{display:flex;justify-content:space-between;align-items:flex-end;gap:16px;flex-wrap:wrap;margin-bottom:22px}
            .brand{display:flex;align-items:center;gap:12px}.mark{flex:none}
            .eyebrow{margin:0;font:600 .72rem/1 var(--mono);letter-spacing:.12em;text-transform:uppercase;color:var(--muted)}
            h1{margin:.3rem 0 0;font-size:1.6rem;letter-spacing:-.02em;line-height:1.1}
            .meta{display:flex;gap:14px;align-items:center;color:var(--muted);font-size:.85rem}
            .where{padding:4px 10px;border:1px solid var(--border);border-radius:999px;background:var(--surface)}
            .arrow{vertical-align:-2px;margin-left:3px}
            .hero{position:relative;overflow:hidden;display:grid;grid-template-columns:1fr auto;gap:28px;padding:28px;
            border:1px solid var(--border);border-radius:20px;background:var(--surface);box-shadow:var(--shadow);margin-bottom:40px}
            .hero::before{content:"";position:absolute;inset:0;pointer-events:none;
            background:radial-gradient(600px 260px at 100% 0%,var(--tint),transparent 70%)}
            .hero.passed{--tint:var(--pass-soft);--tone:var(--pass)}.hero.failed{--tint:var(--fail-soft);--tone:var(--fail)}
            .hero.incomplete{--tint:var(--accent-soft);--tone:var(--accent)}
            .hero>*{position:relative}
            .hero-status{display:inline-flex;align-items:center;gap:8px;padding:6px 14px 6px 10px;border-radius:999px;
            background:var(--tint);color:var(--tone);font-weight:700;letter-spacing:.02em}
            .hero h2{margin:14px 0 8px;font-size:1.65rem;letter-spacing:-.02em;line-height:1.2}
            .hero h2 a{color:var(--tone);text-decoration:underline;text-decoration-thickness:2px;text-underline-offset:4px}
            .lead{margin:0;color:var(--muted);max-width:52ch}
            .why{margin:12px 0 16px;padding:14px 16px;border-radius:12px;background:var(--fail-soft);color:var(--text);
            border:1px solid color-mix(in srgb,var(--fail) 25%,transparent);white-space:pre-wrap;word-break:break-word;
            max-height:320px;overflow:auto;line-height:1.5}
            .why.small{margin:14px 0 0;font-size:.8rem}
            .actions{display:flex;flex-wrap:wrap;gap:10px;align-items:stretch}
            .action{display:flex;flex-direction:column;gap:4px;padding:10px 14px;border-radius:12px;background:var(--surface-2);
            border:1px solid var(--border)}
            .action span{font:600 .68rem/1 var(--mono);letter-spacing:.1em;text-transform:uppercase;color:var(--muted)}
            .action p{margin:0;font-size:.9rem}
            .guide-button{display:inline-flex;align-items:center;padding:10px 16px;border-radius:12px;background:var(--accent);
            color:#fff;font-weight:600}.guide-button:hover{text-decoration:none;filter:brightness(1.08)}
            @media (prefers-color-scheme:dark){.guide-button{color:#0b1020}}
            .hero-side{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:14px;min-width:150px}
            .ring{width:128px;height:128px}.ring-track{fill:none;stroke:var(--surface-2);stroke-width:10}
            .ring-fill{fill:none;stroke:var(--tone);stroke-width:10;stroke-linecap:round}
            .ring-num{text-anchor:middle;font:700 22px var(--sans);fill:var(--text)}
            .ring-label{text-anchor:middle;font:500 9px var(--mono);letter-spacing:.08em;fill:var(--muted);text-transform:uppercase}
            .strip{display:flex;gap:4px;margin:0;padding:0;list-style:none}
            .strip li{width:12px;height:12px;border-radius:4px;background:var(--idle-soft);border:1px solid var(--border)}
            .strip li.passed{background:var(--pass);border-color:var(--pass)}.strip li.failed{background:var(--fail);border-color:var(--fail)}
            section{margin-bottom:40px}
            .section-head{display:flex;justify-content:space-between;align-items:baseline;gap:16px;flex-wrap:wrap;margin-bottom:14px}
            .section-head h2{margin:0;font-size:1.15rem;letter-spacing:-.01em}
            .section-head p{margin:0;color:var(--muted);font-size:.88rem;max-width:62ch}
            .timeline{list-style:none;margin:0;padding:0;position:relative}
            .timeline::before{content:"";position:absolute;left:15px;top:18px;bottom:18px;width:2px;background:var(--border)}
            .gate{position:relative;padding-left:46px;margin-bottom:12px}
            .node{position:absolute;left:0;top:18px;width:32px;height:32px;border-radius:50%;display:grid;place-items:center;
            background:var(--surface);border:2px solid var(--border);color:var(--idle)}
            .gate.passed .node{border-color:var(--pass);color:var(--pass);background:var(--pass-soft)}
            .gate.failed .node{border-color:var(--fail);color:#fff;background:var(--fail)}
            .card{background:var(--surface);border:1px solid var(--border);border-radius:16px;padding:16px 18px;
            box-shadow:var(--shadow)}
            .gate.failed .card{border-color:color-mix(in srgb,var(--fail) 45%,var(--border))}
            .gate.not-run .card{background:transparent;box-shadow:none;border-style:dashed}
            .card>header{display:flex;justify-content:space-between;gap:16px;align-items:flex-start}
            .card h3{margin:0;font-size:1.02rem}
            .purpose{margin:2px 0 0;color:var(--muted);font-size:.86rem;max-width:58ch}
            .result{text-align:right;display:flex;flex-direction:column;gap:2px;flex:none;max-width:45%}
            .result strong{font-size:.92rem}.gate.failed .result strong{color:var(--fail)}
            .gate.not-run .result strong{color:var(--muted);font-weight:500}
            .when{font:.72rem var(--mono);color:var(--muted)}.when.earlier{color:var(--accent)}
            .metric{margin-top:14px}
            .metric-head{display:flex;justify-content:space-between;gap:12px;font-size:.82rem;color:var(--muted);margin-bottom:6px}
            .metric-head b{color:var(--text);font-size:.95rem}
            .bar{position:relative;height:8px;border-radius:999px;background:var(--surface-2);overflow:visible}
            .bar i{display:block;height:100%;border-radius:999px;background:linear-gradient(90deg,var(--pass),color-mix(in srgb,var(--pass) 70%,#7dd3fc))}
            .metric.low .bar i{background:linear-gradient(90deg,var(--fail),color-mix(in srgb,var(--fail) 70%,#fbbf24))}
            .bar em{position:absolute;top:-4px;width:2px;height:16px;margin-left:-1px;background:var(--text);opacity:.55;border-radius:2px}
            .findings{margin-top:14px;border-top:1px solid var(--border);padding-top:10px}
            .findings summary{cursor:pointer;font-weight:600;font-size:.88rem;color:var(--text)}
            .findings ul{list-style:none;margin:10px 0 0;padding:0;display:grid;gap:8px}.findings ul:empty{display:none}
            .rest{margin-top:8px}.rest summary{cursor:pointer;font-size:.84rem;color:var(--accent);font-weight:600}
            .findings li{padding:10px 12px;border-radius:10px;background:var(--surface-2);border:1px solid var(--border)}
            .f-head{display:flex;flex-wrap:wrap;gap:8px;align-items:center}
            .rule{font:600 .72rem/1.4 var(--mono);padding:2px 8px;border-radius:6px;background:var(--accent-soft);color:var(--accent)}
            .loc{color:var(--muted);word-break:break-all}
            .msg{margin:6px 0 0;font-size:.86rem;white-space:pre-wrap;word-break:break-word}
            .more{margin:8px 0 0;font-size:.82rem;color:var(--muted)}
            .links{display:flex;flex-wrap:wrap;gap:8px 16px;align-items:center;margin-top:14px;font-size:.84rem}
            .chip{font:600 .7rem var(--mono);padding:2px 8px;border-radius:6px;border:1px solid var(--border);color:var(--muted)}
            .cmd{margin-left:auto;color:var(--muted);background:var(--surface-2);padding:3px 8px;border-radius:6px}
            .env{display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:10px;margin:0}
            .env div{padding:12px 14px;border-radius:12px;background:var(--surface);border:1px solid var(--border)}
            .env dt{font:600 .68rem/1 var(--mono);letter-spacing:.1em;text-transform:uppercase;color:var(--muted)}
            .env dd{margin:6px 0 0;font-weight:500;word-break:break-word}
            footer{color:var(--muted);font-size:.84rem;border-top:1px solid var(--border);padding-top:18px}
            footer p{margin:4px 0}
            @media (max-width:720px){.hero{grid-template-columns:1fr;padding:22px}.hero-side{flex-direction:row;justify-content:flex-start}
            .ring{width:96px;height:96px}.card>header{flex-direction:column}.result{text-align:left;max-width:none}
            .cmd{margin-left:0}.hero h2{font-size:1.35rem}}
            @media print{.hero::before{display:none}.card,.hero{box-shadow:none}}
            """;

    private HtmlReport() {}
}
