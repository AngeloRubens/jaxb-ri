#!/bin/bash
# Usage: build-variant.sh <git-ref> <variant-name> <variants-dir>
# Builds jaxb-core and jaxb-runtime of <git-ref> (tests skipped: the test suite runs in the
# "JAXB RI" workflow) and copies the two jars to <variants-dir>/<variant-name>/.
set -euo pipefail
REF=$1; NAME=$2; OUT=$3
WT=$(mktemp -d)/src
git worktree add -q --detach "$WT" "$REF"
mvn -B -q -f "$WT/jaxb-ri/pom.xml" install -pl runtime/impl -am -DskipTests -Dmaven.javadoc.skip=true -Dmaven.source.skip=true
mkdir -p "$OUT/$NAME"
cp "$WT"/jaxb-ri/core/target/jaxb-core-*-SNAPSHOT.jar "$OUT/$NAME/jaxb-core.jar"
cp "$WT"/jaxb-ri/runtime/impl/target/jaxb-runtime-*-SNAPSHOT.jar "$OUT/$NAME/jaxb-runtime.jar"
echo "$REF $(git -C "$WT" rev-parse HEAD)" > "$OUT/$NAME/COMMIT"
cat "$OUT/$NAME/COMMIT"
git worktree remove --force "$WT"
