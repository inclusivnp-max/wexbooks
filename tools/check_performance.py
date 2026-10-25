#!/usr/bin/env python3
"""Compare matching Pixel Macrobenchmark scenarios. Never compares native text to EPUB."""
import argparse
import json
import sys
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('baseline', type=Path)
parser.add_argument('current', type=Path)
args = parser.parse_args()

def cases(path):
    return {b['name'].split('[')[0]: b for b in json.loads(path.read_text())['benchmarks']}

base, current = cases(args.baseline), cases(args.current)
checks = []
startup_old = base['libraryColdStart']['metrics']['timeToInitialDisplayMs']['median']
startup_new = current['libraryColdStart']['metrics']['timeToInitialDisplayMs']['median']
checks.append(('library startup median', startup_new, startup_old * 1.15))
for metric, tolerance in [('frameDurationCpuMs', None), ('frameOverrunMs', 2.0)]:
    old = base['epubPageTurns']['sampledMetrics'][metric]['P95']
    new = current['epubPageTurns']['sampledMetrics'][metric]['P95']
    limit = old * 1.15 if tolerance is None else old + tolerance
    checks.append((f'EPUB {metric} P95', new, limit))
if 'nativePageTurns' in current:
    value = current['nativePageTurns']['sampledMetrics']['frameDurationCpuMs']['P95']
    checks.append(('native CPU frame P95 (initial 60Hz budget)', value, 16.7))
passed = True
for name, value, limit in checks:
    ok = value <= limit
    passed &= ok
    print(f'{"PASS" if ok else "FAIL"}: {name}: {value:.3f} ms; limit {limit:.3f} ms')
sys.exit(0 if passed else 1)
