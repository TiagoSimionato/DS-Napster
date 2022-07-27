---
output:
  pdf_document: default
---
# DS-Napster

Repo for my Distributed Systems Programming Project. It can share large files between computers like Napster, using a central Server with many Peers with it.

## Compile

To compile, run the command while on the /napster folder

    javac -d ../bin *.java

## Server

To run the Server, go to the /bin folder and run

    java napster.Servidor

Before the Server starts it needs to read an *Ip* addr, so type

    127.0.0.1

## Peer

To run the Peer, go to the /bin folder and run

    java napster.Peer

Before the Peer starts it needs to read its own *Ip* addr and *Port*. If the peer cannot start after reading both infos from user (e.g. port already in use) it will ask for those info again. It will then ask for the storage folder of the peer, creating it if it does not exist.

At last, the peer class will loop in a interactive menu ask for what operation it should do.