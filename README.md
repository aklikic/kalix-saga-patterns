# Kalix Workshop - Cinama Show Booking - Java
Not supported by Lightbend in any conceivable way, not open for contributions.
## Prerequisite
Java 17 or later<br>
Apache Maven 3.6 or higher<br>
Docker 20.10.14 or higher (client and daemon)<br>
cURL<br>
IDE / editor<br>

## Create kickstart maven project

```
mvn archetype:generate \
  -DarchetypeGroupId=io.kalix \
  -DarchetypeArtifactId=kalix-spring-boot-archetype \
  -DarchetypeVersion=1.3.4
```
Define value for property 'groupId': `com.example`<br>
Define value for property 'artifactId': `cinema-booking-java`<br>
Define value for property 'version' 1.0-SNAPSHOT: :<br>
Define value for property 'package' com.example: : `com.example`<br>

## Import generated project in your IDE/editor

## Update main class
1. Move `io.kx.Main` to `io.kx` package
2. Change default annotation for `ACL` to: `@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))`
3. In `pom.xml` in `<mainClass>io.kx.loan.Main</mainClass>` replace `io.kx.loanapp.Main` with `io.kx.Main`

# Loan application service

## Define persistence (domain)`
1. Create interface `io.kx.loanapp.LoanAppDomain`
2. Create enum `LoanAppDomainStatus` in interface `io.kx.loanapp.LoanAppDomain`
3. Create Java Record `LoanAppDomainState` in interface `io.kx.loanapp.LoanAppDomain` and add parameters
4. Create Java Interface `LoanAppDomainEvent` interface `io.kx.loanapp.LoanAppDomain` and add Java records for events `Submitted`, `Approved`, `Declined` and Jackson annotations for polymorph serialization
5. In `LoanAppDomainState` Java Record implement `empty`, `onSubmitted`, `onApproved` and `onDeclined` methods

<i><b>Tip</b></i>: Check content in `loan-app-step-1` git branch

## Define API data structure and endpoints
1. Create Java Interface `LoanAppApi` and add Java Records for requests and responses
3. Create class `LoanAppEntity` extending `EventSourcedEntity<LoanAppDomain.LoanAppDomainState, LoanAppDomain.LoanAppDomainEvent>`
   1. add class level annotations (event sourcing entity configuration):
   ```
   @Id("loanAppId")
   @TypeId("loanapp")
   @RequestMapping("/loanapp/{loanAppId}")
   ```
   2. add class level annotations (path prefix):
   ```
   @RequestMapping("/loanapp/{loanAppId}")
   ```
   3. Override `emptyState` and return `LoanAppDomain.LoanAppDomainState.empty()`, set loanAppId via `EventSourcedEntityContext` injected through the constructor
   4. Implement each request method and event handlers and annotate with `@EventHandler`

<i><b>Tip</b></i>: Check content in `loan-app-step-1` git branch


## Implement unit test
1. Create  `src/test/java` <br>
2. Create  `io.kx.loanapp.LoanAppEntityTest` class<br>
3. Implement `happyPath`
   <i><b>Tip</b></i>: Check content in `loan-app-step-1` git branch

## Run unit test
```
mvn test
```
## Implement integration test
1. Edit `io.kx.loanapp.IntegrationTest` class<br>
3. Implement `happyPath`
   <i><b>Tip</b></i>: Check content in `loan-app-step-1` git branch

## Run integration test
```
mvn -Pit verify
```

