package net.java.sip.communicator.service.protocol.mock;

import net.java.sip.communicator.service.protocol.*;

import java.util.*;

public class MockAccountID
    extends AccountID
{
    public MockAccountID(String userID,
                            Map<String, String> accountProperties,
                            String protocolName)
    {
        super(userID, accountProperties, protocolName, "mock");
    }
}
