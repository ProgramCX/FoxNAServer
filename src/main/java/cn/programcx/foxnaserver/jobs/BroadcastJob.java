package cn.programcx.foxnaserver.jobs;

import lombok.SneakyThrows;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;


import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;
import java.util.Enumeration;
import java.util.Properties;

public class BroadcastJob extends QuartzJobBean {
    @SneakyThrows
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        //读取发送广播的端口号
        Properties props = new Properties();

        String configPath = System.getProperty("config.path", "config.properties");
        try (InputStream inputStream = new FileInputStream(configPath)) {
            props.load(inputStream);

            int port = props.getProperty("port").isEmpty() ? 25522 : Integer.parseInt(props.getProperty("port"));

            //发送广播消息
            DatagramSocket socket = new DatagramSocket();
            socket.setBroadcast(true);


            getBroadcastAddresses().forEach(addr -> {
                try {
                    InetAddress address = InetAddress.getByName(addr);
                    String msg = props.getProperty("name").isEmpty() ? "FoxNAS" : props.getProperty("name");

                    byte[] buf = msg.getBytes();
                    DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);

                    socket.send(packet);

                    System.out.println("Sent " + msg + " to " + addr);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

        }
    }

    /**
     * 获取本机广播地址
     * @return 广播地址
     * @throws Exception 异常
     */
    public static List<String> getBroadcastAddresses() throws Exception {
        List<String> broadcastAddresses = new ArrayList<>();

        // 获取本机的所有网络接口
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();

            // 跳过虚拟接口等无效接口
            if (ni.isLoopback() || !ni.isUp()) {
                continue;
            }

            // 获取该网络接口的所有 IP 地址
            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                InetAddress broadcast = ia.getBroadcast();
                if (broadcast != null) {
                    broadcastAddresses.add(broadcast.getHostAddress());
                }
            }
        }

        return broadcastAddresses;
    }
}
