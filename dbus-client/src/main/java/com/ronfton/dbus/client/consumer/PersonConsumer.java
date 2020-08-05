package com.ronfton.dbus.client.consumer;

import com.linkedin.databus.client.consumer.AbstractDatabusCombinedConsumer;
import com.linkedin.databus.client.pub.ConsumerCallbackResult;
import com.linkedin.databus.client.pub.DbusEventDecoder;
import com.linkedin.databus.core.DbusEvent;
import com.ronfton.dbus.client.decoder.DbusEventDecoderWrapper;
import com.ronfton.dbus.client.decoder.GenericRow;
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
        LOG.info("+++++++++++++++++++++++ PersonConsumer +++++++++++++++++++++++");
        GenericRow row = DbusEventDecoderWrapper.getGenericRow(event, eventDecoder);
        try {
            LOG.info("person:>>>>" + row.toString());
        } catch (Exception e) {
            LOG.error("error decoding event ", e);
            return ConsumerCallbackResult.ERROR;
        }

        return ConsumerCallbackResult.SUCCESS;
    }

}
