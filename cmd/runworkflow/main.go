// Command runworkflow runs one job of a generated project's GitHub Actions workflow on this machine, so CI proves
// the workflow a user gets instead of a copy of it.
//
//	go run ./cmd/runworkflow -dir orders [-workflow .github/workflows/ci.yml] [-job build]
//
// It runs what the project templates use and refuses anything else, so a new kind of step cannot pass untested:
// run steps with bash, docker:// actions and actions/checkout, which it skips because the project is already on
// disk. Expressions (${{ }}) are refused in steps for the same reason.
package main

import (
	"flag"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strings"

	"go.yaml.in/yaml/v3"
)

type workflow struct {
	Env  map[string]string `yaml:"env"`
	Jobs map[string]job    `yaml:"jobs"`
}

type job struct {
	Env   map[string]string `yaml:"env"`
	Steps []step            `yaml:"steps"`
}

type step struct {
	Name string            `yaml:"name"`
	Run  string            `yaml:"run"`
	Uses string            `yaml:"uses"`
	With map[string]string `yaml:"with"`
	Env  map[string]string `yaml:"env"`
}

func main() {
	dir := flag.String("dir", ".", "the generated project")
	file := flag.String("workflow", ".github/workflows/ci.yml", "the workflow, relative to -dir")
	jobName := flag.String("job", "build", "the job to run")
	flag.Parse()
	if err := run(*dir, *file, *jobName); err != nil {
		fmt.Fprintln(os.Stderr, "runworkflow:", err)
		os.Exit(1)
	}
}

func run(dir, file, jobName string) error {
	abs, err := filepath.Abs(dir)
	if err != nil {
		return err
	}
	b, err := os.ReadFile(filepath.Join(abs, file))
	if err != nil {
		return err
	}
	var w workflow
	if err := yaml.Unmarshal(b, &w); err != nil {
		return fmt.Errorf("%s: %w", file, err)
	}
	j, ok := w.Jobs[jobName]
	if !ok {
		return fmt.Errorf("%s: no job %s", file, jobName)
	}
	for i, s := range j.Steps {
		label := s.Name
		if label == "" {
			label = s.Uses
		}
		fmt.Printf("::group::step %d: %s\n", i+1, label)
		env := merge(w.Env, j.Env, s.Env)
		if err := runStep(abs, s, env); err != nil {
			fmt.Println("::endgroup::")
			return fmt.Errorf("step %d (%s): %w", i+1, label, err)
		}
		fmt.Println("::endgroup::")
	}
	return nil
}

func runStep(dir string, s step, env map[string]string) error {
	for _, text := range append([]string{s.Run, s.With["args"]}, values(s.Env)...) {
		if strings.Contains(text, "${{") {
			return fmt.Errorf("expressions are not supported: %q", text)
		}
	}
	switch {
	case s.Run != "":
		// What GitHub runs for a bash step on Linux.
		cmd := exec.Command("bash", "--noprofile", "--norc", "-eo", "pipefail", "-c", s.Run)
		return execute(cmd, dir, env)
	case strings.HasPrefix(s.Uses, "actions/checkout@"):
		fmt.Println("skipped: the project is already on disk")
		return nil
	case strings.HasPrefix(s.Uses, "docker://"):
		for k := range s.With {
			if k != "args" {
				return fmt.Errorf("with.%s is not supported", k)
			}
		}
		args := []string{"run", "--rm", "-v", dir + ":/github/workspace", "-w", "/github/workspace"}
		for _, k := range sortedKeys(env) {
			args = append(args, "-e", k+"="+env[k])
		}
		args = append(args, strings.TrimPrefix(s.Uses, "docker://"))
		args = append(args, strings.Fields(s.With["args"])...)
		return execute(exec.Command("docker", args...), dir, env)
	default:
		return fmt.Errorf("unsupported step: uses %q", s.Uses)
	}
}

func execute(cmd *exec.Cmd, dir string, env map[string]string) error {
	cmd.Dir = dir
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.Env = os.Environ()
	for _, k := range sortedKeys(env) {
		cmd.Env = append(cmd.Env, k+"="+env[k])
	}
	return cmd.Run()
}

func merge(maps ...map[string]string) map[string]string {
	out := map[string]string{}
	for _, m := range maps {
		for k, v := range m {
			out[k] = v
		}
	}
	return out
}

func values(m map[string]string) []string {
	var out []string
	for _, k := range sortedKeys(m) {
		out = append(out, m[k])
	}
	return out
}

func sortedKeys(m map[string]string) []string {
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	return keys
}
