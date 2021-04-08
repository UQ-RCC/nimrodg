#!/usr/bin/env bash
set -e

##
# For reference:
# kill $(ps aux | grep nimrod | grep java | awk '{ print $2; }')
##

SCRIPTPATH="$( cd "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"
ROOTDIR=$(realpath "$SCRIPTPATH/../..")
NIMROD=$ROOTDIR/nimw.sh

if [ -z $OS_AUTH_URL -o -z $OS_USERNAME -o -z $OS_PASSWORD -o -z $OS_PROJECT_ID -o -z $OS_USER_DOMAIN_NAME ]; then
  echo '$OS_AUTH_URL, $OS_USERNAME, $OS_PASSWORD, $OS_PROJECT_ID, or $OS_USER_DOMAIN_NAME not set.'
  exit 1
fi

export XDG_CONFIG_DIRS=$SCRIPTPATH:$ROOTDIR:$XDG_CONFIG_DIRS

$NIMROD setup init ~/.config/nimrod/nimrod-setup.ini

##
# See org.jclouds.openstack.keystone.config.KeystoneProperties for property strings
# https://jclouds.apache.org/blog/2018/01/16/keystone-v3/
##
$NIMROD resource add nectar cloud -- \
    --agents-per-node=1 \
    --platform=x86_64-pc-linux-musl \
    --context=openstack-nova \
    --endpoint="$OS_AUTH_URL" \
    --username="$OS_USER_DOMAIN_NAME:$OS_USERNAME" \
    --password="$OS_PASSWORD" \
    --location-id=Melbourne \
    --hardware=m3.xsmall \
    --image-id="Melbourne/f8b79936-6616-4a22-b55d-0d0a1d27bceb" \
    --availability-zone=QRIScloud \
    -Djclouds.keystone.version=3 \
    -Djclouds.keystone.scope="projectId:$OS_PROJECT_ID"

$NIMROD experiment add exp1 - <<EOF
parameter x integer range from 1 to 1000 step 1

task main
  onerror ignore
  exec echo $x
  exec sleep 10
endtask
EOF

$NIMROD resource assign nectar exp1
