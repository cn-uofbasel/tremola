package nz.scuttlebutt.tremola.ssb.peering.discovery;

public class ReceivedQuery {
    private String shortName;
    private String initID;
    private int hopCount;
    private int queryID;
    private long arrivalDate;

    public ReceivedQuery(String shortName, String initiatorID, int hopCount, int queryID) {
        this.shortName = shortName;
        this.initID = initiatorID;
        this.hopCount = hopCount;
        this.queryID = queryID;
        arrivalDate = System.currentTimeMillis();
    }

    public boolean isEqualTo(String initiatorID, int queryID) {
        return initID == initiatorID && this.queryID == queryID;
    }

    /**
     * To keep the database short, delete this entry if this method returns true.
     * @return true if this query is out dated
     */
    public boolean isOutDated() {
        return System.currentTimeMillis() - arrivalDate > 60000L;
    }

}
