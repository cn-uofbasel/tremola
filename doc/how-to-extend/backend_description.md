# Backend

We will here give a little help for understanding the backend implementation, that you find in
app/src/main/java/nz/scuttlebutt/tremola/ .

## Definition

### Mark:

A mark is a Secure Scuttlebutt multiserver address. It contains:

1. a protocol (mostly `net`)
2. a domain or IP address
3. a port
4. 'shs' for 'secure handshake'
5. a curve25519 public key

For example, `net:192.168.43.69:8008~shs:uA2qyrA6OaSeDuSUjGtrxHU9nibaajIfVcY07cIrONc=`
is a valid multiserver address.

### LogID, FeedID:

Also abbreviated lid and fid, they represent a Secure Scuttlebutt ID. An SSB ID is a public key to which we add a '@' at
the beginning and a '.ed25519' at the end.

For example, `@uA2qyrA6OaSeDuSUjGtrxHU9nibaajIfVcY07cIrONc=.ed25519`  is a valid ID.

### EBT

See <a href="https://github.com/ssbc/epidemic-broadcast-trees">Epidemic Broadcast Tree </a>

## Short overview

The entrance class of our code is MainActivity, as in each android app. OnCreate is the function called at launch.
It initialises different fields, but mostly 4 threads.

### Thread 1 and 2: UDP Broadcast

Those two threads, implemented entirely in /peering/UDPbroadcast.kt, are fairly simple: one announces the peer via
UDP/IP broadcast and the other listens to other peers doing the same.
With the received information, it stores a map of the local peers available that will be consulted by other classes.

### Thread 3: RPC

This thread is the most important one, doing most of the job. It waits for new connections on a TCP port, initiates
a secure handshake with it and, if the peers are connected, exchange data via remote procedure calls.

### Thread 4: Lookup

This thread also listens to UDP broadcast, but on a different port, and using a different protocol (not SSB). It asks
for information about peer FeedIDs with the help of Shortnames. Shortnames are "nicknames" based on the hash of the
public key to give a human-readable (virtually) unique identification name to each peer (for more information on the
statistic of its uniqueness, see /utils/HelperFunctions.Companion::id2). It allows to easily add a peer as contact,
even if this peer is not connected (as long as a peer knowing its feedID is connected and reachable).

### Answering the frontend

In addition to these threads, the function onFrontendRequest in WebAppInterface.kt responds to the requests coming from
the backend. You can find a description of each call as a comment directly in the code.
