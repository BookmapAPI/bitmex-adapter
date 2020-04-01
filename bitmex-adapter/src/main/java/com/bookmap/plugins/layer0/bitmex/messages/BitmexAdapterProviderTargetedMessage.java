package com.bookmap.plugins.layer0.bitmex.messages;

import velox.api.layer1.messages.UserProviderTargetedMessage;

public interface BitmexAdapterProviderTargetedMessage extends UserProviderTargetedMessage {
    
    String getVersion();
}
