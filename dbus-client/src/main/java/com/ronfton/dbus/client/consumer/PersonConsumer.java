package com.ronfton.dbus.client.consumer;

import com.linkedin.databus.client.consumer.AbstractDatabusCombinedConsumer;
import com.linkedin.databus.client.pub.ConsumerCallbackResult;
import com.linkedin.databus.client.pub.DbusEventDecoder;
import com.linkedin.databus.core.DbusEvent;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.apache.log4j.Logger;

public class PersonConsumer extends AbstractDatabusCombinedConsumer
{
    private static final Logger LOG = Logger.getLogger(PersonConsumer.class);

    @Override
    public ConsumerCallbackResult onDataEvent(DbusEvent e, DbusEventDecoder eventDecoder)
    {
        return processEvent(e, eventDecoder);
    }

    @Override
    public ConsumerCallbackResult onBootstrapEvent(DbusEvent e, DbusEventDecoder eventDecoder)
    {
        return processEvent(e, eventDecoder);
    }

    private ConsumerCallbackResult processEvent(DbusEvent event,
                                                DbusEventDecoder eventDecoder)
    {
        GenericRecord decodedEvent = eventDecoder.getGenericRecord(event, null);
        try {
            Utf8 firstName = (Utf8)decodedEvent.get("firstName");
            Utf8 lastName = (Utf8)decodedEvent.get("lastName");
            Long birthDate = (Long)decodedEvent.get("birthDate");
            Utf8 deleted = (Utf8)decodedEvent.get("deleted");

            LOG.info("firstName: " + firstName.toString() +
                    ", lastName: " + lastName.toString() +
                    ", birthDate: " + birthDate +
                    ", deleted: " + deleted.toString());
        } catch (Exception e) {
            LOG.error("error decoding event ", e);
            return ConsumerCallbackResult.ERROR;
        }

        return ConsumerCallbackResult.SUCCESS;
    }

}
