#!/bin/sh
# Runs the same raw bytecode through the Ada and Rust interpreters and
# fails if their results differ. Run from the repository root after building both.
set -eu
ADA=ada-jvm/build/bin/ada_jvm
RUST=ada-jvm/rust-vm/target/release/jesus-rust-vm
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
status=0
check() {
  name=$1; shift
  printf "$@" > "$tmp/$name.code"
  a=$("$ADA" "$tmp/$name.code" | tr -d ' ')
  r=$("$RUST" "$tmp/$name.code" | tr -d ' ')
  if [ "$a" != "$r" ]; then
    echo "MISMATCH $name: ada=$a rust=$r"; status=1
  else
    echo "ok $name = $a"
  fi
}
check add  '\020\010\020\005\140\254'
check div  '\002\020\003\154\254'
check neg  '\020\375\254'
check mul  '\020\007\020\006\150\254'
check sub  '\020\011\020\004\144\254'
exit $status
