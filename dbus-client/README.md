# RelayPull过程

doPickRelay -> doRequestSources -> doSourcesResponseSuccess -> doRequestRegister -> doRegisterResponseSuccess -> 
doRequestStream -> doReadDataEvents -> doStreamResponseDone -> doRequestStream -> doReadDataEvents -> doStreamResponseDone -> 
doRequestStream -> doReadDataEvents -> doStreamResponseDone -> ....

