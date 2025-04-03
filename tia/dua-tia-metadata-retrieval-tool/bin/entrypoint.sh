#!/bin/bash -e

source entrypoint-functions.sh

# resolve variable in queue name
QUEUE_NAME="$(eval echo $QUEUE_NAME)"

RECOVERY_QUEUE_NAME="recovery-$QUEUE_NAME"
REJECTED_QUEUE_NAME="rejected-$QUEUE_NAME"

CLASSIFY_QUEUE_NAME="classify"
UPLOAD_QUEUE_NAME="upload"

echo "--------------------------------"
echo "DUA-TIA Metadata Retrieval Tool "
echo "--------------------------------"
echo "redis host         : $REDIS_HOST"
echo "redis port         : $REDIS_PORT"
echo "queue name         : $QUEUE_NAME"
echo "recovery queue name: $RECOVERY_QUEUE_NAME"
echo "rejected queue name: $REJECTED_QUEUE_NAME"
echo "classify queue name: $CLASSIFY_QUEUE_NAME"
echo "upload queue name  : $UPLOAD_QUEUE_NAME"
echo "main data dir      : $DATA_PATH"
echo "grobid url         : $GROBID_URL"
echo "tika url           : $TIKA_URL"

# splunk credential are required to upload pdf thumbnail
echo "splunk credentials : $SPLUNK_CREDENTIALS"
echo "splunk token       : $SPLUNK_TOKEN"
echo "splunk document url: $SPLUNK_DOCUMENT_URL"
echo "splunk thumbnailurl: $SPLUNK_THUMBNAIL_URL"
echo "--------------------------------"

while true ; do

    EVENT=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT --raw spop $QUEUE_NAME)

    if [ ! -z "$EVENT" ]; then
        echo "metadata processing event received ($EVENT)"

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
