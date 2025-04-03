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

# $1 = EVENT
# #2 = PAYLOAD_FILE
function redis_send_upload_event() {
    local EVENT="$1"
    local UPLOAD_HASHSET="upload-$EVENT"
    local PAYLOAD_FILE="$2"

    # create a hashset with document metadata and fulltext
    redis-cli -h $REDIS_HOST -p $REDIS_PORT hset "$UPLOAD_HASHSET" "originating_event" "$EVENT"
    redis-cli -h $REDIS_HOST -p $REDIS_PORT -x hset "$UPLOAD_HASHSET" "payload" < "$PAYLOAD_FILE"

    # add to the upload queue
    echo "sending upload event ($UPLOAD_HASHSET)"
    redis-cli -h $REDIS_HOST -p $REDIS_PORT --raw sadd "$UPLOAD_QUEUE_NAME" "$UPLOAD_HASHSET"
}

# $1 = EVENT
function process_event() {
    local EVENT="$1"
    local ORIGINATING_EVENT=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT --raw hget "$EVENT" originating_event || echo "")
    if [ -z "$ORIGINATING_EVENT" ]  ; then
        echo "ERROR: cannot get originating_event for $EVENT from redis" 1>&2
        redis_push_reject_event "$EVENT"
        return 1
    fi

    # prepare work directory
    local DIR="$WORK_DIR/$EVENT"
    local EVENT_ID=$(basename $EVENT)
    local RESULTS_FILE="$DIR/$EVENT_ID.json"
    local RESULTS_KEY="results"
    mkdir -p "$DIR"

    echo "current EVENT: $EVENT"

    echo "dumping $RESULTS_KEY from redis to $RESULTS_FILE"

    if ! redis-cli -h $REDIS_HOST -p $REDIS_PORT --raw hget "$EVENT" $RESULTS_KEY > $RESULTS_FILE ; then
        echo "ERROR: cannot get $RESULTS_KEY for $EVENT from redis" 1>&2
        redis_push_reject_event "$EVENT"
        return 1
    fi

    redis_document_processing_status "$ORIGINATING_EVENT" "inference" "starting inference on $DIR"

    #
    # actual inference execution
    #
    if ! execute_inference "$DIR" ; then
        echo "ERROR: cannot execute inference on working directory $DIR" 1>&2
        redis_push_recovery_event "$EVENT"
        redis_document_processing_status "$ORIGINATING_EVENT" "inference" "ERROR: cannot execute inference on working directory $DIR"
        return 3
    fi

    if [ ! -f "$RESULTS_FILE" ]; then
        echo "ERROR: results file $RESULTS_FILE not found" 1>&2
        redis_push_recovery_event "$EVENT"
        redis_document_processing_status "$ORIGINATING_EVENT" "inference" "ERROR: results file $RESULTS_FILE not found"
        return 4
    fi

    if [ -z "$(cat $RESULTS_FILE | jq .classification[].label)" ]; then
        echo "ERROR: missing classification contents in $RESULTS_FILE" 1>&2
        redis_push_recovery_event "$EVENT"
        redis_document_processing_status "$ORIGINATING_EVENT" "inference" "ERROR: missing classification contents in $RESULTS_FILE"
        return 5
    fi
    
    if ! redis_send_upload_event "$ORIGINATING_EVENT" "$RESULTS_FILE" ; then
        echo "ERROR: cannot send upload event" 1>&2
        redis_push_recovery_event "$EVENT"
        redis_document_processing_status "$ORIGINATING_EVENT" "inference" "ERROR: cannot send upload event"
        return 6
    fi

    redis_document_processing_status "$ORIGINATING_EVENT" "inference" "completed"

    echo "event $1 succesfully processed, removing temporary directory $DIR"
    rm -rf "$DIR"

    return 0
}

# $1 = WORK_DIR
function execute_inference() {
    local WORK_DIR="$1"

    echo "executing ML inference ($WORK_DIR)"

    # execute inference
    pushd /app/src/
    python3 bin/inference.py \
        $WORK_DIR/*.json \
        -c /app/src/config/conf.yml 
    popd

    # the ml-model inference will output a json file with a well-known name
    # ready to be uploaded to splunk

    return 0
}
