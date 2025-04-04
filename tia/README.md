# TIA Service

In the *DUA* project, the TIA Service is responsible of harvesting new *academic* and *policy* documents from the configured journal repositories, download their metadata and full-text PDFs (where available), evaluate the *Initiative* impact on these documents through an NLP-ML model, and archive those information on a data storage shared between the other *DUA* services (e.g. UAD).

The subject *Initiatives* can be easily and dynamically configured (see the [configuration manager documentation](dua-tia-configuration-manager/README.md)). More than one *Initiatives* can exists at a time, several document repositories can be configured for each of them and different keyword dictionaries can be applied during the harvesting process.

This *Git* module describes the overall TIA Service behaviour giving the system overview and the logic behind the integration between its sub-components.


## Components interactions diagram

Follows the components interaction diagram which describe the flow of information between the various components of the *TIA* service.

Within the diagram, the *core* components are highlighted in red.

The following list reports each core components' git repository:

- configuration manager: **dua-tia-configuration-manager**
- scheduler: **dua-tia-scheduler**
- repository harvester: **dua-tia-repository-harvester**
- document retriever: **dua-tia-document-retriever**
- metadata retrieval tool: **dua-tia-metadata-retrieval-tool**
- ML Model (NLP): **dua-tia-ml-model**
- document metadata uploader: **dua-tia-metadata-uploader**

