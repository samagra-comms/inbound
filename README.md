![Maven Build](https://github.com/samagra-comms/inbound/actions/workflows/build.yml/badge.svg)
![Docker Build](https://github.com/samagra-comms/inbound/actions/workflows/docker-build-push.yml/badge.svg)

# Overview
Inbound receives the messages from a channel, and uses the channel adapter to convert it to XMessage format. Once the message is converted, it will be pushed to the kafka topic, the orchestrator will listen to this topic to further process it.

# Getting Started

## Prerequisites

* java 11 or above
* docker
* kafka
* postgresql
* redis
* fusion auth
* lombok plugin for IDE
* maven

## Build
* build with tests run using command **mvn clean install -U**
* or build without tests run using command **mvn clean install -DskipTests**

# Detailed Documentation
[Click here](https://uci.sunbird.org/use/developer/uci-basics)