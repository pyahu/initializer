//go:build acceptance

// Package observability proves that one request's trace, metric, and log reach an
// OTLP backend and correlate.
package observability

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"testing"
	"time"

	h "github.com/pyahu/initializer/acceptance/harness"
)

const service = "single-hexagonal"

// lgtmReady is the line Testcontainers' LgtmStackContainer (2.0.5) waits for.
const lgtmReady = "The OpenTelemetry collector and the Grafana LGTM stack are up and running."

type stack struct {
	tempo, prom, loki string
	base              string
	traceID           string
	t0                time.Time
}

// start runs a fresh backend and the fixture, then sends one GET /ping with a sampled parent.
func start(t *testing.T) *stack {
	t.Helper()
	net := h.Network(t)
	lgtm := h.Name("lgtm")
	h.Start(t, lgtm, "--network", net, "--network-alias", "lgtm", "-p", "127.0.0.1::3100", "-p", "127.0.0.1::3200", "-p", "127.0.0.1::9090", h.ImageRef(t, "otel-lgtm-image"))
	h.WaitLog(t, lgtm, lgtmReady, 120*time.Second)
	app := h.Name("app")
	h.Start(t, app, "--network", net, "-p", "127.0.0.1::8080",
		"-e", "OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm:4318",
		"-e", "OTEL_SERVICE_NAME="+service,
		"-e", "MANAGEMENT_OTLP_METRICS_EXPORT_STEP=5s",
		h.FixtureImage())
	base := "http://" + h.Port(t, app, "8080")
	h.WaitHTTP(t, base+"/readyz", 250*time.Millisecond, 60*time.Second)
	s := &stack{
		tempo: "http://" + h.Port(t, lgtm, "3200"), prom: "http://" + h.Port(t, lgtm, "9090"), loki: "http://" + h.Port(t, lgtm, "3100"),
		traceID: h.RandomHex(16), t0: time.Now(),
	}
	s.base = base
	s.ping(t)
	return s
}

// ping sends GET /ping with a sampled parent for the current trace ID.
func (s *stack) ping(t *testing.T) {
	t.Helper()
	req, _ := http.NewRequest(http.MethodGet, s.base+"/ping", nil)
	req.Header.Set("traceparent", fmt.Sprintf("00-%s-%s-01", s.traceID, h.RandomHex(8)))
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	body, _ := io.ReadAll(resp.Body)
	resp.Body.Close()
	if resp.StatusCode != 200 || string(body) != "pong" {
		t.Fatalf("GET /ping: %d %q", resp.StatusCode, body)
	}
}

// poll retries f every second for at most 60 s.
func poll(t *testing.T, what string, f func() (bool, string)) string {
	t.Helper()
	deadline := time.Now().Add(60 * time.Second)
	last := ""
	for time.Now().Before(deadline) {
		ok, detail := f()
		if ok {
			return detail
		}
		last = detail
		time.Sleep(time.Second)
	}
	t.Fatalf("%s not found within 60 s; last response: %s", what, last)
	return ""
}

