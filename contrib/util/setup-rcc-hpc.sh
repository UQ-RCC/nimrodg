#!/usr/bin/env bash
set -e

SCRIPTPATH="$( cd "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"
ROOTDIR=$(realpath "$SCRIPTPATH/../..")
NIMROD=$ROOTDIR/nimw.sh

export XDG_CONFIG_DIRS=$SCRIPTPATH:$ROOTDIR:$XDG_CONFIG_DIRS

$NIMROD setup init ~/.config/nimrod/nimrod-setup.ini

# Setting an invalid AMQP uri to test the status querying
$NIMROD resource add tinaroo hpc -- \
    --platform=x86_64-pc-linux-musl \
    --transport=openssh \
    --uri=ssh://tinaroo.rcc.uq.edu.au \
    --limit=10 \
    --max-batch-size=2 \
    --type=pbspro \
    --ncpus=1 \
    --mem=1GiB \
    --walltime=24:01:00 \
    --account=UQ-RCC \
    --queue=workq \
    --query-interval=30s

$NIMROD experiment add exp1 - <<EOF
parameter x integer range from 1 to 1000 step 1

task main
  onerror ignore
  exec echo $x
  exec sleep 10
endtask
EOF

$NIMROD resource assign tinaroo exp1
