Adapters: Adapters convert information provided by channels (SMS, Whatsapp) for each specific provider to xMessages and vise versa.

There are typically two types of communication that can happen, either the message will be sent to the user, or the message that the user enters will be received by the system.

Inbound module is about the incoming content:
Currently we accepts text/media/location/quickReplyButton/list messages. It should be implemeted according to the network provider(Netcore/Gupshup) documentation.
To enable these, we have to add them in convertMessageToXMsg and convert these according to the xMessage spec (https://uci.sunbird.org/use/developer/uci-basics/xmessage-specification)


More details can be found here: https://uci.sunbird.org/use/developer/contribution-guide/create-an-adapter#3.1.-incoming-content

