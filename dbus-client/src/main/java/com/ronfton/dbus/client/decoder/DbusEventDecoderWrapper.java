package com.ronfton.dbus.client.decoder;

import com.linkedin.databus.client.pub.DbusEventDecoder;
import com.linkedin.databus.core.DbusEvent;
import com.linkedin.databus.core.DbusOpcode;
import com.ronfton.dbus.client.consumer.PersonConsumer;
import org.apache.avro.generic.GenericContainer;
import org.apache.avro.generic.GenericRecord;
import org.apache.commons.collections.CollectionUtils;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * @Description DbusEvent解码器封装
 * @author somiya
 * @date 2020/8/5 6:01 PM
 */
public class DbusEventDecoderWrapper
{
    private static final Logger LOG = Logger.getLogger(PersonConsumer.class);

   public static GenericRow getGenericRow(DbusEvent event, DbusEventDecoder decoder) {
       GenericContainer decodedObject = decoder.getGenericContainer(event, null);

       try
       {
           if (decodedObject instanceof List) {
               List<GenericRecord> recordList = (List<GenericRecord>) decodedObject;
               if (CollectionUtils.isEmpty(recordList)) {
                   return null;
               }
               if (recordList.size() < 2) {
                   return new GenericRow(recordList.get(0), null);
               }
               return new GenericRow(recordList.get(0), recordList.get(1));
           } else {
               if (DbusOpcode.UPSERT.equals(event.getOpcode())) {
                   return new GenericRow((GenericRecord) decodedObject, null);
               }
               return new GenericRow(null, (GenericRecord) decodedObject);
           }
       } catch (Exception e) {
           LOG.error("getGenericRow ERROR:" + e.getMessage(), e);
           return null;
       }
   }
}