package nz.scuttlebutt.tremola.ssb.peering.discovery

/**
 * This class represents a query of the look up protocol for Tremola.
 * @param initId The ID of the person initiating the query.
 * @param queryId The ID of the query to make it unique.
 * @property arrivalDate The timestamp of when the query arrived.
 */
class Query(private val initId: String, private val queryId: Int) {
    private val arrivalDate: Long = System.currentTimeMillis()

    /** Used to compare whether the queries has certain field values.
     * @param initiatorId The ID of the person initiating the query.
     * @param queryId The ID of the query, to make it unique.
     * @return True if they are equal, false if they are different.
     * */
    fun isEqualTo(initiatorId: String?, queryId: Int): Boolean {
        return initId == initiatorId && this.queryId == queryId
    }

    /**
     * To keep the database small, entries should be deleted if this method returns true.
     * @return True if this query is older than [TIME_TO_LIVE].
     * TODO Should this not be a function instead of a field?
     */
    val isOutDated: Boolean
        get() = System.currentTimeMillis() - arrivalDate > TIME_TO_LIVE

    companion object {
        /** In milliseconds, how long a query is considered fresh and kept in logs. */
        private const val TIME_TO_LIVE = 5000L
    }
}