[![Maven Build](https://github.com/samagra-comms/inbound/actions/workflows/build.yml/badge.svg)](https://github.com/samagra-comms/inbound/actions/workflows/build.yml/badge.svg)
[![Docker Build](https://github.com/samagra-comms/inbound/actions/workflows/docker-build-push.yml/badge.svg)](https://github.com/samagra-comms/inbound/actions/workflows/build.yml/docker-build-push.svg)

# Overview
Inbound receives the messages from a channel, and uses the channel adapter to convert it to XMessage format. Once the message is converted, it will be pushed to the kafka topic, the orchestrator will listen to this topic to further process it.
