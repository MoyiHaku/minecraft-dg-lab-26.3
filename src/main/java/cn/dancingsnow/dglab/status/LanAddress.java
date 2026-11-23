package cn.dancingsnow.dglab.status;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class LanAddress {
    static InetAddress choose(String requested) throws IOException {
        if (requested != null) return literal(requested);
        record Choice(InetAddress address, int rank, int index) {}
        var choices = new ArrayList<Choice>();
        var interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces != null && interfaces.hasMoreElements()) {
            NetworkInterface nic = interfaces.nextElement();
            if (!nic.isUp() || nic.isLoopback()) continue;
            String label = (nic.getName() + " " + nic.getDisplayName()).toLowerCase(Locale.ROOT);
            boolean virtual = nic.isVirtual() || List.of("docker", "wsl", "vmware", "vethernet", "virtualbox",
                    "vpn", "tun", "tailscale", "zerotier", "bridge").stream().anyMatch(label::contains);
            nic.inetAddresses().filter(ip -> ip instanceof Inet4Address && ip.isSiteLocalAddress())
                .forEach(ip -> choices.add(new Choice(ip, virtual ? 1 : 0, nic.getIndex())));
        }
        return choices.stream().min(Comparator.comparingInt(Choice::rank).thenComparingInt(Choice::index))
                .orElseThrow(() -> new IOException("no private IPv4 interface")).address();
    }

    private static InetAddress literal(String value) throws IOException {
        String[] pieces = value.split("\\.", -1);
        if (pieces.length != 4) throw new IOException("invalid local IPv4");
        byte[] octets = new byte[4];
        for (int i = 0; i < pieces.length; i++) {
            if (!pieces[i].matches("[0-9]{1,3}")) throw new IOException("invalid local IPv4");
            int octet = Integer.parseInt(pieces[i]);
            if (octet > 255) throw new IOException("invalid local IPv4");
            octets[i] = (byte) octet;
        }
        InetAddress ip = InetAddress.getByAddress(octets);
        if (!ip.isSiteLocalAddress() && !ip.isLoopbackAddress()) throw new IOException("expected a private LAN IPv4");
        return ip;
    }

    private LanAddress() {}
}
