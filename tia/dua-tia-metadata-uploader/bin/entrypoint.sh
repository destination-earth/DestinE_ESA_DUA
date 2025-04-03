#!/bin/bash -e

source entrypoint-functions.sh

# resolve variable in queue name
UPLOAD_QUEUE_NAME="$(eval echo $UPLOAD_QUEUE_NAME)"

RECOVERY_QUEUE_NAME="recovery-$UPLOAD_QUEUE_NAME"
REJECTED_QUEUE_NAME="rejected-$UPLOAD_QUEUE_NAME"

WORK_DIR=/tmp

echo "--------------------------------"
echo "   DUA-TIA Metadata Uploader    "
echo "--------------------------------"
echo "redis host         : $REDIS_HOST"
echo "redis port         : $REDIS_PORT"
echo "queue name         : $UPLOAD_QUEUE_NAME"
echo "recovery queue name: $RECOVERY_QUEUE_NAME"
echo "rejected queue name: $REJECTED_QUEUE_NAME"
echo "splunk HEC token   : $SPLUNK_HEC_TOKEN"
echo "splunk document url: $SPLUNK_DOCUMENT_URL"
echo "RAG token          : $RAG_TOKEN"
echo "RAG knowledge url  : $RAG_KNOWLEDGE_URL"
echo "RAG document url   : $RAG_DOCUMENT_URL"
echo "working directory  : $WORK_DIR"
echo "--------------------------------"

while true ; do

    EVENT=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT --raw spop $UPLOAD_QUEUE_NAME)

    if [ ! -z "$EVENT" ]; then
        echo "metadata-upload event received ($EVENT)"

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
