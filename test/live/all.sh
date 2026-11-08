#!/bin/bash
# Every suite on every supported version, one server at a time. Results go to work/results-*.json.
cd "$(dirname "$0")"
status=0
for v in ${PMT_VERSIONS:-26.3 26.2 26.1.2}; do
  node run.mjs "$v" sorting sorting-solo trunk trunk-solo crash switches || status=1
done
exit $status
