package nz.scuttlebutt.tremola.ssb.peering.discovery

class Query(private val initId: String, private val queryId: Int) {
    private val arrivalDate: Long = System.currentTimeMillis()

    fun isEqualTo(initiatorId: String?, queryId: Int): Boolean {
        return initId == initiatorId && this.queryId == queryId
    }

    /**
     * To keep the database short, delete this entry if this method returns true.
     * @return true if this query is out dated
     */
    val isOutDated: Boolean
        get() = System.currentTimeMillis() - arrivalDate > TIME_TO_LIVE

    companion object {
        private const val TIME_TO_LIVE = 5000L
    }
}