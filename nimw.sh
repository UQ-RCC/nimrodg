#!/bin/bash

# A wrapper to invoke nimrod normally via gradle

CMD="gradle --no-daemon cli2 -PappArgs=["

for var in "$@"
do
	CMD+="'$(printf "%q" "$var")',"
done

if [ "$#" -gt "1" ];
then
	CMD=${CMD%?}
fi

CMD+="]"

$CMD