func getJSON(u string, accept string, out any) (string, error) {
	req, _ := http.NewRequest(http.MethodGet, u, nil)
	if accept != "" {
		req.Header.Set("Accept", accept)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 {
		return string(b), fmt.Errorf("status %d", resp.StatusCode)
	}
	return string(b), json.Unmarshal(b, out)
}

type kv struct {
	Key   string `json:"key"`
	Value struct {
		StringValue string `json:"stringValue"`
	} `json:"value"`
}

type resourceSpans struct {
	Resource struct {
		Attributes []kv `json:"attributes"`
	} `json:"resource"`
	ScopeSpans []struct {
		Spans []struct {
			Name       string `json:"name"`
			Kind       any    `json:"kind"`
			Attributes []kv   `json:"attributes"`
		} `json:"spans"`
	} `json:"scopeSpans"`
}

func attr(list []kv, key string) string {
	for _, a := range list {
		if a.Key == key {
			return a.Value.StringValue
		}
	}
	return ""
}

func (s *stack) trace() (bool, string) {
	var body struct {
		Batches       []resourceSpans `json:"batches"`
		ResourceSpans []resourceSpans `json:"resourceSpans"`
		Trace         *struct {
			ResourceSpans []resourceSpans `json:"resourceSpans"`
		} `json:"trace"`
	}
	raw, err := getJSON(s.tempo+"/api/traces/"+s.traceID, "application/json", &body)
	if err != nil {
		return false, fmt.Sprintf("%v %s", err, raw)
	}
	all := append(body.Batches, body.ResourceSpans...)
	if body.Trace != nil {
		all = append(all, body.Trace.ResourceSpans...)
	}
	for _, rs := range all {
		if attr(rs.Resource.Attributes, "service.name") != service {
			continue
		}
		for _, ss := range rs.ScopeSpans {
			for _, sp := range ss.Spans {
				kind := fmt.Sprint(sp.Kind)
				if (kind == "SPAN_KIND_SERVER" || kind == "2") && attr(sp.Attributes, "uri") == "/ping" {
					return true, fmt.Sprintf("Tempo trace %s has server span %q with uri /ping", s.traceID, sp.Name)
				}
			}
		}
	}
	return false, raw
}

func (s *stack) metric() (bool, string) {
	q := fmt.Sprintf(`sum(http_server_requests_seconds_count{service_name=%q,uri="/ping"})`, service)
	var body struct {
		Status string `json:"status"`
		Data   struct {
			Result []struct {
				Value []any `json:"value"`
			} `json:"result"`
		} `json:"data"`
	}
	raw, err := getJSON(s.prom+"/api/v1/query?query="+url.QueryEscape(q), "", &body)
	if err != nil || body.Status != "success" || len(body.Data.Result) != 1 || len(body.Data.Result[0].Value) != 2 {
		return false, raw
	}
	v, _ := strconv.ParseFloat(fmt.Sprint(body.Data.Result[0].Value[1]), 64)
	return v >= 1, fmt.Sprintf("Prometheus %s = %v", q, v)
}

func (s *stack) log() (bool, string) {
	q := fmt.Sprintf(`{service_name=%q} | trace_id=%q`, service, s.traceID)
	u := fmt.Sprintf("%s/loki/api/v1/query_range?query=%s&start=%d&end=%d&limit=10", s.loki, url.QueryEscape(q),
		s.t0.Add(-60*time.Second).UnixNano(), time.Now().Add(5*time.Second).UnixNano())
	var body struct {
		Status string `json:"status"`
		Data   struct {
			Result []struct {
				Values [][]string `json:"values"`
			} `json:"result"`
		} `json:"data"`
	}
	raw, err := getJSON(u, "", &body)
	if err != nil || body.Status != "success" {
		return false, raw
	}
	for _, r := range body.Data.Result {
		for _, v := range r.Values {
			if len(v) == 2 && strings.Contains(v[1], "ping") {
				return true, fmt.Sprintf("Loki %s returned %q", q, v[1])
			}
		}
	}
	return false, raw
}

func (s *stack) exemplar() (bool, string) {
	q := fmt.Sprintf(`http_server_requests_seconds_bucket{service_name=%q,uri="/ping"}`, service)
	u := fmt.Sprintf("%s/api/v1/query_exemplars?query=%s&start=%d&end=%d", s.prom, url.QueryEscape(q), s.t0.Unix()-60, time.Now().Unix()+60)
	var body struct {
		Status string `json:"status"`
		Data   []struct {
			Exemplars []struct {
				Labels map[string]string `json:"labels"`
			} `json:"exemplars"`
		} `json:"data"`
	}
	raw, err := getJSON(u, "", &body)
	if err != nil || body.Status != "success" {
		return false, raw
	}
	for _, d := range body.Data {
		for _, e := range d.Exemplars {
			if e.Labels["trace_id"] == s.traceID {
				return true, fmt.Sprintf("Prometheus exemplar on %s carries trace_id %s", q, s.traceID)
			}
		}
	}
	return false, raw
}

func TestSignalsArrive(t *testing.T) {
	s := start(t)
	tr := poll(t, "trace", s.trace)
	m := poll(t, "metric", s.metric)
	l := poll(t, "log", s.log)
	// Four more requests, each with its own sampled parent. At sampling.probability 0.1 a
	// ratio-only sampler would keep all five with probability 1e-5; parent-based keeps them all.
	first := s.traceID
	for i := 0; i < 4; i++ {
		s.traceID = h.RandomHex(16)
		s.ping(t)
		poll(t, "trace "+s.traceID, s.trace)
	}
	s.traceID = first
	h.Result(t, "%s; %s; %s; 4 more sampled parents at probability 0.1, all 5 traces in Tempo", tr, m, l)
}

func TestLogCarriesTraceID(t *testing.T) {
	s := start(t)
	h.Result(t, "%s", poll(t, "log with the request's trace_id", s.log))
}

func TestExemplarLinksTrace(t *testing.T) {
	s := start(t)
	h.Result(t, "%s", poll(t, "exemplar with the request's trace_id", s.exemplar))
}
