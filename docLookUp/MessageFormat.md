# Description of the format of the messages
-- version 1.0 15.12.2021

This is a Description of the content of the messages for the discovery protocol

## General description
Each message is json formatted.

## First step
The first step is the discovery broadcast. The Initiator broadcasts a message to find for a peer.
It must contains : 
  - The Target's Shortname (a 10 characters hash of the public key)
  - The Initiator's SSB identity (format "@...=.ed25519" or did:ssb:ed25519:...")
  - a hop count
  - a query identifier such that the tuple <Initiator's identity, query identifier> is unique

Upon reception, a peer can
  - discard it if he already received this query
If not, he registers the tuple <Initiator's identity, query identifier> with a time-to-live
(a conservative estimation of the time to live of the query). Then
  - send a message from step 2 if he is the Target
Else, he perform a lookup in its contact database and either
  - send a message from step 2 if the lookup resolve with an entry
  - broadcast a message

