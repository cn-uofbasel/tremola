package nz.scuttlebutt.tremola.ssb.peering.discovery;

import android.util.Log;

import java.util.Objects;

public class ReceivedQuery {
    private String shortName;
    private String initId;
    private int hopCount;
    private int queryId;
    private long arrivalDate;

    public ReceivedQuery(String shortName, String initiatorId, int hopCount, int queryId) {
        this.shortName = shortName;
        this.initId = initiatorId;
        this.hopCount = hopCount;
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
