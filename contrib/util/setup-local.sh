#!/usr/bin/env bash
set -e

SCRIPTPATH="$( cd "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"
ROOTDIR=$(realpath "$SCRIPTPATH/../..")
NIMROD=$ROOTDIR/nimw.sh

export XDG_CONFIG_DIRS=$SCRIPTPATH:$ROOTDIR:$XDG_CONFIG_DIRS

$NIMROD setup init ~/.config/nimrod/nimrod-setup.ini

# Setting an invalid AMQP uri to test the status querying
$NIMROD resource add local local -- --capture-output=stream

$NIMROD experiment add exp1 - <<EOF
parameter x integer range from 1 to 1000 step 1

task main
  onerror ignore
  exec echo $x
  exec sleep 10
endtask
EOF

$NIMROD resource assign local exp1
