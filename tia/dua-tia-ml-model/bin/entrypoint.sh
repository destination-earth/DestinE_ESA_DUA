#!/bin/bash -e

source entrypoint-functions.sh

# resolve variable in queue name
CLASSIFY_QUEUE_NAME="$(eval echo $CLASSIFY_QUEUE_NAME)"
UPLOAD_QUEUE_NAME="$(eval echo $UPLOAD_QUEUE_NAME)"

RECOVERY_QUEUE_NAME="recovery-$CLASSIFY_QUEUE_NAME"
REJECTED_QUEUE_NAME="rejected-$CLASSIFY_QUEUE_NAME"

WORK_DIR=/tmp

echo "--------------------------------"
echo "        DUA-TIA ML Model        "
echo "--------------------------------"
echo "redis host         : $REDIS_HOST"
echo "redis port         : $REDIS_PORT"
echo "queue name         : $CLASSIFY_QUEUE_NAME"
echo "recovery queue name: $RECOVERY_QUEUE_NAME"
echo "rejected queue name: $REJECTED_QUEUE_NAME"
echo "upload queue name  : $UPLOAD_QUEUE_NAME"
echo "working directory  : $WORK_DIR"
echo "test mode          : $TEST_MODE"
echo "--------------------------------"

while true ; do

    EVENT=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT --raw spop $CLASSIFY_QUEUE_NAME)

    if [ ! -z "$EVENT" ]; then
        echo "ml-model inference event received ($EVENT)"

        if process_event "$EVENT" ; then
            echo "event correctly processed ($EVENT)"
        else
            echo "ERROR: event processed with errors ($EVENT)" 1>&2
        fi

    else
        echo "waiting for incoming event"
        sleep 10
    fi

done
