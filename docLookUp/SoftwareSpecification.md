# Software Specification Document
#####  (Interpretation based on Balzert, Lehrbuch der Softwaretechnik (3rd Edition, Chapter 20.3))
#####  Structure used from Lecture Software Engineering, University Basel, lecturer : Marcel Lüthi


| Version | Author | Source | Status | Date | Comments |
| ------- | ----- | ------ | ------ | ----- | --------- |
|  0.1    |  Etienne Mettaz | Tremola | In Progress | 16.12.2021 | |


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
* /F20/ The Shortname (hash of the public key) of the Targeted peer must be sufficient to 
  initiate a peer discovery query 
* /F30/ The peer discovery sends a UDP broadcast
* /F40/ Any peer receiving the query must understand it
* /F50/ If the Target is in the receiving peer's contact list or if he is the Target himself, he 
  can answer the query by delivering the queried data to the Initiator
* /F60/ If the receiving peer doesn't know the Target, he can forward it
* /F70/ Upon reception of an answer, the Initiator must have enough information to contact the 
  Target
* /F80/ The system must not recognize false keywords
* /F90/ The system should be capable of letting the tooltip appear in less than one second after the user hovers over a keyword
* /F100/ The functionality should be capable of being activated and deactivated by a user
* /F110/ The functionality should be available offline
* /F120/ The system should allow keywords to be searchable
* /F130/ The functionality should be able to distinguish among multiple keywords added for one entry
* /F140/ The functionality should be compatible with Bibtex citations
* /F150/ The system should be able in the future to give the user the option to choose from different MSC (2010, 2020, ...) editions

The functional requirements can be determined with the help of use cases. The use cases should be described in detail in Appendix A.

## 5. Non-functional Requirements
A quality target determination for the system should be made using a table

| System Quality | very good | good | normal | not relevant |
| -------------------  | -------- | --- | ------ | -------------- |
| Functionality |          |     |   x    |                 |
| Reliability   |          |  x  |        |                 |
| Usability     |           |      |       x         |
| Efficiency    |          |         |   x    |                 |
| Maintainability   |          |    |     x   |                 |
| Portability   |          |  x  |        |                 |

Individual requirements can be defined as follows:

* /N10/ The functionality should be an enhancement to already existing software of Jabref
* /N10/ The database should be able to be easily updated

## 6. Acceptance Criteria
Acceptance criteria determine how requirements can be checked for their implementation before acceptance

