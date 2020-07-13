package com.ronfton.dbus.client;

import com.linkedin.databus.client.DatabusHttpClientImpl;
import com.ronfton.dbus.client.consumer.PersonConsumer;

import java.rmi.registry.LocateRegistry;

/**
 * @Description databus客户端
 * @author somiya
 * @date 2020/7/2 10:01 AM
 */
public class DbusClient
{
    static final String PERSON_SOURCE = "com.linkedin.events.example.or_test.Person";
    public static void main(String[] args) throws Exception
    {
        DatabusHttpClientImpl.Config configBuilder = new DatabusHttpClientImpl.Config();

        // 配合文件查找根路径
        String classPath = configBuilder.getClass().getResource("/").getPath();

        // 设置启动参数
        String log4jFileOption = String.format("-l%s", DbusClient.class.getResource("/conf/client_log4j.properties").getPath());
        String configFileOption = String.format("-p%s", DbusClient.class.getResource("/conf/client.properties").getPath());
        String[] theArgs = {log4jFileOption, configFileOption};

        //Try to connect to a relay on localhost
        configBuilder.getRuntime().getRelay("1").setHost("127.0.0.1");
        configBuilder.getRuntime().getRelay("1").setPort(11115);
        configBuilder.getRuntime().getRelay("1").setSources(PERSON_SOURCE);

        // checkpoints根目录
        String checkpointsDir = DbusClient.class.getResource("/client_checkpoints").getPath();
        configBuilder.getCheckpointPersistence().getFileSystem().setRootDirectory(checkpointsDir);
        //Instantiate a client using command-line parameters if any
        DatabusHttpClientImpl client = DatabusHttpClientImpl.createFromCli(theArgs, configBuilder);
        LocateRegistry.createRegistry(client.getContainerStaticConfig().getJmx().getRmiRegistryPort());

        //register callbacks
        PersonConsumer personConsumer = new PersonConsumer();
        //client.register(personConsumer, PERSON_SOURCE);
        client.registerDatabusStreamListener(personConsumer, null, PERSON_SOURCE);
        client.registerDatabusBootstrapListener(personConsumer, null, PERSON_SOURCE);
        //fire off the Databus client
        client.startAndBlock();
    }
}
