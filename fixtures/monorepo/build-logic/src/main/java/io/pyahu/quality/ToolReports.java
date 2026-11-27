package io.pyahu.quality;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Reads the XML each tool writes. A missing or broken file reads as nothing, never as an error. */
final class ToolReports {

    record TestCase(String className, String name, double seconds, String status, String message) {}

    record Violation(String file, int line, String rule, String message, String url) {}

    record Counter(long missed, long covered) {
        long total() {
            return missed + covered;
        }

        double ratio() {
            return total() == 0 ? 1.0 : (double) covered / total();
        }

        Counter plus(Counter other) {
            return new Counter(missed + other.missed, covered + other.covered);
        }
    }

    record ClassCoverage(String className, String sourceFile, Counter lines) {}

    record Coverage(Counter lines, Counter branches, List<ClassCoverage> classes) {}

    record Mutation(String sourceFile, String mutatedClass, String method, int line, String mutator, String description, String status, boolean detected) {}

    /** JUnit XML of one test task, such as build/test-results/test, as far as that task's last run wrote it. */
    static List<TestCase> tests(Path dir, TaskRun run) {
        List<TestCase> cases = new ArrayList<>();
        if (run == null || !Files.isDirectory(dir)) {
            return cases;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "TEST-*.xml")) {
            for (Path file : files) {
                if (!run.wrote(file)) {
                    continue;
                }
                Document doc = parse(file);
                if (doc == null) {
                    continue;
                }
                NodeList nodes = doc.getElementsByTagName("testcase");
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element tc = (Element) nodes.item(i);
                    String status = "passed";
                    String message = "";
                    Element problem = first(tc, "failure");
                    if (problem == null) {
                        problem = first(tc, "error");
                    }
                    if (problem != null) {
                        status = "failed";
                        message = problem.getAttribute("message");
                        if (message.isBlank()) {
                            message = problem.getTextContent();
                        }
                    } else if (first(tc, "skipped") != null) {
                        status = "skipped";
                    }
                    cases.add(new TestCase(
                            tc.getAttribute("classname"), tc.getAttribute("name"), number(tc.getAttribute("time")), status, message));
                }
            }
        } catch (IOException e) {
            return cases;
        }
        return cases;
    }

    /** PMD's XML report. */
    static List<Violation> pmd(Path file, Path projectDir) {
        List<Violation> out = new ArrayList<>();
        Document doc = parse(file);
        if (doc == null) {
            return out;
        }
        NodeList files = doc.getElementsByTagName("file");
        for (int i = 0; i < files.getLength(); i++) {
            Element f = (Element) files.item(i);
            String name = relative(projectDir, f.getAttribute("name"));
            NodeList violations = f.getElementsByTagName("violation");
            for (int j = 0; j < violations.getLength(); j++) {
                Element v = (Element) violations.item(j);
                out.add(new Violation(
                        name,
                        (int) number(v.getAttribute("beginline")),
                        v.getAttribute("rule"),
                        v.getTextContent().strip(),
                        v.getAttribute("externalInfoUrl")));
            }
        }
        return out;
    }

    /** JaCoCo's XML report, limited to the packages the gate checks: domain and application. */
    static Coverage businessCoverage(Path file) {
        Document doc = parse(file);
        if (doc == null) {
            return null;
        }
        Counter lines = new Counter(0, 0);
        Counter branches = new Counter(0, 0);
        List<ClassCoverage> classes = new ArrayList<>();
        NodeList packages = doc.getElementsByTagName("package");
        for (int i = 0; i < packages.getLength(); i++) {
            Element p = (Element) packages.item(i);
            String name = "/" + p.getAttribute("name") + "/";
            if (!name.contains("/domain/") && !name.contains("/application/")) {
                continue;
            }
            for (Element child : children(p, "counter")) {
                Counter c = counter(child);
                if ("LINE".equals(child.getAttribute("type"))) {
                    lines = lines.plus(c);
                } else if ("BRANCH".equals(child.getAttribute("type"))) {
                    branches = branches.plus(c);
                }
            }
            for (Element cls : children(p, "class")) {
                for (Element child : children(cls, "counter")) {
                    if ("LINE".equals(child.getAttribute("type"))) {
                        classes.add(new ClassCoverage(
                                cls.getAttribute("name").replace('/', '.'),
                                p.getAttribute("name") + "/" + cls.getAttribute("sourcefilename"),
                                counter(child)));
                    }
                }
            }
        }
        return new Coverage(lines, branches, classes);
    }

    /** PIT's mutations.xml. */
    static List<Mutation> mutations(Path file) {
        List<Mutation> out = new ArrayList<>();
        Document doc = parse(file);
        if (doc == null) {
            return out;
        }
        NodeList nodes = doc.getElementsByTagName("mutation");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element m = (Element) nodes.item(i);
            String mutator = text(m, "mutator");
            out.add(new Mutation(
                    text(m, "sourceFile"),
                    text(m, "mutatedClass"),
                    text(m, "mutatedMethod"),
                    (int) number(text(m, "lineNumber")),
                    mutator.substring(mutator.lastIndexOf('.') + 1),
                    text(m, "description"),
                    m.getAttribute("status"),
                    Boolean.parseBoolean(m.getAttribute("detected"))));
        }
        return out;
    }

    static String relative(Path projectDir, String path) {
        try {
            Path p = Path.of(path);
            return p.isAbsolute() && p.startsWith(projectDir) ? projectDir.relativize(p).toString() : path;
        } catch (RuntimeException e) {
            return path;
        }
    }

    private static Document parse(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            // JaCoCo names a DTD it does not ship; never fetch it.
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static Element first(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() == 0 ? null : (Element) list.item(0);
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(tag)) {
                out.add(e);
            }
        }
        return out;
    }

    private static String text(Element parent, String tag) {
        Element e = first(parent, tag);
        return e == null ? "" : e.getTextContent().strip();
    }

    private static Counter counter(Element e) {
        return new Counter((long) number(e.getAttribute("missed")), (long) number(e.getAttribute("covered")));
    }

    private static double number(String value) {
        try {
            return Double.parseDouble(value.replace(",", ""));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private ToolReports() {}
}
