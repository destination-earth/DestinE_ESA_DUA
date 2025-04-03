# dua-tia-document-retriever

The Document Retriever plays a crucial role in handling document processing within the TIA Service. It actively monitors the *download* queue, retrieving messages that signal the availability of new documents. Once a document is identified, the retriever downloads its PDF file and securely stores it in its original format within a designated staging area.

Upon successful completion of the download, the Document Retriever ensures seamless integration with subsequent processing stages by pushing a new event to the *metadata* queue. This message is directed to the relevant *metadata retrieval* component instance, enabling further metadata extraction and analysis using the *staging area* as a common processing area for the document manipulation.

Several drivers can be implemented to download the PDF files, using an ad-hoc logic for the repository where they can be downloaded, if needed.

A rich set of additional metadata is downloaded, if available, from external services like *unpaywall*, *cross-ref* and *open-citations*.

Furthermore, if the PDF download link is broken, even additional locations provided by the *unpaywall* service are used.


# Configuration

Configuration properties can leverage some of the key behaviour of the application

- the *redis* connection
- the *staging area*, a local directory where the application can create subdirectories with a well-known structure based on the *repository search binding* belonging to the document, and where the document PDFs are located after the download
- the possibility to enable the *unpaywall* service as a fallback for the broken donwnload urls
- additional metadata enrichment with *unpaywall*, *cross-ref* and *opencitations*

```properties
dua.tia.documentretriever.redis.host=localhost
dua.tia.documentretriever.redis.port=6379

dua.tia.documentretriever.download.path=/var/tmp/dua-tia-document-retriever
dua.tia.documentretriever.download.max-retry=10

dua.tia.documentretriever.metadata-queue-name=metadata-dev

dua.tia.documentretriever.unpaywall-fallback-enabled=true
dua.tia.documentretriever.unpaywall-metadata-enabled=true
dua.tia.documentretriever.unpaywall-url=https://api.unpaywall.org/v2/
dua.tia.documentretriever.unpaywall-email=hooray-unpaywal@example.com

dua.tia.documentretriever.cross-ref-metadata-enabled=true
dua.tia.documentretriever.cross-ref-url=https://api.crossref.org/works/

dua.tia.documentretriever.open-citations-metadata-enabled=true
dua.tia.documentretriever.open-citations-url=https://opencitations.net/index/api/v2/citations/
```

Other relevant configurations can be changed using their corresponding Spring Boot default properties.


# Component build & startup 

```bash

$ mvn -DskipTests=true clean compile package

$ java -jar target/dua-tia-document-retriever-0.0.2.jar

```
