# dua-tia-scheduler

This module's reponsability is to trigger repositories harvesting events based on the following TIA configuration values

- enabled repositories list
- individual repository polling interval

The *configuration manager* service is periodically called to gain this information and evaluate the event triggering.
When the harvesting event shall be triggered for a certain repository, the repository information are pushed to a **redis** queue to be later processed by the *repository harvester* component.


# Configuration

The main configuration properties are summarized below.

```properties
dua.tia.scheduler.polling-period-sec=60

dua.tia.configuration-manager.base-url=http://dua-tia-configuration-manager:8080

dua.tia.scheduler.redis.host=localhost
dua.tia.scheduler.redis.port=6379
```

Other relevant configurations can be changed using their corresponding Spring Boot default properties.

# Component build & startup 

```bash

$ mvn -DskipTests=true clean compile package

$ java -jar target/dua-tia-scheduler-0.0.2.jar

```