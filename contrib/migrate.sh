#!/usr/bin/env bash

set -e

#MIGRATE_SCRIPT=-1.0.0_to_0.0.0.sql
#MIGRATE_SCRIPT=0.0.0_to_1.0.0.sql
MIGRATE_SCRIPT=1.0.0_to_2.0.0.sql

if [ -z $PGPASSWORD ]; then
        echo 'PGPASSWORD not set'
        exit 2
fi

PSQL="psql -w -h 127.0.0.1 nimrod_portal -U nimrod-portal"

for user in $($PSQL -At -c 'SELECT username FROM public.portal_users'); do
        echo "Migrating user $user..."
        $PSQL <<< $(cat -- <(echo "SET search_path TO $user;") $MIGRATE_SCRIPT)
        echo "Fixing permissions for $user..."
        $PSQL -At -c "SELECT public.portal_fix_user('$user');"
done
