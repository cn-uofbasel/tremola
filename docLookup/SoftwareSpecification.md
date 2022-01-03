# Software Specification Document
#####  (Interpretation based on Balzert, Lehrbuch der Softwaretechnik (3rd Edition, Chapter 20.3))
#####  Structure used from Lecture Software Engineering, University Basel, lecturer : Marcel Lüthi


| Version | Author         | Source  | Status      | Date       | Comments          |
|---------|----------------|---------|-------------|------------|-------------------|
| 0.1     | Etienne Mettaz | Tremola | In Progress | 16.12.2021 |                   |
| 0.2     | Etienne Mettaz | Tremola | In Progress | 29.12.2021 |                   |
| 0.3     | Etienne Mettaz | Tremola | In Progress | 01.01.2022 | Use-case complete |
| 1.0     | Etienne Mettaz | Tremola | Done        | 03.01.2022 |                   |


## 1. Vision and Goals
Description of the vision and goals that should be solved through the implementation of the system.

* /V10/ Allowing users to connect and initiate a gossip talk easily on the app Tremola
* /G10/ Add a peer discovery tool and an automatic initial handshake


## 2. General Conditions and Stakeholders
Description of organizational framework: areas of application, target groups, operating conditions

* /S10/ Tremola Users
* /S20/ Tremola Contributors & Developers
* /C10/ Project is part of android App to connect peers using Secure Scuttlebutt
* /C20/ Project runs on any Android phone running on targeted version of the OS 
* /C30/ Project is capable of running multi-year

## 3. Context and Overview
Definition of the relevant system environment (context) and system overview

* /O10/ Project is written in Kotlin, Java and Javascript
* /O20/ Project is sourced on GitHub

## 4. Functional Requirements
The core functionality of the system is to be described from the client's point of view on the
highest abstraction level.

* /F10/ The system must offer the possibility to initiate a peer discovery query on the GUI
* /F20/ The Shortname (hash of the public key) of the Target peer must be sufficient to 
  initiate a peer discovery query 
* /F30/ The system must not recognize Shortname that do not match the correct pattern
* /F40/ The peer discovery sends broadcast messages
* /F50/ The code allows an easy addition of new means to reach peers, including Bluetooth
* /F60/ A peer receiving the query must understand it
* /F70/ If the Target is in the receiving peer's contact list or if he is the Target himself, he 
  can answer the query by delivering the queried data to the Initiator
* /F80/ If the receiving peer doesn't know the Target's public key, he can forward it
* /F90/ The receiving peer can forward it on any channel that he knows, not only the one that received the query
* /F100/ The system must be able to choose between two answers
* /F110/ The system should be able in the future to give the user the option not to forward any query
* /F120/ The system should be able in the future to give the user the option not to automatically add a peer found by a lookup

The functional requirements can be determined with the help of use cases. The use cases should be described in detail in Appendix A.

## 5. Non-functional Requirements
A quality target determination for the system should be made using a table

| System Quality  | very good | good | normal | not relevant |
|-----------------|-----------|------|--------|--------------|
| Functionality   |           | x    |        |              |
| Reliability     |           | x    |        |              |
| Usability       |           |      | x      |              |
| Efficiency      |           |      | x      |              |
| Maintainability |           | x    |        |              |
| Portability     |           |      | x      |              |

Individual requirements can be defined as follows:

* /N10/ The functionality should be an enhancement to already existing peering functionality of Tremola

## 6. Acceptance Criteria
Acceptance criteria determine how requirements can be checked for their implementation before acceptance

* /A10/ The front-end allows a user to enter a shortname to start a request
* /A20/ The front-end refuses a request and notify the user of the shortname does not have the correct format
* /A30/ A new contact is added in case of a successful lookup
* /A40/ The user gets notified in case of an unsuccessful lookup, i.e. when the timer goes off 
* /A50/ The feature and functional changes are documented in the source code via Javadocs

