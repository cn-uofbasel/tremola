package nz.scuttlebutt.tremola.ssb.peering.discovery;

import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nz.scuttlebutt.tremola.ssb.core.SSBid;

public final class LocalIdentity {
/*
    private static final Pattern pattern = Pattern.compile("^net:(.*):(.*)~shs:(.*)$");

    private final SSBid identity;
    private final InetSocketAddress inetSocketAddress;

    public LocalIdentity(String ip, String port, SSBid identity){
        this.identity = identity;
        inetSocketAddress = new InetSocketAddress(ip, Integer.valueOf(port));
    }


    public static LocalIdentity fromString(String string){
        Matcher matcher = pattern.matcher(string);
        if (matcher.matches()) {
            val k = Base64.getDecoder().decode(matcher.group(3).getBytes());
            return new LocalIdentity(
                    matcher.group(1),
                    matcher.group(2),
                    SSBid(k)
            );
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        LocalIdentity that = (LocalIdentity) o;
        return toString().equals(that.toString());
    }

    @Override
    public String toString() {
        val k = identity.verifyKey
        return "net:"+inetSocketAddress.getHostString()+":" + inetSocketAddress.getPort() + "~shs:" + Base64.getEncoder().encodeToString(identity.verifyKey);
    }

 */
}
