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
# $2 = WORK_DIR
function redis_push_classify_event() {
    local EVENT="$1"
    local WORK_DIR="$2"
    local METADATA_FILE="document-metadata.json"
    local RESULTS_FILE="$(basename $EVENT).json"

    if [ ! -f "$WORK_DIR/$METADATA_FILE" ]; then
        echo "ERROR: $WORK_DIR/$METADATA_FILE file not found"  1>&2
        return 1
    fi
    if [ ! -f "$WORK_DIR/$RESULTS_FILE" ]; then
        echo "ERROR: $WORK_DIR/$RESULTS_FILE results file not found"  1>&2
        return 1
    fi

    # check if full-text is available, otherwise go directly to the uploader
    if [ "$(cat $WORK_DIR/$RESULTS_FILE | jq .doc_type | tr -d '"' )" == "academic" -a ! -z "$(cat $WORK_DIR/$RESULTS_FILE | jq .full_text | tr -d '"' )" ]; then
        echo "full text found for academic doc, document shall be classified"
        local QUEUE_NAME="$CLASSIFY_QUEUE_NAME"
        local RESULTS_KEY="results"
    else
        echo "full text not found or not academic doc, document cannot be classified"
        local QUEUE_NAME="$UPLOAD_QUEUE_NAME"
        local RESULTS_KEY="payload"
    fi
    local HASHSET_NAME="$QUEUE_NAME-$EVENT"

    # create the hashset with originating event and json results
    redis-cli -h $REDIS_HOST -p $REDIS_PORT hset "$HASHSET_NAME" "originating_event" "$EVENT"

    echo "setting \"$RESULTS_KEY\" with \"$RESULTS_FILE\" contents"
    redis-cli -h $REDIS_HOST -p $REDIS_PORT -x hset "$HASHSET_NAME" "$RESULTS_KEY" < "$WORK_DIR/$RESULTS_FILE"

    # enqueue event to the correct processing queue (classify/upload)
    echo "sending event \"$HASHSET_NAME\" to the \"$QUEUE_NAME\" queue"
    redis-cli -h $REDIS_HOST -p $REDIS_PORT --raw sadd "$QUEUE_NAME" "$HASHSET_NAME"
}

# $1 = EVENT
function redis_push_recovery_event() {
    local EVENT="$1"
    
    echo "pushing recovery event for $EVENT to $RECOVERY_QUEUE_NAME"
    redis-cli -h $REDIS_HOST -p $REDIS_PORT --raw sadd "$RECOVERY_QUEUE_NAME" "$EVENT"
}

# $1 = EVENT
function redis_push_rejected_event() {
    local EVENT="$1"

    echo "pushing reject event for $EVENT to $REJECTED_QUEUE_NAME"
    redis-cli -h $REDIS_HOST -p $REDIS_PORT --raw sadd "$REJECTED_QUEUE_NAME" "$EVENT"
}

# $1 = EVENT
# $2 = WORK_DIR
function execute_metadata_retrieval_tool() {
    local EVENT="$1"
    local WORK_DIR="$2"
    local METADATA_LOG="$WORK_DIR/metadata-retrieval-tool.log"
    local AGGREGATOR_LOG="$WORK_DIR/metadata-aggregator.log"

    echo "python3 /app/dua-tia-metadata-retrieval-tool.py --grobid-url $GROBID_URL --tika-url $TIKA_URL -o $WORK_DIR $WORK_DIR"

    redis_document_processing_status "$EVENT" "metadata" "started"

    # execute metadata retrieval tool
    #   gets full-text (if available), authors, dates, citations from external tools as GROBID and TIKA
    set -o pipefail
    if python3 /app/dua-tia-metadata-retrieval-tool.py --grobid-url $GROBID_URL --tika-url $TIKA_URL $WORK_DIR |& tee $METADATA_LOG ; then
        
        echo "xml generation completed succesfully"
        
        # execute metadata_extractor tool
        #   aggregates metadata from all possible available source in a single json file
        if ! python3 /app/src/bin/metadata.py $WORK_DIR |& tee $AGGREGATOR_LOG ; then
            set +o pipefail
            echo "ERROR: cannot aggregate metadata for work dir $WORK_DIR" 1>&2
            redis_push_recovery_event "$EVENT"
            redis_document_processing_status "$EVENT" "metadata" "ERROR: cannot aggregate metadata for work dir $WORK_DIR, metadata aggregator output is: $(cat $AGGREGATOR_LOG)"
            return 2
        fi
        set +o pipefail

        if ! redis_push_classify_event "$EVENT" "$WORK_DIR" ; then
            echo "ERROR: cannot push classify event for work dir $WORK_DIR" 1>&2
            redis_push_recovery_event "$EVENT"
            redis_document_processing_status "$EVENT" "metadata" "ERROR: cannot push classify event for work dir $WORK_DIR, metadata tool output is: $(cat $METADATA_LOG), aggregator output is: $(cat $AGGREGATOR_LOG)"
            return 2
        fi
        redis_document_processing_status "$EVENT" "metadata" "completed"
        return 0
    else
        set +o pipefail
        echo "ERROR: cannot extract metadata for event $EVENT work dir $WORK_DIR" 1>&2
        redis_push_recovery_event "$EVENT"
        redis_document_processing_status "$EVENT" "metadata" "ERROR: cannot extract metadata (wd=$WORK_DIR), metadata tool output is: $(cat $METADATA_LOG)"

        return 1
    fi
}

