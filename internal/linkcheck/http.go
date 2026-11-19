package linkcheck

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"
)

type waitFunc func(context.Context, time.Duration) error

func waitHTTP(ctx context.Context, delay time.Duration) error {
	timer := time.NewTimer(delay)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}

func readURL(client *http.Client, address string) ([]byte, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Minute)
	defer cancel()
	return readURLWithRetry(ctx, client, address, time.Now, waitHTTP)
}

// GET is idempotent. Retry only transport errors, 408, 429 and 5xx, within a fixed budget.
func readURLWithRetry(ctx context.Context, client *http.Client, address string, now func() time.Time, wait waitFunc) ([]byte, error) {
	backoff := []time.Duration{5 * time.Second, 15 * time.Second, 30 * time.Second}
	var last error
	for attempt := 0; attempt <= len(backoff); attempt++ {
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, address, nil)
		if err != nil {
			return nil, err
		}
		req.Header.Set("User-Agent", UserAgent)
		req.Header.Set("Accept", "text/html,application/xhtml+xml,application/xml,*/*")
		resp, err := client.Do(req)
		retryAfter := ""
		if err != nil {
			if ctx.Err() != nil {
				return nil, ctx.Err()
			}
			last = err
		} else {
			retryAfter = resp.Header.Get("Retry-After")
			if resp.StatusCode == http.StatusOK {
				body, readErr := io.ReadAll(io.LimitReader(resp.Body, 32<<20))
				resp.Body.Close()
				if readErr == nil {
					return body, nil
				}
				last = readErr
			} else {
				resp.Body.Close()
				last = fmt.Errorf("%s: status %d", address, resp.StatusCode)
				if resp.StatusCode != 408 && resp.StatusCode != 429 && resp.StatusCode < 500 {
					return nil, last
				}
			}
		}
		if attempt == len(backoff) {
			break
		}
		delay, err := httpRetryDelay(retryAfter, now(), backoff[attempt])
		if err != nil {
			return nil, fmt.Errorf("%w; %v", last, err)
		}
		if err := wait(ctx, delay); err != nil {
			return nil, fmt.Errorf("%w; retry stopped: %v", last, err)
		}
	}
	return nil, last
}

func httpRetryDelay(header string, now time.Time, fallback time.Duration) (time.Duration, error) {
	delay := fallback
	if value := strings.TrimSpace(header); value != "" {
		if strings.Trim(value, "0123456789") == "" {
			seconds, err := strconv.ParseUint(value, 10, 64)
			if err != nil || seconds > 120 {
				return 0, fmt.Errorf("Retry-After exceeds the 120-second retry budget")
			}
			delay = time.Duration(seconds) * time.Second
		} else if date, err := http.ParseTime(value); err == nil {
			delay = date.Sub(now)
			if delay < 0 {
				delay = 0
			}
		}
	}
	if delay > 2*time.Minute {
		return 0, fmt.Errorf("Retry-After exceeds the 120-second retry budget")
	}
	return delay, nil
}
