package co.ledger.wallet.daemon.modules

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.twitter.finatra.json.modules.FinatraJacksonModule

object DaemonJacksonModule extends FinatraJacksonModule {

  override val additionalJacksonModules = Seq(
    new DaemonDeserializersModule
  )


  override def additionalMapperConfiguration(mapper: ObjectMapper): Unit = {
    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  }
}
