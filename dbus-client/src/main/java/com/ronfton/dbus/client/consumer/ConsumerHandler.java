package com.ronfton.dbus.client.consumer;

import com.linkedin.databus.client.DatabusHttpClientImpl;
import com.linkedin.databus.client.pub.DatabusClient;
import com.linkedin.databus.client.pub.DatabusCombinedConsumer;
import com.ronfton.dbus.client.util.ClassUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * @Description
 * @author somiya
 * @date 2020/8/12 2:21 PM
 */
public class ConsumerHandler
{
    private static final Logger log = Logger.getLogger(ConsumerHandler.class);

    private static final List<ConsumerEntity> SOURCES_CONSUMER_LIST = new ArrayList<>(8);

    private static final String RELAY_HOST = "localhost";
    private static final int RELAY_PORT = 11115;

    /**
     * 扫描Consumer
     *
     * @param basePackage 基础包
     */
    public static void scanConsumer(String basePackage)
    {
        if (StringUtils.isEmpty(basePackage))
        {
            return;
        }
        // 获取此包以及子包下的所有.class文件资源
        List<Class<?>> result = ClassUtil.getClasses(basePackage);
        for (Class<?> aClass : result)
        {
            if (!DatabusCombinedConsumer.class.isAssignableFrom(aClass)) {
                continue;
            }
            Consumer c = aClass.getAnnotation(Consumer.class);
            if (null == c) {
                continue;
            }
            ConsumerEntity entity = new ConsumerEntity(c.clazz().getName(), c.value(), (Class<? extends DatabusCombinedConsumer>)aClass);
            SOURCES_CONSUMER_LIST.add(entity);
        }
    }

    public static void connectRelay(DatabusHttpClientImpl.RuntimeConfigBuilder runtimeConfigBuilder) {
        for (ConsumerEntity entity : SOURCES_CONSUMER_LIST)
        {
            runtimeConfigBuilder.getRelay(entity.getRelayId()).setHost(RELAY_HOST);
            runtimeConfigBuilder.getRelay(entity.getRelayId()).setPort(RELAY_PORT);
            runtimeConfigBuilder.getRelay(entity.getRelayId()).setSources(entity.getSourceId());
        }
    }

    public static void registerConsumers(DatabusClient client) {
        for (ConsumerEntity entity : SOURCES_CONSUMER_LIST)
        {
            try
            {
                DatabusCombinedConsumer consumer = entity.getConsumer().newInstance();
                client.registerDatabusStreamListener(consumer, null, entity.getSourceId());
                client.registerDatabusBootstrapListener(consumer, null, entity.getSourceId());
            } catch (Exception e) {
                log.error("register consumer error：" + e.getMessage(), e);
            }

        }
    }

    public static class ConsumerEntity {
        private String sourceId;
        private String relayId;
        private Class<? extends DatabusCombinedConsumer> consumer;

        public ConsumerEntity(String sourceId, String relayId, Class<? extends DatabusCombinedConsumer> consumer) {
            this.sourceId = sourceId;
            this.relayId = relayId;
            this.consumer = consumer;
        }

        public String getSourceId()
        {
            return sourceId;
        }

        public String getRelayId()
        {
            return relayId;
        }

        public Class<? extends DatabusCombinedConsumer> getConsumer()
        {
            return consumer;
        }
    }
}