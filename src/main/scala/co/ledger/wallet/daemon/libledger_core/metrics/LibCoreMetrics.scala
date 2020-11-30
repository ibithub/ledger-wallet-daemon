package co.ledger.wallet.daemon.libledger_core.metrics

import co.ledger.core.AllocationMetrics

import scala.concurrent.{ExecutionContext, Future}


object LibCoreMetrics {

  case class AllocationMetric(objectType: String, numberOfInstances : Int)

  def fetch(implicit ec: ExecutionContext): Future[List[AllocationMetric]] = Future {

    import scala.collection.JavaConverters._

    val allocations = AllocationMetrics.getObjectAllocations.asScala

    allocations.map(e => AllocationMetric(e._1, e._2))
      .toList
      .filter(_.numberOfInstances >= 50)
      .sortBy(_.numberOfInstances)
  }
}