<i><b>Note</b></i>: Integration tests uses [TestContainers](https://www.testcontainers.org/) to span integration environment so it could require some time to download required containers.
Also make sure docker is running.

## Run locally
Start the service and kalix proxy:

```
mvn kalix:runAll
```

## Test service locally
Submit loan application:
```
curl -XPOST -d '{
  "clientId": "12345",
  "clientMonthlyIncomeCents": 60000,
  "loanAmountCents": 20000,
  "loanDurationMonths": 12
}' http://localhost:9000/loanapp/1/submit -H "Content-Type: application/json"
```

Get loan application:
```
curl -XGET http://localhost:9000/loanapp/1 -H "Content-Type: application/json"
```

Approve:
```
curl -XPOST http://localhost:9000/loanapp/1/approve -H "Content-Type: application/json"
```

### Deploy
1. Install Kalix CLI
   https://docs.kalix.io/setting-up/index.html#_1_install_the_kalix_cli
2. Kalix CLI
   1. Register (FREE)
    ```
    kalix auth signup
    ```
   **Note**: Following command will open a browser where registration information can be filled in<br>
   2. Login
    ```
    kalix auth login
    ```
   **Note**: Following command will open a browser where authentication approval needs to be provided<br>

   3. Create a project
    ```
    kalix projects new loan-application-java --region=gcp-us-east1
    ```
   **Note**: `gcp-is-east1` is currently the only available region for deploying trial projects. For non-trial projects you can select Cloud Provider and regions of your choice<br>

   4. Authenticate local docker for pushing docker image to `Kalix Container Registry (KCR)`
    ```
    kalix auth container-registry configure
    ```
   **Note**: The command will output `Kalix Container Registry (KCR)` path that will be used to configure `dockerImage` in `pom.xml`<br>
   5. Extract Kalix user `username`
   ```
   kalix auth current-login
   ```
   **Note**: The command will output Kalix user details and column `USERNAME` will be used to configure `dockerImage` in `pom.xml`<br>
3. Configure `dockerImage` path in `pom.xml`
   Replace `my-docker-repo` in `dockerImage` in `pom.xml` with: <br>
   `Kalix Container Registry (KCR)` path + `/` + `USERNAME` + `/loan-application-java`<br>
   **Example** where `Kalix Container Registry (KCR)` path is `kcr.us-east-1.kalix.io` and `USERNAME` is `myuser`:<br>
```
<dockerImage>kcr.us-east-1.kalix.io/myuser/loan-application-java/${project.artifactId}</dockerImage>
```
4. Deploy service in Kalix project:
 ```
mvn deploy kalix:deploy
 ```
This command will:
- compile the code
- execute tests
- package into a docker image
- push the docker image to Kalix docker registry
- trigger service deployment by invoking Kalix CLI
5. Check deployment:
```
kalix service list
```
Result:
```
kalix service list                                                                         
NAME                                         AGE    REPLICAS   STATUS        IMAGE TAG                     
loan-application-java                        50s    0          Ready         1.0-SNAPSHOT                  
```
**Note**: When deploying service for the first time it can take up to 1 minute for internal provisioning

## Test service in production
Proxy connection to Kalix service via Kalix CLI
```
kalix service proxy loan-application-java
```
Proxy Kalix CLI command will expose service proxy connection on `localhost:8080` <br>

Submit loan application:
```
curl -XPOST -d '{
  "clientId": "12345",
  "clientMonthlyIncomeCents": 60000,
  "loanAmountCents": 20000,
  "loanDurationMonths": 12
}' http://localhost:8080/loanapp/1/submit -H "Content-Type: application/json"
```
Get loan application:
```
curl -XGET http://localhost:8080/loanapp/1 -H "Content-Type: application/json"
```
Approve:
```
curl -XPOST http://localhost:8080/loanapp/1/approve -H "Content-Type: application/json"
```

# Loan application processing service
## Create loan application processing packages
Create package `io.kx.loanproc` in `main` and `test` <br>
## Define persistence (domain)`
1. Create interface `io.kx.loanproc.LoanProcDomain`
2. Create enum `LoanProcDomainStatus` in interface `io.kx.loanproc.LoanProcDomain`
3. Create Java Record `LoanProcDomainState` in interface `io.kx.loanproc.LoanProcDomain` and add parameters
4. Create Java Interface `LoanProcDomainEvent` interface `io.kx.loanproc.LoanProcDomain` and add Java records for events `ReadyForReview`, `Approved`, `Declined` and `TypeName` annotations for not using class names
5. In `LoanAppDomainState` Java Record implement `empty`, `onReadyForReview`, `onApproved` and `onDeclined` methods

<i><b>Tip</b></i>: Check content in `loan-proc-step-2` git branch

## Define API data structure and endpoints
1. Create Java Interface `LoanProcApi` and add Java Records for requests and responses
3. Create class `LoanProcEntity` extending `EventSourcedEntity<LoanProcDomain.LoanProcDomainState, LoanProcDomain.LoanProcDomainEvent>`
   1. add class level annotations (event sourcing entity configuration):
   ```
   @Id("loanAppId")
   @TypeId("loanproc")
   @RequestMapping("/loanproc/{loanAppId}")
   ```
   2. add class level annotations (path prefix):
   ```
   @RequestMapping("/loanproc/{loanAppId}")
   ```
   3. Override `emptyState` and return `LoanProcDomain.LoanProcDomainState.empty()`, set loanAppId via `EventSourcedEntityContext` injected through the constructor
   4. Implement each request method and event handlers and annotate with `@EventHandler`

<i><b>Tip</b></i>: Check content in `loan-proc-step-2` git branch


## Implement unit test
1. Create  package `io.kx.loanproc` in `src/test/java` <br>
2. Create  `io.kx.loanproc.LoanProcEntityTest` class<br>
3. Implement `happyPath`
   <i><b>Tip</b></i>: Check content in `loan-proc-step-2` git branch

## Run unit test
```
mvn test
```
## Implement integration test
1. Edit `io.kx.loanproc.IntegrationTest` class<br>
3. Implement `happyPath`
   <i><b>Tip</b></i>: Check content in `loan-proc-step-2` git branch

## Run integration test
```
mvn -Pit verify
```

<i><b>Note</b></i>: Integration tests uses [TestContainers](https://www.testcontainers.org/) to span integration environment so it could require some time to download required containers.
Also make sure docker is running.

## Run locally
Start the service and kalix proxy:

```
mvn kalix:runAll
```

## Test service locally
Start processing:
```
curl -XPOST http://localhost:9000/loanproc/1/process -H "Content-Type: application/json"
```

Get loan processing:
```
curl -XGET http://localhost:9000/loanproc/1 -H "Content-Type: application/json"
```
Approve:
```
curl -XPOST -d '{"reviewerId":"9999"}' http://localhost:9000/loanproc/1/approve -H "Content-Type: application/json"
```
### Re-Deploy
```
mvn deploy kalix:deploy
```
```
kalix service list
NAME                    AGE   INSTANCES   STATUS             IMAGE TAG                     
loan-application-java   32m   1           UpdateInProgress   1.0-SNAPSHOT
```
## Test service in production
Proxy connection to Kalix service via Kalix CLI
```
kalix service proxy loan-application-java
```
Proxy Kalix CLI command will expose service proxy connection on `localhost:8080` <br>

Start processing:
```
curl -XPOST http://localhost:8080/loanproc/1/process -H "Content-Type: application/json"
```

Get loan processing:
```
curl -XGET http://localhost:8080/loanproc/1 -H "Content-Type: application/json"
```
Approve:
```
curl -XPOST -d '{"reviewerId":"9999"}' http://localhost:8080/loanproc/1/approve -H "Content-Type: application/json"
```
# Views
## Create a view
1. Create package `io.kx.loanproc.view`
2. Create Java Interface `io.kx.loanproc.view.LoanProcViewModel` with Java records for `ViewRecord` and `ViewRequest`
3. Create `io.kx.loanproc.viewLoanProcByStatusView` class extending `View`
   1. Add class level annotation for table name: `@Table("loanproc_by_status")`
   2. Implement getLoanProcByStatus with `@Query` and `@PostMapping` annotations
   3. Implement event handler methods for each domain event

<i><b>Tip</b></i>: Check content in `views-step-3` git branch

##Unit test
Because of the nature of views only Integration tests are done.

## Create integration tests for view
In `io.kx.loanproc.IntegrationTest` copy `loanProcHappyPath` test to `loanProcHappyPathWithView` and add view query via `componentClient`
<i><b>Tip</b></i>: Check content in `views-step-3` git branch

## Run integration test
```
mvn -Pit verify
```
## Package & Deploy
```
mvn deploy kalix:deploy
```
## Test service in production
Proxy connection to Kalix service via Kalix CLI
```
kalix service proxy loan-application-java
```
```
curl -XPOST -d {"statusId":"STATUS_APPROVED"} http://localhost:8080/loanproc/views/by-status -H "Content-Type: application/json"
```
# Eventing - Event driven communication
## Action for submitted event (Loan application service -> Loan application processing service)
1. Create `io.kx.loanapp.LoanAppToLoanProcEventingAction` class extending `Action`
2. Add class level annotation: `@Subscribe.EventSourcedEntity(value = LoanAppEntity.class, ignoreUnknown = true)`
3. Inject `ComponentClient` via constructor
4. Implement `onSubmitted` event handler method

<i><b>Tip</b></i>: Check content in `eventing-step-4` git branch

## Action for approved & declined processing event (Loan application processing service -> Loan application service)
1. Create `io.kx.loanproc.LoanProcToLoanAppEventingAction` class extending `Action`
2. Add class level annotation: `@Subscribe.EventSourcedEntity(value = LoanProcEntity.class, ignoreUnknown = true)`
3. Inject `ComponentClient` via constructor
4. Implement `onApproved` and `onDeclined` event handler methods

<i><b>Tip</b></i>: Check content in `eventing-step-4` git branch

## Create integration tests for eventing (end-to-end test)
Update `io.kx.IntegrationTest` and add `endToEndHappyPath` and `endToEndHappyPathWithDecline` test
<i><b>Tip</b></i>: Check content in `eventing-step-4` git branch
## Run integration test
```
mvn -Pit verify
```
## Package & Deploy
```
mvn deploy kalix:deploy
```
## Test service in production
Proxy connection to Kalix service via Kalix CLI
```
kalix service proxy loan-application-java
```

Submit loan application:
```
curl -XPOST -d '{
  "clientId": "12345",
  "clientMonthlyIncomeCents": 60000,
  "loanAmountCents": 20000,
  "loanDurationMonths": 12
}' http://localhost:8080/loanapp/3/submit -H "Content-Type: application/json"
```
Check loan processing status:
```
curl -XPOST -d {"statusId":"STATUS_READY_FOR_REVIEW"} http://localhost:8080/loanproc/views/by-status -H "Content-Type: application/json"
```
Approve loan processing:
```
curl -XPOST -d '{"reviewerId":"9999"}' http://localhost:8080/loanproc/3/approve -H "Content-Type: application/json"
```
Get loan application:
```
curl -XGET http://localhost:8080/loanapp/3 -H "Content-Type: application/json"
```

# Timers
## Creating config
1. Create class `io.kx.loanproc.LoanProcConfig`
2. Add class level annotation:
```
@Configuration
@ConfigurationProperties(prefix = "loanproc")
```
3. Add parameter `Integer timeoutMillis` with getter and setter
4. In `src/main/resources/application.properties` add `loanproc.timeoutMillis = 600000`
## Action for managing timer for timeout
1. Create `io.kx.loanproc.LoanProcTimeoutAction` class extending `Action`
2. Add class level annotation `@Subscribe.EventSourcedEntity(value = LoanProcEntity.class, ignoreUnknown = true)`
3. Inject `ComponentClient kalixClient` via constructor
4. Implement `getTimerName` method
5. Implement event handler methods for `onReadyForReview`, `onApproved` and `onDeclined`

<i><b>Tip</b></i>: Check content in `timers-step-5` git branch
## Create integration tests for eventing (end-to-end test)
1. Create folder `src/it/resources`
2. Create file `src/it/resources/test-application.properties` and add `loanproc.timeoutMillis = 1000`
3. Create a new integration test class `io.kx.TimerIntegrationTest`.
   <i><b>Tip</b></i>: Check content in `timers-step-5` git branch
## Run integration test
```
mvn -Pit verify
```
## Package & Deploy
```
mvn deploy kalix:deploy
```
# Action as a controller (API gateway)
## Action
1. Create `io.kx.loanapp.LoanAppGatewayAction` class extending `Action`
2. add class level annotations (path prefix):
   ```
   @RequestMapping("/loanapp-gw")
   ```
3. add `submit` method
   <i><b>Tip</b></i>: Check content in `controller-action-step-6` git branch
## Integration test
Add new test case `endToEndHappyPathWithGw` and use `loanapp-gw` instead of `loanapp` for submitting. Set `loanAppId` from the response.<br>
<i><b>Tip</b></i>: Check content in `controller-action-step-6` git branch
## Run integration test
```
mvn -Pit verify
```
## Package & Deploy
```
mvn deploy kalix:deploy
```