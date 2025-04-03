# dua-tia-repository-harvester

This module is responsible to explore the various configured document repositories and schedule the download of the latest published documents. It interfaces with **redis** to read the repository harvesting events queue, and with the *configuration manager* REST service to get details about the inspected repositories. 
For each *repository harvesting* event, the list of the related *repository search binding* (links between initiatives, document types repositories and keyword dictionaries) is retrieved.
Each iteration works on its own keyword dictionary, reorganizing its structure to obtain a search query to perform on the document repository using its own query syntax.

On this purpose, the software is designed to handle different implementation of query drivers, one for each repository that should be queried. These drivers should also handle the repositories query and quota limits (requests per hours), if needed.

The query is built with the following criteria:

1. should handle the last publication date used for the repository
2. shall implement the results pagination
3. shall implement quota limits 
4. must reorganize the dictionary keywords placing them on different logical conditions using each dictionary level terms in OR and different levels in AND

as a clarification example of the point 4., the following keyword dictionary snippet

```json
{
    "keywords": [
        "Earth Explorer"
    ],
    "skipKeywords": true,
    "children": [
        {
            "keywords": [
                "GOCE"
            ],
            "children": [
                {
                    "keywords": [
                        "Earth's gravity",
                        "gravity"
                    ],
                    "children": [
                        "ocean circulation",
                        "sea level",
                        "ice dynamics"
                    ]
                }
            ]
        }
    ]
}
```

will be rendered in query pseudo-code as

```
"GOCE" AND ("Earth's gravity" OR "gravity") AND ("ocean circulation" OR "sea level" OR "ice dynamics")
```

This strategy gives a powerful and flexible tool to the System Administrator to configure the harvesting process.

For each published document found, a *download* event is triggered and pushed to a download queue handled by **redis**.


# Configuration

The main configuration options are the following

```properties
dua.tia.configuration-manager.base-url=http://localhost:8080

dua.tia.repositoryharvester.full-text-max-size=1000000
dua.tia.repositoryharvester.json-response-max-size=50000000

dua.tia.repositoryharvester.redis.host=localhost
dua.tia.repositoryharvester.redis.port=6379
```

Other relevant configurations can be changed using their corresponding Spring Boot default properties.


# Component build & startup 

```bash

$ mvn -DskipTests=true clean compile package

$ java -jar target/dua-tia-repository-harvester-0.0.2.jar

```
