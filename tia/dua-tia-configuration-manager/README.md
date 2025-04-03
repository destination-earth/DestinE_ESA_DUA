# dua-tia-configuration-manager

Rest-service for the TIA Service configuration management

One of the main tasks of the TIA Service is to drive the document discovery on several pre-configured document repositories to download fresh published documents for various initiatives. 
This module implements a Spring Boot REST Api server to configure the TIA module, providing endpoints to configure the following entities:

- initiative: the main scope of the documents to be downloaded (e.g. Earth Explorers missions)
- document type: a category for groups of downloaded documents (e.g. Academic, Policy)
- document repository: the configuration of the external repository to be inspected, URL, polling time, etc.
- repository credentials: api-keys, if required by the repo
- keyword dictionary: a multi-level keyword dictionary to build a combination of nested logical expressions to search publications from the repositories
- repository search bindings: links between initiative, document type, keyword dictionary and repositories

Individual endpoints are provided to configure each configuration entity. An additional *bulk-configuration* endpoint allows the system administrator to upload the entire configuration at once.

Click the following link to see the [apidoc documentation details](api-docs.json)



# Sample TIA configuration

Follows an example configuration kit that can be used as an initial setup of the TIA module. The *bulk-configuration* endpoint can be used to upload this configuration.

```json
{
    "initiatives": [
        {
            "id": "earth-explorers",
            "name": "Earth Explorers",
            "shortName": "ee"
        }
    ],
    "documentTypes": [
        {
            "id": "policy",
            "name": "Policy",
            "shortName": "pol"
        },
        {
            "id": "academic",
            "name": "Academic",
            "shortName": "ac"
        }
    ],
    "documentRepositories": [
        {
            "id": "jrc",
            "name": "JRC publications repository",
            "url": "https://publications.jrc.ec.europa.eu/repository/data/search/query",
            "driver": "jrc",
            "frequency": 3600,
            "enabled": true
        },
        {
            "id": "scopus",
            "name": "Scopus",
            "url": "https://api.elsevier.com/content/",
            "driver": "scopus",
            "frequency": 3600,
            "enabled": true
        },
        {
            "id": "open_alex",
            "name": "OpenAlex",
            "url": "https://api.openalex.org/works",
            "driver": "open_alex",
            "frequency": 3600,
            "enabled": true
        }
    ],
    "keywordDictionaries": [
        {
            "id": "ee-en-1.1",
            "name": "EE English v1.1",
            "shortName": "eeen11",
            "dictionaryDefinition": {
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
                    },
                    {
                        "keywords": [
                            "SMOS"
                        ],
                        "children": [
                            {
                                "keywords": [
                                    "soil moisture"
                                ],
                                "children": [
                                    "Water cycle",
                                    "Numerical weather prediction",
                                    "flood monitoring and forecasting",
                                    "land atmosphere interaction",
                                    "drought monitoring and forecasting",
                                    "agricultural yield monitoring and forecasting"
                                ]
                            },
                            {
                                "keywords": [
                                    "salinity",
                                    "Sea Ice",
                                    "Vegetation Optical Depth",
                                    "Freeze Thaw",
                                    "Wind Speed",
                                    "Solar Flux",
                                    "radiometer",
                                    "brightness"
                                ],
                                "children": []
                            }
                        ]
                    },
                    {
                        "keywords": [
                            "CryoSat-2"
                        ],
                        "children": [
                            {
                                "keywords": [
                                    "Ice"
                                ],
                                "children": [
                                    "Thickness",
                                    "Sea",
                                    "Sea level",
                                    "Shelves",
                                    "SIRAL",
                                    "Climate change",
                                    "Oceanography",
                                    "Altimetry"
                                ]
                            }
                        ]
                    },
                    {
                        "keywords": [
                            "ESA Swarm"
                        ],
                        "children": [
                            "magnetic field",
                            "Ionosphere",
                            "Thermosphere",
                            "Magnetosphere"
                        ]
                    },
                    {
                        "keywords": [
                            "Aeolus"
                        ],
                        "children": [
                            {
                                "keywords": [
                                    "mission",
                                    "satellite"
                                ],
                                "children": [
                                    "aerosol",
                                    "cloud",
                                    "doppler lidar",
                                    "weather forecasting",
                                    "wind",
                                    "wind measurement",
                                    "wind observations",
                                    "wind profile",
                                    "wind speed"
                                ]
                            }
                        ]
                    }
                ]
            }
        }
    ],
    "repositorySearchBindingReferences": [],
    "repositorySearchBindings": [
        {
            "initiativeId": "earth-explorers",
            "documentTypeId": "academic",
            "keywordDictionaryId": "ee-en-1.1",
            "documentRepositoryIds": [
                "scopus",
                "open_alex"
            ]
        },
        {
            "initiativeId": "earth-explorers",
            "documentTypeId": "policy",
            "keywordDictionaryId": "ee-en-1.1",
            "documentRepositoryIds": [
                "jrc"
            ]
        }
    ]
}
```

In this example, the *Scopus* repository is used but it's not open-access. The access on this repository is limited and an api-key is required. The TIA Service supports credentials handling by the "Authorization: Bearer" standard mechanism.

It is possible to configure a per-repository api-key using the *credentials* endpoint of the configuration manager REST Api. The credential id shall match the repository *id* specified during the repository setup applied using the repositories configuration endpoints.

```json
{
    "id": "scopus",
    "value": "<api-key>"
}
```

# Configuration parameters

This module interacts with a **redis** server for the information persistence. Access to the redis instance can be leveraged with the following properties

```
dua.tia.configuration-manager.redis.host=localhost
dua.tia.configuration-manager.redis.port=6379
```

Other relevant configurations can be changed using their corresponding Spring Boot default properties.

# Component build & startup 

```bash

$ mvn -DskipTests=true clean compile package

$ java -jar target/dua-tia-configuration-manager-0.0.1.jar

```
