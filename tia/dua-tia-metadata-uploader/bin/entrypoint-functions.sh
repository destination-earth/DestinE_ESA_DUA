#!/bin/bash -e

function log_timestamp() {
    date -u +%FT%TZ
}

# $1 = EVENT
# $2 = STEP
# $3 = STATUS
function redis_document_processing_status() {
    local EVENT="$1"
    local STEP="$2"
    local STATUS="$3"
    local KEY="doc-processing-$EVENT"
    
    redis-cli -h $REDIS_HOST -p $REDIS_PORT hset "$KEY" "$STEP" "$(log_timestamp): $STATUS"
}

# $1 = EVENT
function redis_push_recovery_event() {
    local EVENT="$1"
    
    echo "pushing recovery event $EVENT to $RECOVERY_QUEUE_NAME"
    redis-cli -h $REDIS_HOST -p $REDIS_PORT sadd "$RECOVERY_QUEUE_NAME" "$EVENT"
}

# $1 = EVENT
function redis_push_reject_event() {
    local EVENT="$1"
    
    echo "pushing reject event $EVENT to $REJECTED_QUEUE_NAME"
    redis-cli -h $REDIS_HOST -p $REDIS_PORT sadd "$REJECTED_QUEUE_NAME" "$EVENT"
}

# $1 = EVENT
function redis_set_event_completed() {
    local EVENT="$1"
    
    echo "event $EVENT completed, removing from redis"
    redis-cli -h $REDIS_HOST -p $REDIS_PORT del "$EVENT"
}

# TODO: the process_event function shall be revied and completed

# $1 = EVENT
function process_event() {
    local EVENT="$1"
    local ORIGINATING_EVENT=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT --raw hget "$EVENT" originating_event || echo "")
    if [ -z "$ORIGINATING_EVENT" ]  ; then
        echo "ERROR: cannot get originating_event for $EVENT from redis" 1>&2
        redis_push_reject_event "$EVENT"
        return 1
    fi

    redis_document_processing_status "$ORIGINATING_EVENT" "upload" "starting"

    # prepare work directory
    local DIR="$WORK_DIR/$EVENT"
    local SOURCE_RAG=$(echo $ORIGINATING_EVENT | tr "/" "_")
    local PAYLOAD_FILE="$DIR/$SOURCE_RAG.json"
    local SPLUNK_PAYLOAD_FILE="$DIR/payload-splunk.json"
    mkdir -p "$DIR"

    echo "current EVENT: $EVENT"

    echo "dumping payload from redis to $PAYLOAD_FILE"
    if ! redis-cli -h $REDIS_HOST -p $REDIS_PORT --raw hget "$EVENT" payload > $PAYLOAD_FILE ; then
        echo "ERROR: cannot get payload for $EVENT from redis" 1>&2
        redis_push_reject_event "$EVENT"
        redis_document_processing_status "$ORIGINATING_EVENT" "upload" "ERROR: cannot get payload for $EVENT from redis"
        return 1
    fi

    # remove full text from the payload file with jq
    cat $PAYLOAD_FILE | jq "del(.full_text)" > $SPLUNK_PAYLOAD_FILE

    redis_document_processing_status "$ORIGINATING_EVENT" "upload" "starting upload on $DIR"

    # upload results to Splunk
    if ! upload_results_splunk "$DIR" "$SPLUNK_PAYLOAD_FILE" "$ORIGINATING_EVENT" ; then
        echo "ERROR: cannot upload payload file $SPLUNK_PAYLOAD_FILE to splunk" 1>&2
        redis_push_recovery_event "$EVENT"
        redis_document_processing_status "$ORIGINATING_EVENT" "upload" "ERROR: cannot upload payload file $SPLUNK_PAYLOAD_FILE to splunk"
        return 2
    fi

    # upload results also to OpenWebUI RAG
    if ! upload_results_rag "$PAYLOAD_FILE" ; then
        echo "ERROR: cannot upload payload file $PAYLOAD_FILE to RAG" 1>&2
        redis_push_recovery_event "$EVENT"
        redis_document_processing_status "$ORIGINATING_EVENT" "upload" "ERROR: cannot upload payload file $PAYLOAD_FILE to RAG"
        return 3
    fi

    redis_document_processing_status "$ORIGINATING_EVENT" "upload" "completed"

    echo "event $1 succesfully processed, removing temporary directory $DIR"
    rm -rf "$DIR"

    return 0
}

# $1 = WORK_DIR
# $2 = RESULTS_FILE
# $3 = ORIGINATING_EVENT
function upload_results_splunk() {
    local WORK_DIR="$1"
    local RESULTS_FILE="$2"
    local ORIGINATING_EVENT="$3"

    local hec_host=$(echo $HOSTNAME | awk -F- '{print $1"-"$2"-"$3"-"$4;}')

cat - <<EOF > $WORK_DIR/splunk-event.json
{
    "host": "$hec_host",
    "event": $(cat "$RESULTS_FILE"),
    "source": "$ORIGINATING_EVENT"
}
EOF

    echo "uploading results to \"$SPLUNK_DOCUMENT_URL\""
    echo " using HEC token \"$SPLUNK_HEC_TOKEN\""

    # actually call splunk
    curl --fail-with-body -k \
	    -H "Authorization: Splunk ${SPLUNK_HEC_TOKEN}" \
	    -H 'Content-Type: application/json' \
        -d @"$WORK_DIR/splunk-event.json" \
        "$SPLUNK_DOCUMENT_URL"
}

# $1 = RESULTS_FILE
function upload_results_rag() {
    local RESULTS_FILE="$1"
    local MISSION_NAME=$(cat $RESULTS_FILE | jq .mission | tr -d '"')

    echo "checking for configured RAG knowledge for mission \"$MISSION_NAME\" at \"$RAG_KNOWLEDGE_URL\""
    echo " using RAG token \"$RAG_TOKEN\""

    local KNOWLEDGE_ID=$(curl -H "Authorization: Bearer $RAG_TOKEN" "$RAG_KNOWLEDGE_URL" | \
        jq ".[] | select(.name==\"$MISSION_NAME\") | .id" | \
        tr -d '"')

    if [ -z "$KNOWLEDGE_ID" ]; then
        echo "ERROR: no knowledge configured for mission \"$MISSION_NAME\"" 1>&2
        return 1
    fi


    echo "uploading document to \"$RAG_DOCUMENT_URL\""
    echo " using RAG token \"$RAG_TOKEN\""

    # actually call rag
    local FILE_ID=$(curl \
	    -H "Authorization: Bearer ${RAG_TOKEN}" \
        -X POST \
        -F "file=@$RESULTS_FILE" \
        "$RAG_DOCUMENT_URL" | \
        jq .id | tr -d '"')

    if [ -z "$FILE_ID" ]; then
        echo "ERROR: file \"$RESULTS_FILE\" not uploaded" 1>&2
        return 2
    fi


    echo "binding document \"$FILE_ID\" to knowledge \"$KNOWLEDGE_ID\""
    echo " using RAG token \"$RAG_TOKEN\""

    # make an association between the given file and the RAG knowledge
    curl -X POST "${RAG_KNOWLEDGE_URL}${KNOWLEDGE_ID}/file/add" \
        -H "Authorization: Bearer ${RAG_TOKEN}" \
        -H "Content-Type: application/json" \
        -d "{\"file_id\": \"$FILE_ID\"}"
    
    echo
    echo "upload to RAG completed"
}