* /A10/ The correct Mathematical Subject Classification is given for an existing keyword
* /A20/ The classification shows up as a Tooltip
* /A30/ The feature does not show up when disabled
* /A40/ The feature and its functional requirements pass all manual unit tests
* /A50/ The feature and functional changes are documented in the source code via Javadocs
* /A60/ The feature and its functionality are documented in the [Jabref User Documentation](https://docs.jabref.org/))

## 7. Glossary
*Tooltip*: A GUI box feature in Jabref that appears as a user hovers over a specific trigger providing additional information
*Keyword*: Searchable additions to Jabref entries for better organization and bibliography management. Found under the "Optional Fields" tab when created an entry.

# Appendix

## Appendix A. Use-cases

### Use Case 1:
* Name - *Want mathematics subject classification for keyword*
* Actors - *Jabref System, User*
* Prerequisite - *Mathematics subject classification exists for keyword; Classification tooltip functionality exists (& is enabled) in Jabref*
* Standard flow -
    * User types in keyword while creating entry
    * Jabref generates entry
    * User hovers over the keyword
    * Tooltip appears with mathematics subject classification corresponding to keyword
* Result (Success) - *Tooltip with correct classification appears over keyword*
* Result (Failure) - *Tooltip does not recognise keyword; Tooltip does not show correct classification; Tooltip does not appear for other reason*

### Use Case 2:

* Name - *Mathematical subject name too long*
* Actors - *Jabref System, User*
* Prerequisite - *Mathematics subject classification exists for keyword; BibTex citation exists*
* Standard flow -
    * User want to add/import an entry but does not want to type the full MSC name
    * User types in keyword while creating entry
    * Jabref generates entry with just the keyword
* Result (Success) - *User can add MSC keyword to entry instead of subject name*
* Result (Failure) - *Tooltip does not show full subject name; Tooltip does not recognise keyword; Tooltip does not show correct classification*

### Use Case 3:

* Name - *Multiple keywords*
* Actors - *Jabref System, User*
* Prerequisite - *Tooltip can distinguish among keywords*
* Standard flow -
    * User types in multiple keywords while creating entry, using a ";" between keywords to separate them
    * Jabref generates entry
    * User hovers over each of the keywords individually
    * Tooltip appears with mathematics subject classifications corresponding to each individual keyword in entry
* Result (Success) - *Tooltip returns multiple subjects for multiple keywords*
* Result (Failure) - *Tooltip does not show multiuple subjects; Tooltip does not recognise multiple keywords; Tooltip does not show correct classifications*

### Use Case 4:

* Name - *Many papers*
* Actors - *Jabref System, User*
* Prerequisite - *Multiple entries of mathematics papers with existing keywords for all of them*
* Standard flow -
    * User adds MSC keyword to multiple entries
    * Looks through entries with the same keyword to organize the bibliography
* Result (Success) - *Keyword search identifies correct and relevant math papers; Tooltip for each keyword is the same if the keyword is the same*
* Result (Failure) - *Tooltip does not show or is different for the same keyword*

### Use Case 5:

* Name - *Finding papers in specific area*
* Actors - *Jabref System, User*
* Prerequisite - *The user has a library in which there are some entries with MSC codes; The user knows the field he is interested in, but does not know the exact MSC code*
* Standard flow -
    * The user opens an entry
    * The user goes to the tab “General”
    * The user hovers over the Keywords, which represents the MSC codes
    * The user learns the general direction of the MSC code
    * Because of the logical composition of the codes, he learns the meaning of the start of the codes
    * The user repeats 1. To 5. Until he finds the topic he is interested in
    * Once he finds the topic, he can use the knowledge he gained to search the MSC code of the topic he is interested in
* Result (Success) - *Through hovering over entries, the user can determine the relevant keywords for further searching*
* Result (Failure) - *The user cannot figure out what keyword(s) he is looking for*

### Use Case 6:

* Name - *Activate the feature in preferences*
* Actors - *Jabref System, User*
* Prerequisite - *The status of this feature in preferences is not active*
* Standard flow -
    * User wants to activate the feature
    * User opens the preferences
    * User clicks the button, with which he can activate the feature
* Result (Success) - *The feature is active after activation*
* Result (Failure) - *The feature is still not active after activation*

### Use Case 7:

* Name - *Deactivate the feature in preferences*
* Actors - *Jabref System, User*
* Prerequisite - *The status of this feature in preferences is active*
* Standard flow -
    * User wants to deactivate the feature
    * User opens the preferences
    * User clicks the button, with which he can deactivate the feature
* Result (Success) - *The feature is no longer active after deactivation*
* Result (Failure) - *The feature is active after deactivation*

### Use Case 8:
* Name - *Check if entered MSC code is correct*
* Actors - *Jabref System, User*
* Prerequisite - *The user has added a new entry to JabRef with a MSC code, of which he knows the meaning and he wants to check if he used the correct code*
* Standard flow -
    * The user opens the entry in JabRef
    * The user goes to the tab: “General”
    * The user hovers over the Keyword with his cursor
    * After a short time, a Tooltip appears that shows the definition of the entered MSC code
* Result (Success) - *The definition, which appears in the Tooltip, is the same as the user wanted to write*
* Result (Failure) - *There is no Tooltip or the definition, which appears is not similar to the one the user wanted to write*

### Use Case 9:
* Name - *Understanding the 01A10 Keyword*
* Actors - *Jabref System, User*
* Prerequisite - *The user has the tool activated and has an entry which has the MSC code 01A10 of which he wants to know the meaning*
* Standard flow -
    * The user opens the entry in JabRef
    * The user goes to the tab: “General”
    * The user hovers over the Keyword, which represents the MSC code 01A10, with his cursor
    * After a short time, a Tooltip appears that shows the definition of 01A10
* Result (Success) - *The Tooltip shows the definition of the MSC code*
* Result (Failure) - *The Tooltip does not appear*