# $1 = EVENT
# $2 = WORK_DIR
function generate_thumbnail() {
    local EVENT="$1"
    local WORK_DIR="$2"
  
    # prepare thumbnail for the first pdf found
    local PDF_NAME=$(ls $WORK_DIR/*.pdf | head -n 1)
    local JPEG_NAME="$(basename "$PDF_NAME" .pdf)".jpeg
    echo "rendering thumbnail for '$PDF_NAME' to file '$JPEG_NAME'"

    redis_document_processing_status "$EVENT" "thumbnail" "started"

    if [ -z "$SPLUNK_TOKEN" ]; then
        local CURL_CREDENTIALS="-u $SPLUNK_CREDENTIALS"
    else
        local CURL_CREDENTIALS="-HAuthorization: Splunk $SPLUNK_TOKEN"
    fi


    if convert -quiet -density 72 -thumbnail x212 -flatten "$PDF_NAME[0]" "$WORK_DIR/$JPEG_NAME" ; then

        # create a json file to upload the thumbnail to splunk  
        INDEX_NAME=$(basename "$WORK_DIR.jpeg")

        # get a previous version of the thumbnail, if any
        echo "deleting previous thumbnail for $INDEX_NAME..."
        local IDS=$(curl -g -k -H 'Content-Type: application/json' "$CURL_CREDENTIALS" "$SPLUNK_THUMBNAIL_URL?query={\"name\":\"$INDEX_NAME\"}" | jq '.[] | ._key' | tr -d '"' )
        for ID in $IDS ; do
                # make a call to delete the old thumbnail from splunk kv
                if ! curl -k "$CURL_CREDENTIALS" -X DELETE "$SPLUNK_THUMBNAIL_URL/$ID" ; then
                #if ! curl -k -X DELETE "$SPLUNK_THUMBNAIL_URL/$ID" ; then
                        echo "ERROR: cannot delete old thumbnail $INDEX_NAME with ID $ID from $SPLUNK_THUMBNAIL_URL" 1>&2
                else
                        echo "thumbnail deleted"
                fi
        done

        cat - <<EOF > $WORK_DIR/thumbnail.json
{
"name": "$INDEX_NAME", 
"image": "$(base64 -w 0 "$WORK_DIR/$JPEG_NAME")"
}
EOF
        echo "uploading thumbnail to splunk index $SPLUNK_THUMBNAIL_URL with name $INDEX_NAME"
        redis_document_processing_status "$EVENT" "thumbnail" "uploading to $SPLUNK_THUMBNAIL_URL"

        # push the pdf thumbnail to splunk
        if ! curl -k "$CURL_CREDENTIALS" -d@"$WORK_DIR/thumbnail.json" -H 'Content-Type: application/json' $SPLUNK_THUMBNAIL_URL ; then
            echo "ERROR: cannot upload thumbnail to $SPLUNK_THUMBNAIL_URL" 1>&2
            redis_document_processing_status "$EVENT" "thumbnail" "ERROR: cannot upload thumbnail to $SPLUNK_THUMBNAIL_URL"
            return 2
        fi

        redis_document_processing_status "$EVENT" "thumbnail" "completed at"
    else
        echo "ERROR: cannot create thumbnail" 1>&2
        redis_document_processing_status "$EVENT" "thumbnail" "cannot create thumbnail"
        return 1
    fi
}

# $1 = EVENT
function process_event() {
    local EVENT="$1"
    local WORK_DIR="$DATA_PATH/$EVENT"

    redis_document_processing_status "$EVENT" "metadata_event" "started"
    redis_document_processing_status "$EVENT" "metadata" "<init>"
    redis_document_processing_status "$EVENT" "thumbnail" "<init>"

    if (( $(ls $WORK_DIR/*.pdf 2>/dev/null | wc -l) == 0 )); then
        echo "ERROR: no PDF files found in $WORK_DIR" 1>&2
        redis_push_rejected_event "$EVENT"
        redis_document_processing_status "$EVENT" "metadata_event" "ERROR: no PDF files found in $WORK_DIR"
        return 1
    fi

    local FILE=$(ls $WORK_DIR/*.pdf)
    if [[ ! $(file "$FILE") =~ PDF ]]; then
        echo "ERROR: file $FILE is not a pdf file" 1>&2
        redis_push_rejected_event "$EVENT"
        redis_document_processing_status "$EVENT" "metadata_event" "ERROR: file $FILE is not a pdf file"
        return 2
    fi

    if execute_metadata_retrieval_tool "$EVENT" "$WORK_DIR" ; then
        if generate_thumbnail "$EVENT" "$WORK_DIR" ; then
            echo "thumbnail correctly generated and uploaded"
            redis_document_processing_status "$EVENT" "metadata_event" "completed"
            return 0
        else
            echo "ERROR: cannot generate or upload thumbnail" 1>&2
            redis_document_processing_status "$EVENT" "metadata_event" "completed (thumbnail is missing)"
            return 4
        fi
    else
        echo "ERROR: processing event '$EVENT' on work dir '$WORK_DIR'" 1>&2
        redis_document_processing_status "$EVENT" "metadata_event" "ERROR on execute_metadata_retrieval_tool"
        return 3
    fi
}