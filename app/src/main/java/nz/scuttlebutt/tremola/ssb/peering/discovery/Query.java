package nz.scuttlebutt.tremola.ssb.peering.discovery;

import java.util.Objects;

public class Query {
    private final String initId;
    private final int queryId;
    private final long arrivalDate;
    private static final long TIME_TO_LIVE = 5000L;

    public Query(String initiatorId, int queryId) {
        this.initId = initiatorId;
        this.queryId = queryId;
        arrivalDate = System.currentTimeMillis();
    }

    public boolean isEqualTo(String initiatorId, int queryId) {
        return Objects.equals(initId, initiatorId) && this.queryId == queryId;
    }

    /**
     * To keep the database short, delete this entry if this method returns true.
     * @return true if this query is out dated
     */
    public boolean isOutDated() {
        return System.currentTimeMillis() - arrivalDate > TIME_TO_LIVE;
    }

}