```mermaid
flowchart

%% actors

    meta_db[(
        metadata db
    )]

    conf_db[("`
        configuration db
        (also redis)
    `")]

    redis_db[(
        redis db
    )]

    sched["`
        scheduler
        (formerly known as 
        *orchestrator*)
    `"]:::hilight

    rep_harv["`
        repository harvester
    `"]:::hilight

    doc_retr["`
        document retriever
        (academic/policy docs)
    `"]:::hilight

    additional_docs["`
        additional technical docs
        (ESA)
    `"]:::hilight

    meta_retr["`
        metadata retrieval tool
        this component performs 
        a call to *GROBID* 
        and *TIKA* via web 
        services
    `"]:::hilight

    doc_serv["`
        document service
        *(Splunk HEC)*
    `"]

    docdownstat_serv["`
        download status service
        *redis*
    `"]

    conf_man["`
        configuration
        manager
    `"]:::hilight

    stg_area[("`staging area
        (academic/policy/esa)`")]

    nlp_clas["`
        ML Model (NLP)
        the component in charge of
        the document classification
    `"]:::hilight

    doc_meta_uploader["document metadata uploader"]:::hilight

    pub_repo[(
        public academic/policy
        repository
    )]

    doi_repo[("`
        public document 
        repository *(resolved by doi)*
    `")]

    rag["`RAG openwebui`"]

    grobid["`grobid`"]

    tika["`tika`"]


%% topics
    topic_repo((repo))
    topic_docs((docs))
    topic_clas((classify))
    topic_meta(("`metadata-*instance*`"))
    topic_meta_store(("`upload`"))
    

%% interactions    

    sched -->|"`generates one message
        for each repo with required
        access credentials, at 
        regular time intervals`"| topic_repo
    
    sched -->|"`reads repositories configuration`" | conf_man

    topic_repo -->|"`picks a message
        from the topic`"| rep_harv

    rep_harv <--->|"`query last 
        published docs`"| pub_repo

    rep_harv -->|"`check if each result doc is 
        already downloaded
        (in this case don't send message to queue)`"| docdownstat_serv

    rep_harv -->|"`generates one message 
        for each new doc found,
        all the available metadata
        are sent to the topic`"| topic_docs

    rep_harv <-->|"`for the current
        repository event, get the
        list of search bindings
        (initiative/doctype/kw dictionary)`"| conf_man

    topic_docs -->|"`each document retriever
        picks a message`"|doc_retr

    doc_retr -->|"`save the document in
        its original format`"| stg_area

    doc_retr -.->|"`sidecar containers,
        access to the same
        volume is granted`"| meta_retr

    doc_retr <-->|"`download full
        text document`"| doi_repo
    
    doc_retr -->|"`on download completion
        puts a message into a 
        dedicated topic for each 
        service instance`"| topic_meta

    doc_retr -->|"`check if current doc is already
        downloaded (skip download)`"| docdownstat_serv
    
    additional_docs -->|"`
        tech docs by ESA
    `"|stg_area

    additional_docs --> topic_meta

    topic_meta -->|"`metadata retrieving
        is triggered by a message 
        visible only to the current 
        service instance`"| meta_retr
    
    stg_area <-->|"`document parsing`"| meta_retr

    meta_retr -->|"`send document metadata 
        (if doc does not require
        classification)`"| topic_meta_store

    meta_retr --> |"`enqueue 
        classification task
        (academic and policy docs,
        payload contains
        the doc full text)`"| topic_clas
    
    meta_retr <--> grobid

    meta_retr <--> tika

    topic_clas --> nlp_clas

    nlp_clas -->|"`send document
        metadata and classification`"| topic_meta_store
    
    topic_meta_store --> doc_meta_uploader
    
    doc_meta_uploader -->|"`send metadata to RAG 
        including document full-text`"| rag

    doc_meta_uploader -->|"`actual document
        metadata storage (with classification,
        if available)`"| doc_serv

    doc_serv <==> meta_db
    conf_man <==> conf_db
    docdownstat_serv <==> redis_db

%% blocks

    subgraph topics
        topic_repo
        topic_docs
        topic_clas
        topic_meta
        topic_meta_store
    end

    subgraph data management

        conf_man
        conf_db

        doc_serv
        meta_db

        docdownstat_serv
        redis_db

    end

    subgraph cots services
        grobid
        tika
    end

    subgraph "external services (internet)"
        pub_repo
        doi_repo
    end

    classDef hilight stroke:#f00

```

## Diagnostics and error handling

In case of an information processing error, the originating event of a certain queue is stored "as-is" in a corresponding queue named "recovery-*queue name*". In this way it is always possible to reprocess every event simply by putting it back to the originating queue.

During the metadata extraction, the classification and the metadata uploading, a special "doc-processing-*doc-id*" hash-set tracks the status of each individual step of the document processing.

```mermaid
flowchart

%% actors

    meta_retr["metadata retrieval tool"]:::hilight

    nlp_clas["ML Model (NLP)"]:::hilight

    doc_meta_uploader["document metadata uploader"]:::hilight

%% topics
    topic_recov_meta(("`recovery-metadata-*instance*`"))
    topic_doc_proc(("`doc-processing-*doc-id*`"))
    topic_recov_clas((recovery-classify))
    topic_recov_upload((recovery-upload))

%% interactions

    meta_retr --> topic_recov_meta

    meta_retr --> topic_doc_proc

    nlp_clas --> topic_recov_clas
    
    nlp_clas --> topic_doc_proc
    
    doc_meta_uploader --> topic_doc_proc
    doc_meta_uploader --> topic_recov_upload

%% blocks

    subgraph topics
        topic_recov_meta
        topic_recov_clas
        topic_doc_proc
        topic_recov_upload
    end


    classDef hilight stroke:#f00

```
## Software Design

The software system architecture involves multiple components that work together to retrieve, manage, and classify documents from public repositories. Here is a breakdown of the components and their roles:

### **1. Scheduler**
   - **Role**: generates messages for each repository that needs to be accessed. It sends these messages to a queue management system at regular intervals.
   - **Inputs**: configuration settings from the *configuration manager* component, specifically the repositories to be contacted and their polling periods.
   - **Output**: messages sent to the queue management system's topic related to repositories.

### **2. Queue management system**
   - **Role**: handles messages in different topics. This system distributes tasks among various components, and specifically it is composed by the **redis** No-Sql DBMS.
   - **Topics**:
     - **repo**: messages generated by the scheduler for each repository.
     - **docs**: messages picked up by document retrievers to process document retrieval tasks.
     - **metadata-{instance}**: dedicated for each service instance, triggered by the completion of document downloads.
     - **classify**: tasks are enqueued here to classify documents using an NLP model.
     - **upload**: documents that shall be uploaded to the target components are added to this queue.

### **3. Repository harvester**
   - **Role**: queries public document repositories and retrieves metadata about available documents.
   - **Inputs**:
     - **Public Academic/Policy Repository**: queries the last published documents.
     - **Public Document Repository**: its the repository which contain the real PDF document of the publication; it's the target of the URL resolved by the document DOI.
   - **Output**: generates messages for each new document found, sending all available metadata to the *repo* topic in the queue management system.

### **4. Document retriever**
   - **Role**: picks up messages from the *docs* topic and is responsible for downloading the full text of documents.
   - **Processes**:
     - downloads and saves documents in their original format in a staging area.
     - upon completion, it puts a message into the *metadata-instance* topic for the specific service instance.

### **5. Staging area**
   - **Role**: temporary storage for documents in their original format after they have been retrieved.

### **6. Metadata retrieval tool**
   - **Role**: retrieves additional metadata by making calls to external services like GROBID/Tika (and others) via web services.
   - **Process**:
     - triggers metadata retrieval tasks upon receiving a message.
     - aggregage the various metadata obtained from the document.
     - propagate messages to the *ml model* or *metadata uploader*.

### **7. ML model (NLP)**
   - **Role**: performs document classification using Natural Language Processing (NLP).
   - **Process**:
     - it is triggered by tasks in the *classify* topic.
     - updates the document classification metadata.
     - propagate the document metadata with classification to the *upload* queue.

### **8. Configuration manager**
   - **Role**: stores configuration data about *Initiatives*, *repositories*, *document types*, *keyword dictionaries* used by the various TIA components and it's leveraged by a set of REST APIs.

### **9. Databases**
   - **Redis Database (redis db)**: stores the configurations handled by the *configuration manager*, the processing queues and the status of document downloads.
   - **Splunk Metadata Database (metadata db)**: stores metadata retrieved from documents, including classification results.

### **Flow Summary**:

1. the *scheduler* triggers the process by sending repository information to the *repo* queue, using the repositories configuration and polling times from the *configuration manager*.
2. the *repository harvester* queries public repositories and generates metadata for new documents, using the initiatives and keyword dictionaries configured in the *configuration manager*, and send the documents data to the *docs* queue.
3. the *document retriever* downloads these documents, storing them in the staging area, and informs the *metadata retrieval tool* which performs full text and metadata extraction, additional metadata enrichment and aggregation; proper messages are dispatched to the *upload* or *classify* queues.
4. metadata is then classified using the *ml model*. 
5. the *metadata uploader* uploads the document metadata to the *UAD Service* and *OpenWebUI* knowledge.

# Modules

- [Configuration Manager](dua-tia-configuration-manager/README.md)
- [Scheduler](dua-tia-scheduler/README.md)
- [Repository Harvester](dua-tia-repository-harvester/README.md)
- [Document Retriever](dua-tia-document-retriever/README.md)
- [Metadata Retrieval Tool](dua-tia-metadata-retrieval-tool/README.md)
- [ML Model](dua-tia-ml-model/README.md)
- [Metadata Uploader](dua-tia-metadata-uploader/README.md)
