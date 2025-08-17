package cn.programcx.foxnaserver.util;

import org.springframework.stereotype.Component;

import java.net.*;
import java.util.Enumeration;

@Component
public class LANIPUtil {
    public  String getLocalIPv4() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;

            Enumeration<InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && isAllowedPrivateIPv4(addr)) {
                    return addr.getHostAddress();
                }
            }
        }
        return null;
    }

    private  boolean isAllowedPrivateIPv4(InetAddress addr) {
        String ip = addr.getHostAddress();
        // 只保留 192.168.x.x 和 10.x.x.x
        return ip.startsWith("10.") || ip.startsWith("192.168.");
    }


    public  String getLocalIPv6() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;

            Enumeration<InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (addr instanceof Inet6Address && !addr.isLoopbackAddress() && isPrivateIPv6(addr)) {
                    return addr.getHostAddress().split("%")[0]; // 包含 zoneId（如 %eth0）
                }
            }
        }
        return null;
    }

    private  boolean isPrivateIPv6(InetAddress addr) {
        String ip = addr.getHostAddress();

        // 去掉 zoneId，例如 "fe80::1%wlan0"
        int percentIndex = ip.indexOf('%');
        if (percentIndex != -1) {
            ip = ip.substring(0, percentIndex);
        }

        // 检查是否为 ULA（fd00::/8） 或 链路本地（fe80::/10）
        return ip.startsWith("fd") || ip.startsWith("fe8");
    }

    public static void main(String[] args) throws SocketException {
        LANIPUtil util = new LANIPUtil();
        System.out.println(util.getLocalIPv6());
    }

}
