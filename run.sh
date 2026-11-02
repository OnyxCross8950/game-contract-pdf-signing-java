#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
OUT="$ROOT/.build"
mkdir -p "$OUT"
javac -d "$OUT" "$ROOT/src/main/java/com/example/gamesign/ContractSignApplication.java" "$ROOT/src/test/java/com/example/gamesign/ContractDecisionTest.java"
if [ "${1:-}" = "test" ]; then
  java -cp "$OUT" com.example.gamesign.ContractDecisionTest
  exit 0
fi
if [ "${1:-}" = "sign" ] && [ "$#" -eq 4 ]; then
  java -cp "$OUT" com.example.gamesign.ContractSignApplication "$2" "$3" "$4"
  exit 0
fi
echo "usage: ./run.sh test | ./run.sh sign contract.pdf cert.pem key.pem" >&2
exit 2