## 7. Glossary
*lookup*: The process of searching for a peer with the help of a shortname.
*shortname*: A 10 character word, split in two by a '-', that is computed from the public key (unique identifier) of a user.
*Initiator*: The initiator peer of the lookup.
*Target*: The user that the Initiator wants to peer with.
*Friend*: A peer that knows the public key of the Target.
*Tremola App*: The [Tremola Android App](https://github.com/etienne428/tremola), a Secure Scuttlebutt client that supports private chat


## 7. Glossary
# Appendix

## Appendix A. Use-cases

### Initiator's point of view, invalid query

#### Use Case 1:
* Name - *The Initiator tries to send an invalid shortname*
* Actors - *Tremola App, Initiator*
* Prerequisite - *The Initiator has a phone with the appropriate version of the Tremola App*
* Standard flow -
    * The Initiator open the Lookup tab in the Contact menu
    * The Initiator types an invalid shortname and click and the 'checked' button
* Result (Success) - *The Initiator gets a notification that the shortname is not valid*
* Result (Failure) - *The frontend sends the (invalid) shortname to the backend; No notification appears*

#### Use Case 2:
* Name - *The Initiator tries to query his own shortname*
* Actors - *Tremola App, Initiator*
* Prerequisite - *The Initiator has a phone with the appropriate version of the Tremola App*
* Standard flow -
  * The Initiator open the Lookup tab in the Contact menu
  * The Initiator types his own shortname and click and the 'checked' button
* Result (Success) - *The Initiator gets a notification that the shortname is his own*
* Result (Failure) - *The query is sent; No notification appears*

#### Use Case 3:
* Name - *The Initiator tries to send a shortname that is already in the database*
* Actors - *Tremola App, Initiator*
* Prerequisite - *The Initiator has a phone with the appropriate version of the Tremola App*
* Standard flow -
  * The Initiator open the Lookup tab in the Contact menu
  * The Initiator types a valid shortname and click and the 'checked' button
* Result (Success) - *The Initiator gets a notification that the shortname is already in the database*
* Result (Failure) - *The query is sent; No notification appears*

### Initiator's point of view, valid but unsuccessful query

#### Use Case 4:
* Name - *The Initiator sends a query that doesn't receive any valid answer*
* Actors - *Tremola App, Initiator*
* Prerequisite - *The Initiator has a phone with the appropriate version of the Tremola App*
* Standard flow -
  * The Initiator open the Lookup tab in the Contact menu
  * The Initiator types the target's shortname and click and the 'checked' button
* Result (Success) - *The user gets a notification that the query was not successful*
* Result (Failure) - *The query is not sent; No notification appears*

### Initiator's point of view, valid and successful query

#### Use Case 5:
* Name - *The Initiator sends a query that receives a valid answer*
* Actors - *Tremola App, Initiator, other user(s)*
* Prerequisite - *The Initiator and other user(s) have phones with the appropriate version of the Tremola App*
* Standard flow -
  * The Initiator open the Lookup tab in the Contact menu
  * The Initiator types the target's shortname and click and the 'checked' button
* Result (Success) - *The user gets a notification that a new contact was added*
* Result (Failure) - *The query is not sent; No notification appears; The contact is not properly added*

### Receiver's point of view, unsuccessful query

#### Use Case 6:
* Name - *The user receives a query but doesn't know the answer*
* Actors - *Tremola App, user*
* Prerequisite - *The user has a phone with the appropriate version of the Tremola App*
* Standard flow -
  * The user receives a valid query but doesn't find a matching peer in his database 
* Result (Success) - *The user forwards the query as a broadcast*
* Result (Failure) - *The query is not forwarded; A wrong answer is sent to the Initiator*

#### Use Case 7:
* Name - *The user receives a query for the second time*
* Actors - *Tremola App, user*
* Prerequisite - *The user has a phone with the appropriate version of the Tremola App*
* Standard flow -
  * The user receives the same query for the second time
* Result (Success) - *The user drops the query*
* Result (Failure) - *The query is processed*

#### Use Case 8:
* Name - *The user receives a query with an incorrect signature*
* Actors - *Tremola App, user*
* Prerequisite - *The user has a phone with the appropriate version of the Tremola App*
* Standard flow -
  * The user receives a query which triplet signature, message, Initiator's public key does not match
* Result (Success) - *The user drops the query*
* Result (Failure) - *The query is processed*

#### Use Case 9:
* Name - *The user receives a query that he initiated himself*
* Actors - *Tremola App, user*
* Prerequisite - *The user has a phone with the appropriate version of the Tremola App*
* Standard flow -
  * The user receives his own query
* Result (Success) - *The user drops the query*
* Result (Failure) - *The query is processed*
* 
### Initiator's point of view, successful query

#### Use Case 10:
* Name - *The user receives a query about himself*
* Actors - *Tremola App, user*
* Prerequisite - *The user has a phone with the appropriate version of the Tremola App*
* Standard flow -
  * The user receives a valid query about himself
* Result (Success) - *The user sends a reply to the Initiator with his public key*
* Result (Failure) - *The query is forwarded; No reply is sent to the Initiator*

#### Use Case 11:
* Name - *The user receives a query about a contact in his database*
* Actors - *Tremola App, user*
* Prerequisite - *The user has a phone with the appropriate version of the Tremola App*
* Standard flow -
  * The user receives a valid query about a contact in his database
* Result (Success) - *The user sends a reply to the Initiator with the Target's public key*
* Result (Failure) - *The query is forwarded; No reply is sent to the Initiator; The reply contains the wrong key*