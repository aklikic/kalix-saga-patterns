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

## pom.xml setup
Reload the `pom.xml` after finished.
### Add dependencies
Add following to pom.xml:
```
<dependency>
  <groupId>io.vavr</groupId>
  <artifactId>vavr</artifactId>
  <version>0.10.4</version>
</dependency>
<dependency>
  <groupId>org.assertj</groupId>
  <artifactId>assertj-core</artifactId>
  <version>3.23.1</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.awaitility</groupId>
  <artifactId>awaitility</artifactId>
  <version>4.2.0</version>
</dependency>
```
### JDK 17 additional configuration
1. Add under `mave-compiler-plugin` plugin `configuration`:
```
<compilerArgs>
   <arg>--enable-preview</arg>
   <arg>-Xlint:deprecation</arg>
   <arg>-parameters</arg>
 </compilerArgs>
```
2. Add under `docker-maven-plugin` `configuration.images.image.entryPoint`: `<arg>--enable-preview</arg>`
3. Add under `maven-surefire-plugin` plugin `<configuration>`: `<argLine>--enable-preview</argLine>`
4. Add under `kalix-maven-plugin` plugin `<configuration>`:
```
<jvmArgs>
   <arg>--enable-preview</arg>
 </jvmArgs>
```
5. Add under `maven-failsafe-plugin` plugin `<configuration>`: `<argLine>--enable-preview</argLine>`

### Control saga spring profile
1. Add to properties: `<sagaProfile>choreography</sagaProfile>`
2. Add under `docker-maven-plugin` `configuration.images.image.entryPoint`: `<arg>-Dspring.profiles.active=${sagaProfile}</arg>`
3. Add under `kalix-maven-plugin` `configuration.jvmArgs`: `<arg>-Dspring.profiles.active=${sagaProfile}</arg>`

# Show Entity

## Setup

Create package `com.example.cinema`

## Define persistence (domain)`
1. Create package `com.example.cinema.model`
2. Implement Java Record `Show`
3. Implement sealed interface `ShowEvent`
   <i><b>Tip</b></i>: Check content in `step-1-show-entity` git branch

## Define API data structure and endpoints
1. Implement interface `CinemaApiModel` with `ShowCommand`,`Response` and `ShowResponse`
2. Implement class `ShowEntity`
   <i><b>Tip</b></i>: Check content in `step-1-show-entity` git branch


## Implement unit test
1. Create  `src/test/java` <br>
2. Create package `com.example.cinema`
3. Implement helper classes `DomainGenerators`, `ShowBuilder` and `ShowCommandGenerators`
4. Implement business logic state test: `ShowTest`
6. Implement Entity test: `ShowEntityTest`<br>
   <i><b>Tip</b></i>: Check content in `step-1-show-entity` git branch

## Run unit test
```
mvn test
```
## Run locally
Start the service and kalix runtime:

```
mvn kalix:runAll
```

## Test service locally
Create show:
```
curl -XPOST -d '{
  "title": "title",
  "maxSeats": 100
}' http://localhost:9000/cinema-show/1 -H "Content-Type: application/json"
```
Reserve a seat:
```
curl -XPATCH -d '{
  "walletId": "title",
  "reservationId": "res1",
  "seatNumber": 1
}' http://localhost:9000/cinema-show/1/reserve -H "Content-Type: application/json"
```
Confirm seat payment:
```
curl -XPATCH http://localhost:9000/cinema-show/1/confirm-payment/res1 -H "Content-Type: application/json"
```
Get:
```
curl -XGET http://localhost:9000/cinema-show/1 -H "Content-Type: application/json"
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
    kalix projects new cinema-booking --region=gcp-us-east1
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
   `Kalix Container Registry (KCR)` path + `/` + `USERNAME` + `/cinema-booking`<br>
   **Example** where `Kalix Container Registry (KCR)` path is `kcr.us-east-1.kalix.io` and `USERNAME` is `myuser`:<br>
```
<dockerImage>kcr.us-east-1.kalix.io/myuser/cinema-booking/${project.artifactId}</dockerImage>
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
cinema-booking-java                          50s    0          Ready         1.0-SNAPSHOT                  
```
**Note**: When deploying service for the first time it can take up to 1 minute for internal provisioning

## Test service in production
Proxy connection to Kalix service via Kalix CLI
```
kalix service proxy cinema-booking-java --port 9000
```
Proxy Kalix CLI command will expose service proxy connection on `localhost:8080` <br>

Create show:
```
curl -XPOST -d '{
  "title": "title",
  "maxSeats": 100
}' http://localhost:8080/cinema-show/1 -H "Content-Type: application/json"
```
Reserve a seat:
```
curl -XPATCH -d '{
  "walletId": "title",
  "reservationId": "res1",
  "seatNumber": 1
}' http://localhost:8080/cinema-show/1/reserve -H "Content-Type: application/json"
```
Confirm seat payment:
```
curl -XPATCH http://localhost:8080/cinema-show/1/confirm-payment/res1 -H "Content-Type: application/json"
```
Get:
```
curl -XGET http://localhost:8080/cinema-show/1 -H "Content-Type: application/json"
```

# Shows by available seats View
## Define View data structures
1. In interface `CinemaApiModel` add `ShowsByAvailableSeatsViewRecord` and `ShowsByAvailableSeatsRecordList`
2. Implement class `ShowsByAvailableSeatsView`
   <i><b>Tip</b></i>: Check content in `step-2-shows-view` git branch

## Implement integration test
1. Delete `IntegrationTest` in `src/itjava.com.example`
2. Create package `cinema`
3. Add helper classes: `TestUtils`, `Calls` (with all Show related endpoints only)
4. Implement integration test `ShowsByAvailableSeatsViewIntegrationTest`

## Run integration test
```
mvn -Pit verify
```
## Test service locally
Create show:
```
curl -XPOST -d '{
  "title": "title",
  "maxSeats": 100
}' http://localhost:9000/cinema-show/1 -H "Content-Type: application/json"
```
Search view:
```
curl -XGET http://localhost:9000/cinema-shows/by-available-seats/1 -H "Content-Type: application/json"
```
# Wallet Entity
## Setup

Create package `com.example.wallet`

## Define persistence (domain)`
1. Create package `com.example.wallet.model`
2. Implement Java Record `Wallet`
3. Implement sealed interface `WalletEvent`
   <i><b>Tip</b></i>: Check content in `step-3-wallet-entity` git branch

## Define API data structure and endpoints
1. Implement interface `WalletApiModel` with `WalletCommand` and `WalletResponse`
2. Implement class `WalletEntity`
   <i><b>Tip</b></i>: Check content in `step-3-wallet-entity` git branch

## Implement unit test
2. Create package `com.example.wallet`
3. Implement helper classes `DomainGenerators`
4. Implement business logic state test: `WalletTest`
6. Implement Entity test: `WalletEntityTest`<br>
   <i><b>Tip</b></i>: Check content in `step-3-wallet-entity` git branch

## Run unit test
```
mvn test
```
## Run locally
Start the service and kalix runtime:

```
mvn kalix:runAll
```

## Test service locally
Create wallet with initial balance:
```
curl -XPOST http://localhost:9000/wallet/1/create/100 -H "Content-Type: application/json"
```
Charge:
```
curl -XPATCH -d '{
  "amount": 50,
  "expenseId": "exp1",
  "commandId": "exp1"
}' http://localhost:9000/wallet/1/charge -H "Content-Type: application/json"
```
Get:
```
curl -XGET http://localhost:9000/wallet/1 -H "Content-Type: application/json"
```

# Choreography Saga





