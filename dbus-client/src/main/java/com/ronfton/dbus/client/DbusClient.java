package com.ronfton.dbus.client;

import com.linkedin.databus.client.DatabusHttpClientImpl;
import com.ronfton.dbus.client.consumer.ConsumerHandler;

import java.rmi.registry.LocateRegistry;

/**
 * @Description databus客户端
 * @author somiya
 * @date 2020/7/2 10:01 AM
 */
public class DbusClient
{
    private static final String SCAN_PACKAGE = "com.ronfton.dbus.client.consumer";

    public static void main(String[] args) throws Exception
    {

        try
        {
            // 扫描Consumer
            ConsumerHandler.scanConsumer(SCAN_PACKAGE);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        DatabusHttpClientImpl.Config configBuilder = new DatabusHttpClientImpl.Config();

        // 设置启动参数
        String log4jFileOption = String.format("-l%s", DbusClient.class.getResource("/conf/client_log4j.properties").getPath());
        String configFileOption = String.format("-p%s", DbusClient.class.getResource("/conf/client.properties").getPath());
        String[] theArgs = {log4jFileOption, configFileOption};

        //Try to connect to a relay on localhost
        ConsumerHandler.connectRelay(configBuilder.getRuntime());

        // checkpoints根目录
        String checkpointsDir = DbusClient.class.getResource("/client_checkpoints").getPath();
        configBuilder.getCheckpointPersistence().getFileSystem().setRootDirectory(checkpointsDir);
        //Instantiate a client using command-line parameters if any
        DatabusHttpClientImpl client = DatabusHttpClientImpl.createFromCli(theArgs, configBuilder);
        LocateRegistry.createRegistry(client.getContainerStaticConfig().getJmx().getRmiRegistryPort());

        //register callbacks
        ConsumerHandler.registerConsumers(client);

        //fire off the Databus client
        client.startAndBlock();
    }
}
