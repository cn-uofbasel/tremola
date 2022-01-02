package nz.scuttlebutt.tremola.ssb.peering.discovery;

import android.util.Log;

import java.util.Objects;

public class ReceivedQuery {
    private final String initId;
    private final int queryId;
    private final long arrivalDate;

    public ReceivedQuery(String initiatorId, int queryId) {
        this.initId = initiatorId;
        this.queryId = queryId;
        arrivalDate = System.currentTimeMillis();
    }

    public boolean isEqualTo(String initiatorId, int queryId) {
        Log.e("QUERY", String.valueOf(Objects.equals(initId, initiatorId) && this.queryId == queryId));
        return Objects.equals(initId, initiatorId) && this.queryId == queryId;
    }

    /**
     * To keep the database short, delete this entry if this method returns true.
     * @return true if this query is out dated
     */
    public boolean isOutDated() {
        return System.currentTimeMillis() - arrivalDate > 60000L;
    }

}
