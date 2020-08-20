package com.ronfton.dbus.server;

import com.linkedin.databus.container.netty.HttpRelay;
import com.linkedin.databus.core.util.InvalidConfigException;
import com.linkedin.databus2.core.DatabusException;
import com.linkedin.databus2.relay.DatabusRelayMain;
import com.linkedin.databus2.relay.config.PhysicalSourceStaticConfig;
import org.apache.commons.cli.HelpFormatter;

import java.io.IOException;
import java.util.Arrays;

/**
 * @Description 数据库监控服务端
 * @author somiya
 * @date 2020/7/1 3:55 PM
 */

public class DbusServer extends DatabusRelayMain
{
    HelpFormatter _helpFormatter;
    public DbusServer(HttpRelay.StaticConfig config, PhysicalSourceStaticConfig [] pConfigs)
            throws IOException, InvalidConfigException, DatabusException
    {
        super(config, pConfigs);
    }

    private static final String MYSQL = "mysql";
    private static final String ORACLE = "oracle";

    public static void main(String[] args) throws Exception
    {
        Cli cli = new Cli("启动时要指定启动参数：mysql或oracle");
        if (args.length <= 0 || !Arrays.asList(MYSQL, ORACLE).contains(args[0])) {
            cli.printCliHelp();
            System.exit(0);
        }
        // 配合文件查找根路径
        String sourceName = String.format("conf/sources-%s.json", args[0]);

        //String sourcePath = DbusServer.class.getResource(sourceName).getPath();
        cli.setDefaultPhysicalSrcConfigFiles(sourceName);


        // 设置启动参数
        //String log4jFileOption = String.format("-l%s", DbusServer.class.getResource("/conf/relay_log4j.properties").getPath());
        String log4jFileOption = String.format("-l%s", "conf/relay_log4j.properties");
        String configFileOptionName = String.format("conf/relay_%s.properties", args[0]);
        //String configFileOption = String.format("-p%s", DbusServer.class.getResource(configFileOptionName).getPath());
        String configFileOption = String.format("-p%s", configFileOptionName);
        String[] theArgs = {log4jFileOption, configFileOption};
        cli.processCommandLineArgs(theArgs);
        cli.parseRelayConfig();
        // Process the startup properties and load configuration
        PhysicalSourceStaticConfig[] pStaticConfigs = cli.getPhysicalSourceStaticConfigs();
        HttpRelay.Config config = cli.getRelayConfigBuilder();

        // 定义监听字段的Avro schema文件
        //String schemaDir = DbusServer.class.getResource("/schemas_registry").getPath();
        String schemaDir = "schemas_registry";
        config.getSchemaRegistry().getFileSystem().setSchemaDir(schemaDir);
        HttpRelay.StaticConfig staticConfig = config.build();

        // Create and initialize the server instance
        DatabusRelayMain serverContainer = new DatabusRelayMain(staticConfig, pStaticConfigs);

        serverContainer.initProducers();
        serverContainer.registerShutdownHook();
        serverContainer.startAndBlock();
    }
}