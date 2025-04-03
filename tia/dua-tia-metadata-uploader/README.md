# dua-tia-metadata-uploader

The *metadata uploader* module is in charge of upload the processed documents to both the *UAD Service* (a Splunk Enterprise instance) and the OpenWebUI interface.

- the UAD Service stores the statistics and classifications for the initiatives documents
- the OpenWebUI REST Service is leveraged to build the documents knowledge base exploited by the AI Assistant to elaborate the responses to the end-user questions

This component waits for incoming messages coming from the *upload* **redis** queue. Each message contains a well-formed json structure (described in the *metadata retrieval tool* module) with all the fields describing the incoming document (e.g. the classification label, title, authors, publication date and journal, affiliations, citations, full-text if available, and so on).

The document is hence uploaded to the UAD Service stripping the full-text, because the UAD Service is only responsible to store the statistics and classification data about the documents.

The document is also uploaded with its full-text to the OpenWebUI Service, adding an item to the knowledge base of the initiative "mission". The "mission" should be intended as a branch of the initiative; for instance the "mission" GOCE is a branch of the "Earth Explorers" initiative.

In OpenWeUI Terms, for each "mission" a separate *knowledge* is dedicated, and an *AI model* is built on top of it. The *AI model* will become expert of its particular "mission".


# Error handling and recovery

In case of an error occured during one of the uploads, the message item is saved to an additional queue (*recovery-upload*). Here the document is safely stored and can be manually reprocessed to retry the upload.


# Dependencies

- redis-cli
- curl
- jq


# Configuration

The basic configuration of this component is managed by a set of envinroment variables, which can be set to leverage various aspects of the application behaviour.

Follows a list of the environment variables with example default values.

```sh
REDIS_HOST=localhost
REDIS_PORT=6379
UPLOAD_QUEUE_NAME=upload
SPLUNK_HEC_TOKEN=abcdef
SPLUNK_DOCUMENT_URL=https://splunk-enterprise:8088/services/collector/event
RAG_TOKEN=qwerty
RAG_KNOWLEDGE_URL=http://openwebui:3000/api/v1/knowledge/
RAG_DOCUMENT_URL=http://openwebui:3000/api/v1/files/
```


# Component build & startup

The *metadata uploader* is compose by a set of shell scripts, and do not requires a compilation phase to be run.

To start the component don't forget to export the configuration environment variables, then simply type the command

```
$ cd bin && ./entrypoint.sh
```