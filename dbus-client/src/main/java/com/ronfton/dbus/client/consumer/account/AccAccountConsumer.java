package com.ronfton.dbus.client.consumer.account;

import com.linkedin.databus.client.consumer.AbstractDatabusCombinedConsumer;
import com.linkedin.databus.client.pub.ConsumerCallbackResult;
import com.linkedin.databus.client.pub.DatabusCombinedConsumer;
import com.linkedin.databus.client.pub.DbusEventDecoder;
import com.linkedin.databus.core.DbusEvent;
import com.ronfton.dbus.client.consumer.Consumer;
import com.ronfton.dbus.client.consumer.RftTagConsumer;
import com.ronfton.dbus.client.consumer.account.entity.AccAccount;
import com.ronfton.dbus.client.decoder.DbusEventDecoderWrapper;
import com.ronfton.dbus.client.decoder.GenericRow;
import org.apache.log4j.Logger;

/**
 * @Description 账户consumer
 * @author somiya
 * @date 2020/8/12 11:41 AM
 */
@Consumer(value = "account", clazz = AccAccount.class)
public class AccAccountConsumer extends AbstractDatabusCombinedConsumer implements DatabusCombinedConsumer
{
    private static final Logger LOG = Logger.getLogger(RftTagConsumer.class);

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

    private ConsumerCallbackResult processEvent(DbusEvent event, DbusEventDecoder eventDecoder)
    {
        LOG.info("+++++++++++++++++++++++ AccAccountConsumer +++++++++++++++++++++++");
        GenericRow row = DbusEventDecoderWrapper.getGenericRow(event, eventDecoder);
        try {
            LOG.info("acc_account:>>>>" + row.toString());
        } catch (Exception e) {
            LOG.error("error decoding event ", e);
            return ConsumerCallbackResult.ERROR;
        }

        return ConsumerCallbackResult.SUCCESS;
    }
}